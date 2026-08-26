package com.smsforwarder.viewer.data.repository

import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.dto.CreateBindingRequest
import com.smsforwarder.viewer.data.remote.dto.CreateBindingResponse
import com.smsforwarder.viewer.data.remote.dto.CreateDeviceRequest
import com.smsforwarder.viewer.data.remote.dto.CreateDownloadTokenRequest
import com.smsforwarder.viewer.data.remote.dto.DeviceCreateResponse
import com.smsforwarder.viewer.data.remote.dto.DeviceDto
import com.smsforwarder.viewer.data.remote.dto.DeviceListResponse
import com.smsforwarder.viewer.data.remote.dto.DownloadTokenDto
import com.smsforwarder.viewer.data.remote.dto.DownloadTokenListResponse
import com.smsforwarder.viewer.data.remote.dto.LoginRequest
import com.smsforwarder.viewer.data.remote.dto.LogoutRequest
import com.smsforwarder.viewer.data.remote.dto.MessageListResponse
import com.smsforwarder.viewer.data.remote.dto.RefreshRequest
import com.smsforwarder.viewer.data.remote.dto.ReissueUploadTokenRequest
import com.smsforwarder.viewer.data.remote.dto.ReissueUploadTokenResponse
import com.smsforwarder.viewer.data.remote.dto.RevokeDownloadTokenResponse
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
    private val createDeviceResponse: Response<DeviceCreateResponse>? = null,
    private val createDownloadTokenResponse: Response<DownloadTokenDto>? = null,
    private val listDownloadTokensResponse: Response<DownloadTokenListResponse>? = null,
    private val revokeDownloadTokenResponse: Response<RevokeDownloadTokenResponse>? = null,
    private val reissueUploadTokenResponse: Response<ReissueUploadTokenResponse>? = null,
) : ApiService {
    override suspend fun register(request: LoginRequest) = Response.success(Unit)
    override suspend fun login(request: LoginRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)

    override suspend fun listDevices(): Response<DeviceListResponse> =
        devicesResponse ?: Response.success(DeviceListResponse(emptyList()))

    override suspend fun createDevice(request: CreateDeviceRequest): Response<DeviceCreateResponse> =
        createDeviceResponse ?: Response.success(DeviceCreateResponse(1, "d", "tok", null, "2026-01-01T00:00:00Z"))

    override suspend fun createBinding(request: CreateBindingRequest): Response<CreateBindingResponse> =
        bindingResponse ?: Response.success(CreateBindingResponse(1, "Device"))

    override suspend fun createDownloadToken(
        deviceId: Long,
        request: CreateDownloadTokenRequest,
    ): Response<DownloadTokenDto> = createDownloadTokenResponse
        ?: Response.success(DownloadTokenDto(1, "tok", null, null, null, "2026-01-01T00:00:00Z"))

    override suspend fun listDownloadTokens(deviceId: Long): Response<DownloadTokenListResponse> =
        listDownloadTokensResponse ?: Response.success(DownloadTokenListResponse(emptyList()))

    override suspend fun revokeDownloadToken(deviceId: Long, tokenId: Long): Response<RevokeDownloadTokenResponse> =
        revokeDownloadTokenResponse ?: Response.success(RevokeDownloadTokenResponse(0))

    override suspend fun reissueUploadToken(
        deviceId: Long,
        request: ReissueUploadTokenRequest,
    ): Response<ReissueUploadTokenResponse> =
        reissueUploadTokenResponse ?: Response.success(ReissueUploadTokenResponse("tok", null))

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

    @Test
    fun `createDevice returns the minted device and upload token`() = runBlocking {
        val api = FakeDeviceApiService(
            createDeviceResponse = Response.success(DeviceCreateResponse(9, "Phone", "up-tok", null, "2026-01-01T00:00:00Z")),
        )
        val repo = DeviceRepository(api)

        val result = repo.createDevice("Phone")

        assertEquals("up-tok", result.getOrNull()?.upload_token)
    }

    @Test
    fun `createDownloadToken returns the minted token`() = runBlocking {
        val api = FakeDeviceApiService(
            createDownloadTokenResponse = Response.success(DownloadTokenDto(3, "dl-tok", null, null, null, "2026-01-01T00:00:00Z")),
        )
        val repo = DeviceRepository(api)

        val result = repo.createDownloadToken(deviceId = 9)

        assertEquals("dl-tok", result.getOrNull()?.download_token)
    }

    @Test
    fun `createDownloadToken as non-owner surfaces failure`() = runBlocking {
        val api = FakeDeviceApiService(createDownloadTokenResponse = Response.error(403, "{}".toResponseBody()))
        val repo = DeviceRepository(api)

        val result = repo.createDownloadToken(deviceId = 9)

        assertTrue(result.isFailure)
    }

    @Test
    fun `listDownloadTokens returns the tokens for a device`() = runBlocking {
        val tokens = listOf(DownloadTokenDto(1, "a", null, null, 0, "2026-01-01T00:00:00Z"))
        val api = FakeDeviceApiService(listDownloadTokensResponse = Response.success(DownloadTokenListResponse(tokens)))
        val repo = DeviceRepository(api)

        val result = repo.listDownloadTokens(deviceId = 9)

        assertEquals(tokens, result.getOrNull())
    }

    @Test
    fun `revokeDownloadToken succeeds`() = runBlocking {
        val api = FakeDeviceApiService(revokeDownloadTokenResponse = Response.success(RevokeDownloadTokenResponse(1)))
        val repo = DeviceRepository(api)

        val result = repo.revokeDownloadToken(deviceId = 9, tokenId = 3)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `reissueUploadToken returns the new token`() = runBlocking {
        val api = FakeDeviceApiService(
            reissueUploadTokenResponse = Response.success(ReissueUploadTokenResponse("new-tok", null)),
        )
        val repo = DeviceRepository(api)

        val result = repo.reissueUploadToken(deviceId = 9)

        assertEquals("new-tok", result.getOrNull()?.upload_token)
    }

    @Test
    fun `reissueUploadToken as non-owner surfaces failure`() = runBlocking {
        val api = FakeDeviceApiService(reissueUploadTokenResponse = Response.error(403, "{}".toResponseBody()))
        val repo = DeviceRepository(api)

        val result = repo.reissueUploadToken(deviceId = 9)

        assertTrue(result.isFailure)
    }
}
