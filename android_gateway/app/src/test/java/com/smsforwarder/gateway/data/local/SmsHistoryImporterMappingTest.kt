package com.smsforwarder.gateway.data.local

import android.database.Cursor
import android.provider.Telephony
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SmsHistoryImporterMappingTest {

    // subscriptionId 10 -> slot 0, matching the shape SimOptionsProvider.activeSims() would
    // return for a device with one active SIM; anything else (including -1/unset) is unresolved.
    private val simOptionsProvider = object : SimOptionsProvider(mock()) {
        override fun slotForSubscriptionId(subscriptionId: Int?): Int? =
            if (subscriptionId == 10) 0 else null
    }

    private fun cursor(address: String?, body: String?, date: Long, type: Int, subscriptionId: Int = -1): Cursor {
        val cursor: Cursor = mock()
        whenever(cursor.getString(1)).thenReturn(address)
        whenever(cursor.getString(2)).thenReturn(body)
        whenever(cursor.getLong(3)).thenReturn(date)
        whenever(cursor.getInt(4)).thenReturn(type)
        whenever(cursor.getInt(5)).thenReturn(subscriptionId)
        return cursor
    }

    @Test
    fun mapsIncomingRowCorrectly() {
        val entity = cursor("+15551234", "hi", 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = 2, dateCol = 3, typeCol = 4, subscriptionIdCol = 5, simOptionsProvider = simOptionsProvider)!!

        assertEquals("+15551234", entity.sender)
        assertEquals("hi", entity.text)
        assertEquals(1000L, entity.createdAt)
        assertEquals(MessageDirection.IN, entity.direction)
        assertEquals(DeliveryStatus.SENT, entity.deliveryStatus)
    }

    @Test
    fun mapsSentRowAsOutgoing() {
        val entity = cursor("+15551234", "bye", 2000L, Telephony.Sms.MESSAGE_TYPE_SENT)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = 2, dateCol = 3, typeCol = 4, subscriptionIdCol = 5, simOptionsProvider = simOptionsProvider)!!

        assertEquals(MessageDirection.OUT, entity.direction)
    }

    @Test
    fun returnsNullWhenAddressIsBlank() {
        val result = cursor(null, "hi", 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = 2, dateCol = 3, typeCol = 4, subscriptionIdCol = 5, simOptionsProvider = simOptionsProvider)

        assertNull(result)
    }

    @Test
    fun missingBodyColumnYieldsEmptyText() {
        val entity = cursor("+15551234", null, 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = -1, dateCol = 3, typeCol = 4, subscriptionIdCol = 5, simOptionsProvider = simOptionsProvider)!!

        assertEquals("", entity.text)
    }

    @Test
    fun resolvesSimSlotFromSubscriptionIdWhenSubscriptionIsActive() {
        val entity = cursor("+15551234", "hi", 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX, subscriptionId = 10)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = 2, dateCol = 3, typeCol = 4, subscriptionIdCol = 5, simOptionsProvider = simOptionsProvider)!!

        assertEquals(0, entity.simSlot)
    }

    @Test
    fun leavesSimSlotNullWhenSubscriptionIsNotActiveOrColumnIsMissing() {
        val inactiveSubscription = cursor("+15551234", "hi", 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX, subscriptionId = 999)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = 2, dateCol = 3, typeCol = 4, subscriptionIdCol = 5, simOptionsProvider = simOptionsProvider)!!
        assertNull(inactiveSubscription.simSlot)

        val missingColumn = cursor("+15551234", "hi", 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = 2, dateCol = 3, typeCol = 4, subscriptionIdCol = -1, simOptionsProvider = simOptionsProvider)!!
        assertNull(missingColumn.simSlot)
    }
}
