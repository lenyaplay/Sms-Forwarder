package com.smsforwarder.viewer.ui.feed

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

private class ScriptedApiService(private val page: List<MessageDto>) : ApiService {
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
    override suspend fun listMessages(deviceId: Long, limit: Int?, beforeId: Long?, since: String?, until: String?) =
        Response.success(MessageListResponse(page, null))
}

private object NoOpSseSource : SseSource {
    override fun stream(url: String): Flow<MessageDto> = emptyFlow()
}

class MessageFeedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(messages: List<MessageDto>): MessageFeedViewModel {
        val tokenStore: TokenStore = mock()
        whenever(tokenStore.read()).thenReturn(Tokens("access", "refresh"))
        val repository = MessageRepository(ScriptedApiService(messages), NoOpSseSource, tokenStore, "http://example.test/")
        return MessageFeedViewModel(repository, SavedStateHandle(mapOf("deviceId" to 1L)))
    }

    @Test
    fun initialPageIsDisplayed() {
        val messages = listOf(
            MessageDto(1, 1, "+1", "hello", null, null, null, "2026-01-01T00:00:00Z"),
            MessageDto(2, 1, "+2", "world", null, null, null, "2026-01-01T00:00:01Z"),
        )
        composeRule.setContent {
            MessageFeedScreen(deviceName = "Test Phone", viewModel = viewModel(messages))
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(MessageFeedTestTags.messageItem(1)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun emptyFeedShowsListWithNoItems() {
        composeRule.setContent {
            MessageFeedScreen(deviceName = "Test Phone", viewModel = viewModel(emptyList()))
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(MessageFeedTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
