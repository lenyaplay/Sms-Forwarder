package com.smsforwarder.gateway.data.remote

import kotlinx.serialization.Serializable

/** Body of `POST /webhook?upload_token=` - field names fixed by docs/specs/0003-sms-webhook.md. */
@Serializable
data class WebhookPayload(
    val from: String,
    val text: String,
    val sentStamp: String? = null,
    val receivedStamp: String? = null,
    val sim: String? = null,
    /** Additive, opt-in extension (spec 0018) - omitted/null unless the user explicitly disables GatewayConfigStore.hideContactNameInPayload(). */
    val contactName: String? = null,
)
