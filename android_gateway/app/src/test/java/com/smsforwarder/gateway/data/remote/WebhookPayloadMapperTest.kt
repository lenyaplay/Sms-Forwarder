package com.smsforwarder.gateway.data.remote

import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebhookPayloadMapperTest {

    private fun message(
        sentStamp: Long? = 111L,
        simSlot: Int? = 0,
    ) = MessageEntity(
        id = 1,
        sender = "+15551234",
        text = "hello",
        sentStamp = sentStamp,
        receivedStamp = 222L,
        simSlot = simSlot,
        deliveryStatus = DeliveryStatus.PENDING,
        createdAt = 333L,
    )

    @Test
    fun `maps all fields to the 0003 webhook format`() {
        val payload = WebhookPayloadMapper.toPayload(message())

        assertEquals("+15551234", payload.from)
        assertEquals("hello", payload.text)
        assertEquals("111", payload.sentStamp)
        assertEquals("222", payload.receivedStamp)
        assertEquals("sim1", payload.sim)
    }

    @Test
    fun `slot 1 maps to sim2 (0-indexed slot to 1-indexed sim name)`() {
        val payload = WebhookPayloadMapper.toPayload(message(simSlot = 1))
        assertEquals("sim2", payload.sim)
    }

    @Test
    fun `missing sim slot and sentStamp map to null, not a placeholder string`() {
        val payload = WebhookPayloadMapper.toPayload(message(sentStamp = null, simSlot = null))
        assertNull(payload.sentStamp)
        assertNull(payload.sim)
    }
}
