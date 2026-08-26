package com.smsforwarder.viewer.data.repository

import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.dto.CreateBindingRequest
import com.smsforwarder.viewer.data.remote.dto.CreateDeviceRequest
import com.smsforwarder.viewer.data.remote.dto.CreateDownloadTokenRequest
import com.smsforwarder.viewer.data.remote.dto.DeviceCreateResponse
import com.smsforwarder.viewer.data.remote.dto.DeviceDto
import com.smsforwarder.viewer.data.remote.dto.DownloadTokenDto
import com.smsforwarder.viewer.data.remote.dto.ReissueUploadTokenRequest
import com.smsforwarder.viewer.data.remote.dto.ReissueUploadTokenResponse
import javax.inject.Inject
import javax.inject.Singleton

sealed class AddDeviceResult {
    data class Success(val deviceName: String) : AddDeviceResult()
    data class Failure(val message: String) : AddDeviceResult()
}

@Singleton
class DeviceRepository @Inject constructor(
    private val apiService: ApiService,
) {
    suspend fun listDevices(): Result<List<DeviceDto>> = try {
        val response = apiService.listDevices()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body.devices)
        } else {
            Result.failure(IllegalStateException("Failed to load devices (HTTP ${response.code()})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addDeviceByToken(downloadToken: String): AddDeviceResult {
        return try {
            val response = apiService.createBinding(CreateBindingRequest(downloadToken))
            val body = response.body()
            when {
                response.isSuccessful && body != null -> AddDeviceResult.Success(body.device_name)
                response.code() == 401 -> AddDeviceResult.Failure("Token is invalid, expired or revoked")
                response.code() == 409 -> AddDeviceResult.Failure("You already have access to this device")
                response.code() == 400 -> AddDeviceResult.Failure("Token is required")
                else -> AddDeviceResult.Failure("Failed to add device (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            AddDeviceResult.Failure("Network error: ${e.message ?: "unknown"}")
        }
    }

    suspend fun createDevice(name: String): Result<DeviceCreateResponse> = try {
        val response = apiService.createDevice(CreateDeviceRequest(name))
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(IllegalStateException("Failed to create device (HTTP ${response.code()})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun createDownloadToken(deviceId: Long): Result<DownloadTokenDto> = try {
        val response = apiService.createDownloadToken(deviceId, CreateDownloadTokenRequest())
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(IllegalStateException("Failed to create download token (HTTP ${response.code()})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun listDownloadTokens(deviceId: Long): Result<List<DownloadTokenDto>> = try {
        val response = apiService.listDownloadTokens(deviceId)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body.tokens)
        } else {
            Result.failure(IllegalStateException("Failed to load download tokens (HTTP ${response.code()})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun revokeDownloadToken(deviceId: Long, tokenId: Long): Result<Unit> = try {
        val response = apiService.revokeDownloadToken(deviceId, tokenId)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to revoke download token (HTTP ${response.code()})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun reissueUploadToken(deviceId: Long): Result<ReissueUploadTokenResponse> = try {
        val response = apiService.reissueUploadToken(deviceId, ReissueUploadTokenRequest())
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(IllegalStateException("Failed to reissue upload token (HTTP ${response.code()})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
