package com.smsforwarder.gateway.data.remote

/** Builds `{serverUrl}webhook?upload_token={token}` per docs/specs/0003-sms-webhook.md. */
object WebhookUrlBuilder {
    fun build(serverUrl: String, uploadToken: String): String {
        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return "${base}webhook?upload_token=$uploadToken"
    }
}
