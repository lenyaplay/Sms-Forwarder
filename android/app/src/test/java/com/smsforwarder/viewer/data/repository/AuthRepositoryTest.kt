package com.smsforwarder.viewer.data.repository

import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.local.Tokens
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.dto.CreateBindingRequest
import com.smsforwarder.viewer.data.remote.dto.CreateBindingResponse
import com.smsforwarder.viewer.data.remote.dto.DeviceListResponse
import com.smsforwarder.viewer.data.remote.dto.LoginRequest
import com.smsforwarder.viewer.data.remote.dto.LogoutRequest
import com.smsforwarder.viewer.data.remote.dto.MessageListResponse
import com.smsforwarder.viewer.data.remote.dto.RefreshRequest
import com.smsforwarder.viewer.data.remote.dto.TokenPairResponse
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

private class FakeApiService(
    private val loginResponse: Response<TokenPairResponse>? = null,
) : ApiService {
    var logoutCalled = false
    var logoutRequest: LogoutRequest? = null

    override suspend fun login(request: LoginRequest): Response<TokenPairResponse> =
        loginResponse ?: Response.success(TokenPairResponse("access", "refresh"))

    override suspend fun refresh(request: RefreshRequest): Response<TokenPairResponse> =
        Response.success(TokenPairResponse("access2", "refresh2"))

    override suspend fun logout(request: LogoutRequest): Response<Unit> {
        logoutCalled = true
        logoutRequest = request
        return Response.success(Unit)
    }

    override suspend fun listDevices(): Response<DeviceListResponse> =
        Response.success(DeviceListResponse(emptyList()))

    override suspend fun createBinding(request: CreateBindingRequest): Response<CreateBindingResponse> =
        Response.success(CreateBindingResponse(1, "Device"))

    override suspend fun listMessages(
        deviceId: Long,
        limit: Int?,
        beforeId: Long?,
        since: String?,
        until: String?,
    ): Response<MessageListResponse> = Response.success(MessageListResponse(emptyList(), null))
}

class AuthRepositoryTest {

    private lateinit var tokenStore: TokenStore

    @Before
    fun setUp() {
        tokenStore = mock()
    }

    @Test
    fun `successful login saves tokens`() = runBlocking {
        val api = FakeApiService(Response.success(TokenPairResponse("acc", "ref")))
        val repo = AuthRepository(api, tokenStore)

        val result = repo.login("user", "pass")

        assertTrue(result is LoginResult.Success)
        org.mockito.kotlin.verify(tokenStore).save(Tokens("acc", "ref"))
    }

    @Test
    fun `failed login (401) returns failure without saving tokens`() = runBlocking {
        val errorResponse = Response.error<TokenPairResponse>(401, "{}".toResponseBody())
        val api = FakeApiService(errorResponse)
        val repo = AuthRepository(api, tokenStore)

        val result = repo.login("user", "wrong")

        assertTrue(result is LoginResult.Failure)
        org.mockito.kotlin.verify(tokenStore, org.mockito.kotlin.never()).save(org.mockito.kotlin.any())
    }

    @Test
    fun `logout clears tokens and calls server`() = runBlocking {
        whenever(tokenStore.read()).thenReturn(Tokens("acc", "ref"))
        val api = FakeApiService()
        val repo = AuthRepository(api, tokenStore)

        repo.logout()

        assertTrue(api.logoutCalled)
        assertEquals(LogoutRequest("ref"), api.logoutRequest)
        org.mockito.kotlin.verify(tokenStore).clear()
    }

    @Test
    fun `logout with no stored tokens still clears local state without calling server`() = runBlocking {
        whenever(tokenStore.read()).thenReturn(null)
        val api = FakeApiService()
        val repo = AuthRepository(api, tokenStore)

        repo.logout()

        assertTrue(!api.logoutCalled)
        org.mockito.kotlin.verify(tokenStore).clear()
    }
}
