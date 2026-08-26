package com.smsforwarder.viewer.data.repository

import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.dto.CreateBindingRequest
import com.smsforwarder.viewer.data.remote.dto.CreateBindingResponse
import com.smsforwarder.viewer.data.remote.dto.DeviceDto
import com.smsforwarder.viewer.data.remote.dto.DeviceListResponse
import com.smsforwarder.viewer.data.remote.dto.LoginRequest
import com.smsforwarder.viewer.data.remote.dto.LogoutRequest
import com.smsforwarder.viewer.data.remote.dto.MessageListResponse
import com.smsforwarder.viewer.data.remote.dto.RefreshRequest
import com.smsforwarder.viewer.data.remote.dto.TokenPairResponse
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class FakeDeviceApiService(
    private val bindingResponse: Response<CreateBindingResponse>? = null,
    private val devicesResponse: Response<DeviceListResponse>? = null,
) : ApiService {
    override suspend fun login(request: LoginRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)

    override suspend fun listDevices(): Response<DeviceListResponse> =
        devicesResponse ?: Response.success(DeviceListResponse(emptyList()))

    override suspend fun createBinding(request: CreateBindingRequest): Response<CreateBindingResponse> =
        bindingResponse ?: Response.success(CreateBindingResponse(1, "Device"))

    override suspend fun listMessages(
        deviceId: Long,
        limit: Int?,
        beforeId: Long?,
        since: String?,
        until: String?,
    ) = Response.success(MessageListResponse(emptyList(), null))
}

class DeviceRepositoryTest {

    @Test
    fun `listDevices returns devices on success`() = runBlocking {
        val devices = listOf(DeviceDto(1, "Phone", "owner", "tok", null, "2026-01-01T00:00:00Z"))
        val api = FakeDeviceApiService(devicesResponse = Response.success(DeviceListResponse(devices)))
        val repo = DeviceRepository(api)

        val result = repo.listDevices()

        assertTrue(result.isSuccess)
        assertEquals(devices, result.getOrNull())
    }

    @Test
    fun `addDeviceByToken success returns device name`() = runBlocking {
        val api = FakeDeviceApiService(bindingResponse = Response.success(CreateBindingResponse(7, "Kitchen phone")))
        val repo = DeviceRepository(api)

        val result = repo.addDeviceByToken("valid-token")

        assertEquals(AddDeviceResult.Success("Kitchen phone"), result)
    }

    @Test
    fun `addDeviceByToken 401 returns invalid token failure`() = runBlocking {
        val api = FakeDeviceApiService(bindingResponse = Response.error(401, "{}".toResponseBody()))
        val repo = DeviceRepository(api)

        val result = repo.addDeviceByToken("bad-token") as AddDeviceResult.Failure

        assertTrue(result.message.contains("invalid", ignoreCase = true))
    }

    @Test
    fun `addDeviceByToken 409 returns already-bound failure`() = runBlocking {
        val api = FakeDeviceApiService(bindingResponse = Response.error(409, "{}".toResponseBody()))
        val repo = DeviceRepository(api)

        val result = repo.addDeviceByToken("token") as AddDeviceResult.Failure

        assertTrue(result.message.isNotBlank())
    }
}
