package com.smsforwarder.gateway.data.remote

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Delivers one already-persisted message to the backend webhook
 * (POST {url}/webhook?upload_token=, docs/specs/0003-sms-webhook.md), retrying
 * with WorkManager's own exponential backoff on failure - the same overall
 * strategy the third-party Gateway App used (RequestWorker/Request), which was
 * independently confirmed working end-to-end in this project's manual testing.
 */
@HiltWorker
class WebhookRequestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val configStore: GatewayConfigStore,
    private val messageDao: MessageDao,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val messageId = inputData.getLong(KEY_MESSAGE_ID, -1)
        if (messageId == -1L) return@withContext Result.failure()

        val message = messageDao.getById(messageId) ?: return@withContext Result.failure()
        val webhookUrl = configStore.webhookUrl()
        if (webhookUrl == null) {
            // Not configured yet - retry (bounded by MAX_RETRIES like any other
            // failure) rather than failing terminally: the user may configure
            // the server URL/token from Settings after this SMS already
            // arrived (SettingsViewModel.onSave also re-enqueues any message
            // still not SENT, so this message isn't solely dependent on
            // outliving its own backoff window either).
            return@withContext if (runAttemptCount >= MAX_RETRIES) Result.failure() else Result.retry()
        }

        val payload = WebhookPayloadMapper.toPayload(message)
        val body = json.encodeToString(WebhookPayload.serializer(), payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(webhookUrl).post(body).build()

        val success = try {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            Log.w("WebhookRequestWorker", "delivery attempt failed", e)
            false
        }

        if (success) {
            messageDao.update(message.copy(deliveryStatus = DeliveryStatus.SENT))
            Result.success()
        } else if (runAttemptCount >= MAX_RETRIES) {
            messageDao.update(message.copy(deliveryStatus = DeliveryStatus.FAILED))
            Result.failure()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val KEY_MESSAGE_ID = "message_id"
        private const val MAX_RETRIES = 10
    }
}
