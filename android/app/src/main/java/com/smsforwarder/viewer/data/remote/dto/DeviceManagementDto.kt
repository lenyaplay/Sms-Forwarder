package com.smsforwarder.viewer.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateDeviceRequest(val name: String)

@Serializable
data class CreateDownloadTokenRequest(val label: String? = null, val ttl_seconds: Int? = null)

@Serializable
data class ReissueUploadTokenRequest(val ttl_seconds: Int? = null)

/**
 * Response of POST /devices. Deliberately its own type, not [DeviceDto]:
 * createDeviceHandler omits `role` entirely (Go's `omitempty` on an empty
 * string), and DeviceDto.role has no default - decoding this response into
 * DeviceDto would throw. See docs/specs/0009-real-backend-integration-tests.md
 * for why this class of bug matters here.
 */
@Serializable
data class DeviceCreateResponse(
    val id: Long,
    val name: String,
    val upload_token: String,
    val upload_token_expires_at: String? = null,
    val created_at: String,
)

@Serializable
data class DownloadTokenDto(
    val id: Long,
    val download_token: String,
    val label: String? = null,
    val download_token_expires_at: String? = null,
    val bindings_count: Int? = null,
    val created_at: String,
)

@Serializable
data class DownloadTokenListResponse(val tokens: List<DownloadTokenDto>)

@Serializable
data class RevokeDownloadTokenResponse(val revoked_bindings_count: Int)

@Serializable
data class ReissueUploadTokenResponse(val upload_token: String, val upload_token_expires_at: String? = null)
