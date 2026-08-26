package com.smsforwarder.viewer.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: Long,
    val device_id: Long,
    val sender: String,
    val text: String,
    val sent_stamp: String? = null,
    val received_stamp: String? = null,
    val sim: String? = null,
    val created_at: String,
)

@Serializable
data class MessageListResponse(
    val messages: List<MessageDto>,
    val next_before_id: Long? = null,
)
