package com.smsforwarder.viewer.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceDto(
    val id: Long,
    val name: String,
    val role: String,
    val upload_token: String? = null,
    val upload_token_expires_at: String? = null,
    val created_at: String,
)

@Serializable
data class DeviceListResponse(val devices: List<DeviceDto>)

@Serializable
data class CreateBindingRequest(val download_token: String)

@Serializable
data class CreateBindingResponse(val device_id: Long, val device_name: String)
