package com.smsforwarder.gateway.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smsforwarder.gateway.data.local.db.ConversationEntity
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDao
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.data.remote.OutgoingSmsSender
import com.smsforwarder.gateway.data.remote.WebhookRequestWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val outgoingSmsSender: OutgoingSmsSender,
    @ApplicationContext private val context: Context,
) {
    open fun observeMessages(): Flow<List<MessageEntity>> = messageDao.observeAll()

    open fun observeConversations(): Flow<List<ConversationEntity>> = messageDao.observeConversations()

    open fun observeThread(sender: String): Flow<List<MessageEntity>> = messageDao.observeThread(sender)

    /**
     * Re-enqueues delivery for every message not yet SENT (PENDING - never
     * configured yet when it arrived - or FAILED - exhausted retries). Called
     * after the user saves server URL/upload_token in Settings, so a message
     * that arrived before configuration isn't silently lost forever once the
     * worker that received it already terminated with Result.failure().
     */
    open suspend fun retryUndeliveredMessages() {
        messageDao.getUndelivered().forEach { enqueueDelivery(it.id) }
    }

    /** Manual retry for one FAILED message (e.g. exhausted retries for a reason other than missing config). */
    open suspend fun retryMessage(messageId: Long) {
        val message = messageDao.getById(messageId) ?: return
        messageDao.update(message.copy(deliveryStatus = DeliveryStatus.PENDING))
        enqueueDelivery(messageId)
    }

    /**
     * Sends an outgoing SMS and stores it locally. Outgoing messages aren't
     * forwarded to the webhook - docs/specs/0003-sms-webhook.md only covers
     * incoming SMS, and that wire contract isn't ours to change unilaterally.
     */
    open suspend fun sendMessage(destination: String, text: String) {
        outgoingSmsSender.send(destination, text)
        messageDao.insert(
            MessageEntity(
                sender = destination,
                text = text,
                sentStamp = System.currentTimeMillis(),
                receivedStamp = System.currentTimeMillis(),
                simSlot = null,
                deliveryStatus = DeliveryStatus.SENT,
                createdAt = System.currentTimeMillis(),
                direction = MessageDirection.OUT,
            )
        )
    }

    /** Persists an incoming SMS and enqueues its webhook delivery. */
    open suspend fun storeAndForward(
        sender: String,
        text: String,
        sentStamp: Long?,
        receivedStamp: Long,
        simSlot: Int?,
    ) {
        val id = messageDao.insert(
            MessageEntity(
                sender = sender,
                text = text,
                sentStamp = sentStamp,
                receivedStamp = receivedStamp,
                simSlot = simSlot,
                deliveryStatus = DeliveryStatus.PENDING,
                createdAt = System.currentTimeMillis(),
            )
        )
        enqueueDelivery(id)
    }

    private fun enqueueDelivery(messageId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<WebhookRequestWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(Data.Builder().putLong(WebhookRequestWorker.KEY_MESSAGE_ID, messageId).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
