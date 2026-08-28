package com.smsforwarder.gateway.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDao
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.data.remote.WebhookRequestWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    @ApplicationContext private val context: Context,
) {
    fun observeMessages(): Flow<List<MessageEntity>> = messageDao.observeAll()

    /** Persists an incoming SMS and enqueues its webhook delivery. */
    suspend fun storeAndForward(
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
