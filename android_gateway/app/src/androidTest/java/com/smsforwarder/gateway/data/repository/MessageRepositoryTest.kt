package com.smsforwarder.gateway.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.GatewayDatabase
import com.smsforwarder.gateway.data.local.db.MessageDao
import com.smsforwarder.gateway.data.remote.OutgoingSmsSender
import com.smsforwarder.gateway.data.remote.WebhookRequestWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class MessageRepositoryTest {

    private lateinit var database: GatewayDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var filterRuleRepository: FilterRuleRepository
    private lateinit var configStore: GatewayConfigStore
    private lateinit var repository: MessageRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, GatewayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messageDao = database.messageDao()
        filterRuleRepository = mock()
        configStore = mock()
        whenever(configStore.retryBackoffPolicy()).thenReturn(BackoffPolicy.EXPONENTIAL)
        whenever(configStore.retryBaseIntervalSeconds()).thenReturn(30L)

        val workConfig = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, workConfig)

        repository = MessageRepository(messageDao, mock<OutgoingSmsSender>(), filterRuleRepository, configStore, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun forwardingAllowedStoresPendingAndEnqueuesDelivery() = runBlocking {
        whenever(filterRuleRepository.shouldAccept(any(), any(), anyOrNull(), any())).thenReturn(true)

        repository.storeAndForward("+15551234", "hi", 111L, 222L, simSlot = 0, subscriptionId = 1)

        val stored = messageDao.observeAll().first()
        assertEquals(1, stored.size)
        assertEquals(DeliveryStatus.PENDING, stored[0].deliveryStatus)

        val work = androidx.work.WorkManager.getInstance(ApplicationProvider.getApplicationContext())
            .getWorkInfosByTag(WebhookRequestWorker::class.java.name).get()
        assertEquals(1, work.size)
    }

    @Test
    fun forwardingBlockedStillStoresLocallyButDoesNotEnqueue() = runBlocking {
        whenever(filterRuleRepository.shouldAccept(any(), any(), anyOrNull(), any())).thenReturn(false)

        repository.storeAndForward("+15551234", "hi", 111L, 222L, simSlot = 0, subscriptionId = 1)

        val stored = messageDao.observeAll().first()
        assertEquals(1, stored.size)
        assertEquals(DeliveryStatus.NOT_FORWARDED, stored[0].deliveryStatus)

        val work = androidx.work.WorkManager.getInstance(ApplicationProvider.getApplicationContext())
            .getWorkInfosByTag(WebhookRequestWorker::class.java.name).get()
        assertTrue(work.isEmpty())
    }

    @Test
    fun forwardingPausedBlocksEnqueueButStoresMessage() = runBlocking {
        whenever(filterRuleRepository.shouldAccept(any(), any(), anyOrNull(), any())).thenReturn(true)
        whenever(configStore.isForwardingPaused()).thenReturn(true)

        repository.storeAndForward("+15551234", "hi", 111L, 222L, simSlot = 0, subscriptionId = 1)

        val stored = messageDao.observeAll().first()
        assertEquals(1, stored.size)
        assertEquals(DeliveryStatus.PENDING, stored[0].deliveryStatus)

        val work = androidx.work.WorkManager.getInstance(ApplicationProvider.getApplicationContext())
            .getWorkInfosByTag(WebhookRequestWorker::class.java.name).get()
        assertTrue(work.isEmpty())
    }

    @Test
    fun retryMessageNoOpsWhenPausedAndLeavesStatusUntouched() = runBlocking {
        whenever(configStore.isForwardingPaused()).thenReturn(true)
        val failedId = messageDao.insert(
            com.smsforwarder.gateway.data.local.db.MessageEntity(
                sender = "+1", text = "failed one", sentStamp = 1L, receivedStamp = 1L, simSlot = 0,
                deliveryStatus = DeliveryStatus.FAILED, createdAt = 1L,
            )
        )

        repository.retryMessage(failedId)

        // Must stay FAILED, not flip to PENDING - a PENDING message with no
        // enqueued WorkManager job would look "in progress" while actually stuck.
        assertEquals(DeliveryStatus.FAILED, messageDao.getById(failedId)!!.deliveryStatus)
        val work = androidx.work.WorkManager.getInstance(ApplicationProvider.getApplicationContext())
            .getWorkInfosByTag(WebhookRequestWorker::class.java.name).get()
        assertTrue(work.isEmpty())
    }

    @Test
    fun retryAllFailedNoOpsWhenPausedAndLeavesStatusUntouched() = runBlocking {
        whenever(configStore.isForwardingPaused()).thenReturn(true)
        val failedId = messageDao.insert(
            com.smsforwarder.gateway.data.local.db.MessageEntity(
                sender = "+1", text = "failed one", sentStamp = 1L, receivedStamp = 1L, simSlot = 0,
                deliveryStatus = DeliveryStatus.FAILED, createdAt = 1L,
            )
        )

        repository.retryAllFailed()

        assertEquals(DeliveryStatus.FAILED, messageDao.getById(failedId)!!.deliveryStatus)
        val work = androidx.work.WorkManager.getInstance(ApplicationProvider.getApplicationContext())
            .getWorkInfosByTag(WebhookRequestWorker::class.java.name).get()
        assertTrue(work.isEmpty())
    }

    @Test
    fun retryAllFailedEnqueuesOnlyFailedMessagesAndResetsThemToPending() = runBlocking {
        whenever(filterRuleRepository.shouldAccept(any(), any(), anyOrNull(), any())).thenReturn(true)
        val failedId = messageDao.insert(
            com.smsforwarder.gateway.data.local.db.MessageEntity(
                sender = "+1", text = "failed one", sentStamp = 1L, receivedStamp = 1L, simSlot = 0,
                deliveryStatus = DeliveryStatus.FAILED, createdAt = 1L,
            )
        )
        messageDao.insert(
            com.smsforwarder.gateway.data.local.db.MessageEntity(
                sender = "+2", text = "not forwarded", sentStamp = 1L, receivedStamp = 1L, simSlot = 0,
                deliveryStatus = DeliveryStatus.NOT_FORWARDED, createdAt = 1L,
            )
        )
        messageDao.insert(
            com.smsforwarder.gateway.data.local.db.MessageEntity(
                sender = "+3", text = "sent", sentStamp = 1L, receivedStamp = 1L, simSlot = 0,
                deliveryStatus = DeliveryStatus.SENT, createdAt = 1L,
            )
        )

        repository.retryAllFailed()

        val work = androidx.work.WorkManager.getInstance(ApplicationProvider.getApplicationContext())
            .getWorkInfosByTag(WebhookRequestWorker::class.java.name).get()
        assertEquals(1, work.size)
        // Reset to PENDING so a retry in flight is no longer counted by
        // observeFailedCount() - otherwise the resend button would stay
        // visible and a second tap could enqueue duplicate jobs.
        assertEquals(DeliveryStatus.PENDING, messageDao.getById(failedId)!!.deliveryStatus)
    }

    @Test
    fun enqueueDeliveryReadsBackoffCriteriaFromConfigStoreNotHardcodedConstants() = runBlocking {
        whenever(filterRuleRepository.shouldAccept(any(), any(), anyOrNull(), any())).thenReturn(true)

        repository.storeAndForward("+15551234", "hi", 111L, 222L, simSlot = 0, subscriptionId = 1)

        // WorkInfo doesn't expose BackoffCriteria for inspection in tests, so this
        // verifies enqueueDelivery actually reads the criteria from configStore
        // (proving it isn't hardcoded) rather than asserting on WorkManager state;
        // the applied value is exercised end-to-end by WebhookRequestWorkerTest's
        // maxAttempts-boundary tests and by live on-device verification.
        org.mockito.kotlin.verify(configStore).retryBackoffPolicy()
        org.mockito.kotlin.verify(configStore).retryBaseIntervalSeconds()
        Unit
    }

    /**
     * Uses a real (not mocked) ContentResolver against a bogus systemSmsId -
     * whether the default-SMS role is held or not on the test device, this
     * real call either no-ops (id matches nothing) or throws SecurityException
     * (role not held); either way, deleteFromSystemStore's catch must not
     * prevent the Room deletion below from completing.
     */
    @Test
    fun deleteMessageRemovesRoomRowEvenWhenSystemStoreDeleteFails() = runBlocking {
        val id = messageDao.insert(
            com.smsforwarder.gateway.data.local.db.MessageEntity(
                sender = "+1", text = "to delete", sentStamp = 1L, receivedStamp = 1L, simSlot = 0,
                deliveryStatus = DeliveryStatus.SENT, createdAt = 1L, systemSmsId = 999_999_999L,
            )
        )

        repository.deleteMessage(id)

        assertEquals(null, messageDao.getById(id))
    }

    @Test
    fun deleteConversationRemovesRoomRowsEvenWhenSystemStoreDeleteFails() = runBlocking {
        messageDao.insert(
            com.smsforwarder.gateway.data.local.db.MessageEntity(
                sender = "+15551234", text = "one", sentStamp = 1L, receivedStamp = 1L, simSlot = 0,
                deliveryStatus = DeliveryStatus.SENT, createdAt = 1L, systemSmsId = 999_999_999L,
            )
        )
        messageDao.insert(
            com.smsforwarder.gateway.data.local.db.MessageEntity(
                sender = "+15551234", text = "two", sentStamp = 1L, receivedStamp = 1L, simSlot = 0,
                deliveryStatus = DeliveryStatus.SENT, createdAt = 2L, systemSmsId = 999_999_998L,
            )
        )

        repository.deleteConversation("+15551234")

        assertTrue(messageDao.observeThread("+15551234").first().isEmpty())
    }

    @Test
    fun deleteMessageWithNoSystemSmsIdSkipsSystemStoreDeleteSilently() = runBlocking {
        val id = messageDao.insert(
            com.smsforwarder.gateway.data.local.db.MessageEntity(
                sender = "+1", text = "never matched", sentStamp = 1L, receivedStamp = 1L, simSlot = 0,
                deliveryStatus = DeliveryStatus.SENT, createdAt = 1L, systemSmsId = null,
            )
        )

        repository.deleteMessage(id)

        assertEquals(null, messageDao.getById(id))
    }
}
