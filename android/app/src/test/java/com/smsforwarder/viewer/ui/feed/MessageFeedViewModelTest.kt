package com.smsforwarder.viewer.ui.feed

import androidx.lifecycle.SavedStateHandle
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
import com.smsforwarder.viewer.data.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

private class PagedApiService(private val pages: MutableList<MessageListResponse>) : ApiService {
    override suspend fun register(request: LoginRequest) = Response.success(Unit)
    override suspend fun login(request: LoginRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)
    override suspend fun listDevices() = Response.success(DeviceListResponse(emptyList()))
    override suspend fun createDevice(request: CreateDeviceRequest) =
        Response.success(DeviceCreateResponse(1, "d", "tok", null, "2026-01-01T00:00:00Z"))
    override suspend fun createBinding(request: CreateBindingRequest) = Response.success(CreateBindingResponse(1, "d"))
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
        val page = if (pages.isNotEmpty()) pages.removeAt(0) else MessageListResponse(emptyList(), null)
        return Response.success(page)
    }
}

private object NoOpSseSource : SseSource {
    override fun stream(url: String): Flow<MessageDto> = emptyFlow()
}

/**
 * MessageFeedViewModel.init() eagerly starts observeLive(), which is an
 * intentionally-unbounded SSE+polling retry loop (production behavior, see
 * MessageRepository). runTest() always drains the scheduler fully before
 * returning (an implicit advanceUntilIdle() at the end, beyond what the test
 * body itself calls), so any test that lets that job stay alive would hang
 * forever pumping its virtual-time delay() reschedules - every test here
 * must call vm.stopLiveUpdates() before returning to leave nothing pending.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(pages: MutableList<MessageListResponse>): MessageFeedViewModel {
        val tokenStore: TokenStore = mock()
        whenever(tokenStore.read()).thenReturn(Tokens("access", "refresh"))
        val repository = MessageRepository(PagedApiService(pages), NoOpSseSource, tokenStore, "http://example.test/")
        return MessageFeedViewModel(repository, SavedStateHandle(mapOf("deviceId" to 1L)))
    }

    private fun msg(id: Long) = MessageDto(id, 1, "+1", "text", null, null, null, "2026-01-01T00:00:0${id}Z")

    @Test
    fun `initial page loads messages and next cursor`() = runTest(dispatcher) {
        val vm = viewModel(mutableListOf(MessageListResponse(listOf(msg(2), msg(1)), 1)))
        dispatcher.scheduler.runCurrent()

        val state = vm.uiState.value
        assertEquals(listOf(msg(2), msg(1)), state.messages)
        assertEquals(1L, state.nextBeforeId)
        assertEquals(true, state.hasMore)

        vm.stopLiveUpdates()
    }

    @Test
    fun `loadMore appends older page and clears cursor on last page`() = runTest(dispatcher) {
        val vm = viewModel(
            mutableListOf(
                MessageListResponse(listOf(msg(2)), 1),
                MessageListResponse(listOf(msg(1)), null),
            ),
        )
        dispatcher.scheduler.runCurrent()

        vm.loadMore()
        dispatcher.scheduler.runCurrent()

        val state = vm.uiState.value
        assertEquals(listOf(msg(2), msg(1)), state.messages)
        assertEquals(null, state.nextBeforeId)
        assertEquals(false, state.hasMore)

        vm.stopLiveUpdates()
    }

    @Test
    fun `resumeLiveUpdates after stopLiveUpdates reloads the feed and restarts live updates`() = runTest(dispatcher) {
        // Simulates ON_STOP (screen backgrounded) followed by ON_START
        // (screen foregrounded again) per spec 0007 assumption 7 - the feed
        // must resync, not just silently stay stale.
        val vm = viewModel(
            mutableListOf(
                MessageListResponse(listOf(msg(1)), null), // initial load
                MessageListResponse(listOf(msg(1), msg(2)), null), // resync after resume picks up msg(2)
            ),
        )
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(msg(1)), vm.uiState.value.messages)

        vm.stopLiveUpdates()
        vm.resumeLiveUpdates()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(msg(1), msg(2)), vm.uiState.value.messages)

        vm.stopLiveUpdates()
    }

    @Test
    fun `resumeLiveUpdates is a no-op while live updates are already running`() = runTest(dispatcher) {
        val vm = viewModel(mutableListOf(MessageListResponse(listOf(msg(1)), null)))
        dispatcher.scheduler.runCurrent()

        // Not preceded by stopLiveUpdates() - should not trigger a redundant reload.
        vm.resumeLiveUpdates()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(msg(1)), vm.uiState.value.messages)

        vm.stopLiveUpdates()
    }
}
