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
}
