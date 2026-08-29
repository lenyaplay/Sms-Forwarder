package com.smsforwarder.gateway.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.local.db.ConversationEntity
import com.smsforwarder.gateway.data.local.db.ConversationMetaEntity
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.data.local.db.MessageDao
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.data.remote.OutgoingSmsSender
import com.smsforwarder.gateway.data.remote.WebhookRequestWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val outgoingSmsSender: OutgoingSmsSender,
    private val filterRuleRepository: FilterRuleRepository,
    private val configStore: GatewayConfigStore,
    @ApplicationContext private val context: Context,
) {
    open fun observeMessages(): Flow<List<MessageEntity>> = messageDao.observeAll()

    open fun observeFailedCount(): Flow<Int> = messageDao.observeFailedCount()

    open fun observeConversations(archived: Boolean = false): Flow<List<ConversationEntity>> =
        messageDao.observeConversations(archived)

    open fun observeThread(sender: String): Flow<List<MessageEntity>> = messageDao.observeThread(sender)

    open fun searchMessages(query: String): Flow<List<MessageEntity>> = messageDao.searchMessages(query)

    open suspend fun archiveConversation(sender: String) {
        messageDao.setConversationMeta(ConversationMetaEntity(sender, isArchived = true))
    }

    open suspend fun unarchiveConversation(sender: String) {
        messageDao.setConversationMeta(ConversationMetaEntity(sender, isArchived = false))
    }

    /**
     * NonCancellable: callers (e.g. ThreadScreen) may navigate away and clear
     * their ViewModel's scope right after triggering this, which would
     * otherwise cancel the delete mid-flight and leave it half-done.
     */
    open suspend fun deleteConversation(sender: String) = withContext(NonCancellable) {
        messageDao.deleteConversationAndMeta(sender)
    }

    open suspend fun deleteMessage(id: Long) = withContext(NonCancellable) {
        messageDao.deleteById(id)
    }

    /**
     * Re-enqueues delivery for every message not yet SENT (PENDING - never
     * configured yet when it arrived - or FAILED - exhausted retries). Called
     * after the user saves server URL/upload_token in Settings, so a message
     * that arrived before configuration isn't silently lost forever once the
     * worker that received it already terminated with Result.failure().
     *
     * NOT_FORWARDED messages are untouched by this - a forwarding-stage
     * filter block is a deliberate decision, not a delivery failure, so it
     * doesn't get swept up in this "retry everything undelivered" pass.
     */
    open suspend fun retryUndeliveredMessages() {
        messageDao.getUndelivered().forEach { enqueueDelivery(it.id) }
    }

    /**
     * Manual retry for one message (e.g. exhausted retries, or a user
     * explicitly overriding a NOT_FORWARDED filter block for this one
     * message - the filter decision from storeAndForward isn't re-checked
     * here, an explicit user action always wins).
     *
     * No-ops while forwarding is paused - resetting the status to PENDING
     * before enqueueDelivery's own pause check would otherwise leave the
     * message looking "in progress" (and, for retryAllFailed, silently drop
     * it from observeFailedCount()) while no WorkManager job was actually
     * created, with no signal to the user that the tap did nothing.
     */
    open suspend fun retryMessage(messageId: Long) {
        if (configStore.isForwardingPaused()) return
        val message = messageDao.getById(messageId) ?: return
        messageDao.update(message.copy(deliveryStatus = DeliveryStatus.PENDING))
        enqueueDelivery(messageId)
    }

    /**
     * Bulk "retry all failed" (Conversations action) - only FAILED messages,
     * same NOT_FORWARDED exclusion rationale as retryUndeliveredMessages: a
     * filter block is a decision, not a delivery failure. Resets each row to
     * PENDING before enqueueing (like retryMessage) so a retry in flight is
     * no longer counted by observeFailedCount() - otherwise the resend
     * button would stay visible and a second tap could enqueue duplicate
     * WorkManager jobs for the same messages before the first round finishes.
     *
     * No-ops while forwarding is paused - see retryMessage's doc.
     */
    open suspend fun retryAllFailed() {
        if (configStore.isForwardingPaused()) return
        messageDao.getFailed().forEach { message ->
            messageDao.update(message.copy(deliveryStatus = DeliveryStatus.PENDING))
            enqueueDelivery(message.id)
        }
    }

    /**
     * Sends an outgoing SMS and stores it locally. Outgoing messages aren't
     * forwarded to the webhook - docs/specs/0003-sms-webhook.md only covers
     * incoming SMS, and that wire contract isn't ours to change unilaterally.
     */
    open suspend fun sendMessage(destination: String, text: String, subscriptionId: Int? = null, simSlot: Int? = null) {
        outgoingSmsSender.send(destination, text, subscriptionId)
        messageDao.insert(
            MessageEntity(
                sender = destination,
                text = text,
                sentStamp = System.currentTimeMillis(),
                receivedStamp = System.currentTimeMillis(),
                simSlot = simSlot,
                deliveryStatus = DeliveryStatus.SENT,
                createdAt = System.currentTimeMillis(),
                direction = MessageDirection.OUT,
            )
        )
    }

    /**
     * Persists an incoming SMS and enqueues its webhook delivery, unless the
     * forwarding-stage filter blocks it - in which case the message is still
     * stored/visible (NOT_FORWARDED), just never enqueued (spec 0015).
     */
    open suspend fun storeAndForward(
        sender: String,
        text: String,
        sentStamp: Long?,
        receivedStamp: Long,
        simSlot: Int?,
        subscriptionId: Int? = null,
    ) {
        val shouldForward = filterRuleRepository.shouldAccept(FilterStage.FORWARDING, sender, subscriptionId, text)
        val id = messageDao.insert(
            MessageEntity(
                sender = sender,
                text = text,
                sentStamp = sentStamp,
                receivedStamp = receivedStamp,
                simSlot = simSlot,
                deliveryStatus = if (shouldForward) DeliveryStatus.PENDING else DeliveryStatus.NOT_FORWARDED,
                createdAt = System.currentTimeMillis(),
            )
        )
        if (shouldForward) enqueueDelivery(id)
    }

    private fun enqueueDelivery(messageId: Long) {
        // Message stays in whatever status the caller already set (PENDING for a
        // new incoming SMS, unchanged for a manual/bulk retry) - pausing only
        // withholds the WorkManager job, it doesn't touch stored data.
        if (configStore.isForwardingPaused()) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<WebhookRequestWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(configStore.retryBackoffPolicy(), configStore.retryBaseIntervalSeconds(), TimeUnit.SECONDS)
            .setInputData(Data.Builder().putLong(WebhookRequestWorker.KEY_MESSAGE_ID, messageId).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
