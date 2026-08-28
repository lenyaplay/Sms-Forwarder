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

    private fun cursor(address: String?, body: String?, date: Long, type: Int): Cursor {
        val cursor: Cursor = mock()
        whenever(cursor.getString(1)).thenReturn(address)
        whenever(cursor.getString(2)).thenReturn(body)
        whenever(cursor.getLong(3)).thenReturn(date)
        whenever(cursor.getInt(4)).thenReturn(type)
        return cursor
    }

    @Test
    fun mapsIncomingRowCorrectly() {
        val entity = cursor("+15551234", "hi", 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = 2, dateCol = 3, typeCol = 4)!!

        assertEquals("+15551234", entity.sender)
        assertEquals("hi", entity.text)
        assertEquals(1000L, entity.createdAt)
        assertEquals(MessageDirection.IN, entity.direction)
        assertEquals(DeliveryStatus.SENT, entity.deliveryStatus)
    }

    @Test
    fun mapsSentRowAsOutgoing() {
        val entity = cursor("+15551234", "bye", 2000L, Telephony.Sms.MESSAGE_TYPE_SENT)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = 2, dateCol = 3, typeCol = 4)!!

        assertEquals(MessageDirection.OUT, entity.direction)
    }

    @Test
    fun returnsNullWhenAddressIsBlank() {
        val result = cursor(null, "hi", 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = 2, dateCol = 3, typeCol = 4)

        assertNull(result)
    }

    @Test
    fun missingBodyColumnYieldsEmptyText() {
        val entity = cursor("+15551234", null, 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX)
            .toMessageEntity(idCol = 0, addressCol = 1, bodyCol = -1, dateCol = 3, typeCol = 4)!!

        assertEquals("", entity.text)
    }
}
