package com.smsforwarder.gateway.data.remote

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.GatewayDatabase
import com.smsforwarder.gateway.data.local.db.MessageDao
import com.smsforwarder.gateway.data.local.db.MessageEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Exercises WebhookRequestWorker's own retry/failure state machine against a
 * local MockWebServer - the realbackend suite only proves the happy path
 * (a real 201), so a regression in the retry/failure branches or the
 * SENT/FAILED status writes would otherwise ship undetected.
 */
@RunWith(AndroidJUnit4::class)
class WebhookRequestWorkerTest {

    private lateinit var server: MockWebServer
    private lateinit var database: GatewayDatabase
    private lateinit var dao: MessageDao
    private lateinit var configStore: GatewayConfigStore
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        database = Room.inMemoryDatabaseBuilder(context, GatewayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.messageDao()
        configStore = mock()
        whenever(configStore.webhookUrl()).thenReturn(server.url("/webhook?upload_token=tok").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    private fun insertMessage(): Long = runBlocking {
        dao.insert(
            MessageEntity(
                sender = "+15551234",
                text = "hello",
                sentStamp = 111L,
                receivedStamp = 222L,
                simSlot = 0,
                deliveryStatus = DeliveryStatus.PENDING,
                createdAt = 333L,
            )
        )
    }

    private fun buildWorker(messageId: Long) =
        TestListenableWorkerBuilder<WebhookRequestWorker>(context)
            .setInputData(Data.Builder().putLong(WebhookRequestWorker.KEY_MESSAGE_ID, messageId).build())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = WebhookRequestWorker(
                    appContext,
                    workerParameters,
                    configStore,
                    dao,
                    OkHttpClient(),
                    Json { ignoreUnknownKeys = true },
                )
            })
            .build()

    @Test
    fun serverErrorRetriesAndLeavesStatusPending() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val messageId = insertMessage()

        val result = buildWorker(messageId).doWork()

        assertEquals(Result.retry(), result)
        assertEquals(DeliveryStatus.PENDING, dao.getById(messageId)!!.deliveryStatus)
    }

    @Test
    fun successMarksMessageSent() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val messageId = insertMessage()

        val result = buildWorker(messageId).doWork()

        assertEquals(Result.success(), result)
        assertEquals(DeliveryStatus.SENT, dao.getById(messageId)!!.deliveryStatus)
    }

    @Test
    fun notYetConfiguredRetriesInsteadOfFailingTerminally() = runBlocking {
        whenever(configStore.webhookUrl()).thenReturn(null)
        val messageId = insertMessage()

        val result = buildWorker(messageId).doWork()

        assertEquals(Result.retry(), result)
        assertEquals(DeliveryStatus.PENDING, dao.getById(messageId)!!.deliveryStatus)
    }
}
