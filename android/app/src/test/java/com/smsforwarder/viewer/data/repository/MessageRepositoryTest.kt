package com.smsforwarder.viewer.data.repository

import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.local.Tokens
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.SseSource
import com.smsforwarder.viewer.data.remote.dto.CreateBindingRequest
import com.smsforwarder.viewer.data.remote.dto.CreateBindingResponse
import com.smsforwarder.viewer.data.remote.dto.CreateDeviceRequest
import com.smsforwarder.viewer.data.remote.dto.CreateDownloadTokenRequest
import com.smsforwarder.viewer.data.remote.dto.DeviceCreateResponse
import com.smsforwarder.viewer.data.remote.dto.DeviceListResponse
import com.smsforwarder.viewer.data.remote.dto.DownloadTokenDto
import com.smsforwarder.viewer.data.remote.dto.DownloadTokenListResponse
import com.smsforwarder.viewer.data.remote.dto.LoginRequest
import com.smsforwarder.viewer.data.remote.dto.LogoutRequest
import com.smsforwarder.viewer.data.remote.dto.MessageDto
import com.smsforwarder.viewer.data.remote.dto.MessageListResponse
import com.smsforwarder.viewer.data.remote.dto.RefreshRequest
import com.smsforwarder.viewer.data.remote.dto.ReissueUploadTokenRequest
import com.smsforwarder.viewer.data.remote.dto.ReissueUploadTokenResponse
import com.smsforwarder.viewer.data.remote.dto.RevokeDownloadTokenResponse
import com.smsforwarder.viewer.data.remote.dto.TokenPairResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

private fun msg(id: Long, createdAt: String = "2026-01-01T00:00:0${id}Z") =
    MessageDto(id, 1, "+1", "text $id", null, null, null, createdAt)

private class FixedApiService(
    private var pages: MutableList<List<MessageDto>>,
) : ApiService {
    override suspend fun register(request: LoginRequest) = Response.success(Unit)
    override suspend fun login(request: LoginRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)
    override suspend fun listDevices() = Response.success(DeviceListResponse(emptyList()))
    override suspend fun createDevice(request: CreateDeviceRequest) =
        Response.success(DeviceCreateResponse(1, "d", "tok", null, "2026-01-01T00:00:00Z"))
    override suspend fun createBinding(request: CreateBindingRequest) =
        Response.success(CreateBindingResponse(1, "d"))
    override suspend fun createDownloadToken(deviceId: Long, request: CreateDownloadTokenRequest) =
        Response.success(DownloadTokenDto(1, "tok", null, null, null, "2026-01-01T00:00:00Z"))
    override suspend fun listDownloadTokens(deviceId: Long) = Response.success(DownloadTokenListResponse(emptyList()))
    override suspend fun revokeDownloadToken(deviceId: Long, tokenId: Long) =
        Response.success(RevokeDownloadTokenResponse(0))
    override suspend fun reissueUploadToken(deviceId: Long, request: ReissueUploadTokenRequest) =
        Response.success(ReissueUploadTokenResponse("tok", null))

    override suspend fun listMessages(
        deviceId: Long,
        limit: Int?,
        beforeId: Long?,
        since: String?,
        until: String?,
    ): Response<MessageListResponse> {
        val page = if (pages.isNotEmpty()) pages.removeAt(0) else emptyList()
        return Response.success(MessageListResponse(page, null))
    }
}

/** Emits a fixed sequence of messages then completes (simulating a dropped SSE stream). */
private class ScriptedSseClient(private val events: List<MessageDto>, private val failImmediately: Boolean = false) :
    SseSource {
    override fun stream(url: String): Flow<MessageDto> = flow {
        if (failImmediately) throw IllegalStateException("connection refused")
        events.forEach { emit(it) }
    }
}

/** Fails on its first call (simulating a dropped connection), then emits on reconnect. */
private class FlakySseClient(private val eventsOnReconnect: List<MessageDto>) : SseSource {
    var callCount = 0
        private set

    override fun stream(url: String): Flow<MessageDto> = flow {
        callCount++
        if (callCount == 1) {
            throw IllegalStateException("connection refused")
        }
        eventsOnReconnect.forEach { emit(it) }
    }
}

class MessageRepositoryTest {

    private lateinit var tokenStore: TokenStore

    private fun setUpTokenStore() {
        tokenStore = mock()
        whenever(tokenStore.read()).thenReturn(Tokens("access", "refresh"))
    }

    @Test
    fun `fetchPage returns messages and next cursor on success`() = runBlocking {
        setUpTokenStore()
        val api = FixedApiService(mutableListOf(listOf(msg(1), msg(2))))
        val repo = MessageRepository(api, ScriptedSseClient(emptyList()), tokenStore, "http://example.test/")

        val result = repo.fetchPage(deviceId = 1)

        assertEquals(listOf(msg(1), msg(2)), result.getOrNull()?.messages)
    }

    @Test
    fun `observeLive emits SSE messages directly`() = runBlocking {
        setUpTokenStore()
        val api = FixedApiService(mutableListOf())
        val sse = ScriptedSseClient(listOf(msg(1), msg(2)))
        val repo = MessageRepository(api, sse, tokenStore, "http://example.test/")

        val received = repo.observeLive(1).take(2).toList()

        assertEquals(listOf(msg(1), msg(2)), received)
    }

    @Test
    fun `observeLive falls back to polling when SSE fails`() = runBlocking {
        setUpTokenStore()
        val api = FixedApiService(mutableListOf(listOf(msg(1))))
        val sse = ScriptedSseClient(emptyList(), failImmediately = true)
        val repo = MessageRepository(api, sse, tokenStore, "http://example.test/")
        repo.pollIntervalMs = 1L

        val received = repo.observeLive(1).take(1).toList()

        assertEquals(listOf(msg(1)), received)
    }

    @Test
    fun `observeLive dedups a message re-delivered by SSE after it was already seen via polling`() = runBlocking {
        setUpTokenStore()
        // SSE fails -> polling delivers msg(1) -> polling loop gives up after
        // its attempt cap and observeLive retries SSE -> the reconnected SSE
        // stream redelivers msg(1) (server doesn't know what the client
        // already saw) plus the genuinely-new msg(2). Only msg(2) must come
        // through a second time; msg(1) must not be emitted twice.
        val api = FixedApiService(mutableListOf(listOf(msg(1))))
        val sse = FlakySseClient(eventsOnReconnect = listOf(msg(1), msg(2)))
        val repo = MessageRepository(api, sse, tokenStore, "http://example.test/")
        repo.pollIntervalMs = 1L

        val received = repo.observeLive(1).take(2).toList()

        assertEquals(listOf(msg(1), msg(2)), received)
        assertEquals(2, sse.callCount)
    }
}
