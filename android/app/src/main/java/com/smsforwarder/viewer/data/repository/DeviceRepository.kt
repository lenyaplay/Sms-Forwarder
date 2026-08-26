package com.smsforwarder.viewer.data.repository

import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.dto.CreateBindingRequest
import com.smsforwarder.viewer.data.remote.dto.DeviceDto
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
}
