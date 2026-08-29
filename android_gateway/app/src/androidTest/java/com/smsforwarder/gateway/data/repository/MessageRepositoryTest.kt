package com.smsforwarder.gateway.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
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
    private lateinit var repository: MessageRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, GatewayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messageDao = database.messageDao()
        filterRuleRepository = mock()

        val workConfig = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, workConfig)

        repository = MessageRepository(messageDao, mock<OutgoingSmsSender>(), filterRuleRepository, context)
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
}
