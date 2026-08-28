package com.smsforwarder.gateway.realbackend

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.GatewayDatabase
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.data.remote.WebhookRequestWorker
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Delivers a real message to the real backend webhook (docs/specs/0009's
 * pattern, applied to this app's own worker instead of raw HTTP) and confirms
 * both the HTTP 201/200 (via Result.success()) and the local status update -
 * proving the two systems agree on the same wire format (0003), not just that
 * this app's own code compiles against it.
 */
@RunWith(AndroidJUnit4::class)
class RealBackendWebhookDeliveryTest {

    @Test
    fun realBackend_deliversStoredMessageAndMarksItSent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ownerLogin = uniqueLogin("webhook-owner")
        val ownerAccessToken = registerAndLoginOwner(ownerLogin, "owner-password-123")
        val (_, uploadToken) = createDevice(ownerAccessToken, "Gateway Test Device ${System.currentTimeMillis()}")

        val database = Room.inMemoryDatabaseBuilder(context, GatewayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.messageDao()
        val messageId = dao.insert(
            MessageEntity(
                sender = "+15550001111",
                text = "Real backend gateway test ${System.currentTimeMillis()}",
                sentStamp = System.currentTimeMillis(),
                receivedStamp = System.currentTimeMillis(),
                simSlot = 0,
                deliveryStatus = DeliveryStatus.PENDING,
                createdAt = System.currentTimeMillis(),
            )
        )

        val configStore: GatewayConfigStore = mock()
        whenever(configStore.webhookUrl()).thenReturn("${REAL_BACKEND_BASE_URL}webhook?upload_token=$uploadToken")

        val worker = TestListenableWorkerBuilder<WebhookRequestWorker>(context)
            .setInputData(
                androidx.work.Data.Builder().putLong(WebhookRequestWorker.KEY_MESSAGE_ID, messageId).build()
            )
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: android.content.Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = WebhookRequestWorker(appContext, workerParameters, configStore, dao, OkHttpClient(), Json { ignoreUnknownKeys = true })
            })
            .build()

        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(DeliveryStatus.SENT, dao.getById(messageId)!!.deliveryStatus)

        database.close()
    }
}
