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
import com.smsforwarder.gateway.data.local.ContactNameResolver
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.local.db.DeliveryLogDao
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.GatewayDatabase
import com.smsforwarder.gateway.data.local.db.MessageDao
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.data.repository.MessageRepository
import com.smsforwarder.gateway.sms.DeliveryResultNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
    private lateinit var deliveryLogDao: DeliveryLogDao
    private lateinit var configStore: GatewayConfigStore
    private lateinit var deliveryResultNotifier: DeliveryResultNotifier
    private lateinit var messageRepository: MessageRepository
    private lateinit var contactNameResolver: ContactNameResolver
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        database = Room.inMemoryDatabaseBuilder(context, GatewayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.messageDao()
        deliveryLogDao = database.deliveryLogDao()
        configStore = mock()
        deliveryResultNotifier = mock()
        messageRepository = mock()
        contactNameResolver = mock()
        whenever(configStore.webhookUrl()).thenReturn(server.url("/webhook?upload_token=tok").toString())
        whenever(configStore.retryMaxAttempts()).thenReturn(10)
        whenever(configStore.hideContactNameInPayload()).thenReturn(true)
        whenever(configStore.deleteAfterForward()).thenReturn(false)
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

    private fun buildWorker(messageId: Long, runAttemptCount: Int = 0) =
        TestListenableWorkerBuilder<WebhookRequestWorker>(context)
            .setInputData(Data.Builder().putLong(WebhookRequestWorker.KEY_MESSAGE_ID, messageId).build())
            .setRunAttemptCount(runAttemptCount)
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
                    deliveryLogDao,
                    deliveryResultNotifier,
                    messageRepository,
                    contactNameResolver,
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

    @Test
    fun failsTerminallyAfterConfiguredMaxAttemptsNotHardcodedTen() = runBlocking {
        whenever(configStore.retryMaxAttempts()).thenReturn(2)
        server.enqueue(MockResponse().setResponseCode(500))
        val messageId = insertMessage()

        val result = buildWorker(messageId, runAttemptCount = 2).doWork()

        assertEquals(Result.failure(), result)
        assertEquals(DeliveryStatus.FAILED, dao.getById(messageId)!!.deliveryStatus)
    }

    @Test
    fun stillRetriesBelowConfiguredMaxAttempts() = runBlocking {
        whenever(configStore.retryMaxAttempts()).thenReturn(2)
        server.enqueue(MockResponse().setResponseCode(500))
        val messageId = insertMessage()

        val result = buildWorker(messageId, runAttemptCount = 1).doWork()

        assertEquals(Result.retry(), result)
        assertEquals(DeliveryStatus.PENDING, dao.getById(messageId)!!.deliveryStatus)
    }

    @Test
    fun successWritesDeliveryLogEntryAndDoesNotNotifyOnFirstAttempt() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val messageId = insertMessage()

        buildWorker(messageId, runAttemptCount = 1).doWork()

        val entries = deliveryLogDao.observeRecent().first()
        assertEquals(1, entries.size)
        assertTrue(entries[0].success)
        assertEquals(null, entries[0].errorMessage)
        verify(deliveryResultNotifier, never()).notifyDeliverySucceededAfterRetry(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(deliveryResultNotifier, never()).notifyDeliveryFailed(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun successAfterRetryNotifiesAndFinalFailureNotifiesSeparately() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val messageId = insertMessage()

        buildWorker(messageId, runAttemptCount = 2).doWork()

        verify(deliveryResultNotifier).notifyDeliverySucceededAfterRetry("+15551234", 2)
        verify(deliveryResultNotifier, never()).notifyDeliveryFailed(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun finalFailureWritesLogEntryWithHttpCodeAndNotifies() = runBlocking {
        whenever(configStore.retryMaxAttempts()).thenReturn(2)
        server.enqueue(MockResponse().setResponseCode(500))
        val messageId = insertMessage()

        buildWorker(messageId, runAttemptCount = 2).doWork()

        val entries = deliveryLogDao.observeRecent().first()
        assertEquals(1, entries.size)
        assertTrue(!entries[0].success)
        assertEquals("HTTP 500", entries[0].errorMessage)
        verify(deliveryResultNotifier).notifyDeliveryFailed("+15551234", 2)
    }

    @Test
    fun deleteAfterForwardTrueDeletesMessageAfterSuccess() = runBlocking {
        whenever(configStore.deleteAfterForward()).thenReturn(true)
        server.enqueue(MockResponse().setResponseCode(201))
        val messageId = insertMessage()

        buildWorker(messageId).doWork()

        verify(messageRepository).deleteMessage(messageId)
    }

    @Test
    fun deleteAfterForwardFalseDoesNotDeleteMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val messageId = insertMessage()

        buildWorker(messageId).doWork()

        verify(messageRepository, never()).deleteMessage(org.mockito.kotlin.any())
    }

    @Test
    fun contactNameIncludedInPayloadWhenExplicitlyUnhidden() = runBlocking {
        whenever(configStore.hideContactNameInPayload()).thenReturn(false)
        whenever(contactNameResolver.displayNameFor("+15551234")).thenReturn("John Doe")
        server.enqueue(MockResponse().setResponseCode(201))
        val messageId = insertMessage()

        buildWorker(messageId).doWork()

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"contactName\":\"John Doe\""))
    }

    @Test
    fun contactNameOmittedByDefault() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val messageId = insertMessage()

        buildWorker(messageId).doWork()

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(!requestBody.contains("contactName"))
        verify(contactNameResolver, never()).displayNameFor(org.mockito.kotlin.any())
        Unit
    }
}
