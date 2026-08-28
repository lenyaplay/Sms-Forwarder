package com.smsforwarder.gateway.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var database: GatewayDatabase
    private lateinit var dao: MessageDao

    private fun message(status: DeliveryStatus = DeliveryStatus.PENDING) = MessageEntity(
        sender = "+15551234",
        text = "hello",
        sentStamp = 111L,
        receivedStamp = 222L,
        simSlot = 0,
        deliveryStatus = status,
        createdAt = System.currentTimeMillis(),
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GatewayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.messageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertThenObserveAllReturnsTheStoredMessage() = runBlocking {
        dao.insert(message())

        val stored = dao.observeAll().first()

        assertEquals(1, stored.size)
        assertEquals("+15551234", stored[0].sender)
        assertEquals(DeliveryStatus.PENDING, stored[0].deliveryStatus)
    }

    @Test
    fun updateChangesDeliveryStatusOfAnExistingRow() = runBlocking {
        val id = dao.insert(message())
        val stored = dao.getById(id)!!

        dao.update(stored.copy(deliveryStatus = DeliveryStatus.SENT))

        assertEquals(DeliveryStatus.SENT, dao.getById(id)!!.deliveryStatus)
    }

    @Test
    fun observeAllOrdersByCreatedAtDescending() = runBlocking {
        dao.insert(message().copy(createdAt = 1L))
        dao.insert(message().copy(createdAt = 2L))

        val stored = dao.observeAll().first()

        assertEquals(2L, stored[0].createdAt)
        assertEquals(1L, stored[1].createdAt)
    }

    @Test
    fun observeConversationsCollapsesMultipleMessagesFromTheSameSenderToOneRow() = runBlocking {
        dao.insert(message().copy(sender = "+15551234", text = "first", createdAt = 1L))
        dao.insert(message().copy(sender = "+15551234", text = "second", createdAt = 2L))
        dao.insert(message().copy(sender = "+15559999", text = "other", createdAt = 3L))

        val conversations = dao.observeConversations().first()

        assertEquals(2, conversations.size)
        val firstConversation = conversations.first { it.sender == "+15551234" }
        assertEquals("second", firstConversation.text)
    }

    @Test
    fun observeConversationsIsCorrelatedPerSenderEvenWithCollidingTimestamps() = runBlocking {
        // Sender A's own last message (createdAt=200) must win even though
        // sender A also has an older row whose createdAt (100) coincidentally
        // matches sender B's max - a naive uncorrelated MAX(createdAt) filter
        // would incorrectly return both of A's rows.
        dao.insert(message().copy(sender = "A", text = "A-old", createdAt = 100L))
        dao.insert(message().copy(sender = "A", text = "A-new", createdAt = 200L))
        dao.insert(message().copy(sender = "B", text = "B-last", createdAt = 100L))

        val conversations = dao.observeConversations().first()

        assertEquals(2, conversations.size)
        assertEquals("A-new", conversations.first { it.sender == "A" }.text)
        assertEquals("B-last", conversations.first { it.sender == "B" }.text)
    }

    @Test
    fun observeThreadReturnsOnlyMessagesFromThatSenderOldestFirst() = runBlocking {
        dao.insert(message().copy(sender = "+15551234", text = "first", createdAt = 1L))
        dao.insert(message().copy(sender = "+15559999", text = "unrelated", createdAt = 2L))
        dao.insert(message().copy(sender = "+15551234", text = "second", createdAt = 3L))

        val thread = dao.observeThread("+15551234").first()

        assertEquals(2, thread.size)
        assertEquals("first", thread[0].text)
        assertEquals("second", thread[1].text)
    }
}
