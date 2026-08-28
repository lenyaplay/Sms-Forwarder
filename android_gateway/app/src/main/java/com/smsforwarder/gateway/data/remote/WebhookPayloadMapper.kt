package com.smsforwarder.gateway.data.remote

import com.smsforwarder.gateway.data.local.db.MessageEntity

/** Builds the exact wire format of docs/specs/0003-sms-webhook.md from a stored message. */
object WebhookPayloadMapper {
    fun toPayload(message: MessageEntity): WebhookPayload = WebhookPayload(
        from = message.sender,
        text = message.text,
        sentStamp = message.sentStamp?.toString(),
        receivedStamp = message.receivedStamp.toString(),
        sim = message.simSlot?.let { "sim${it + 1}" },
    )
}
