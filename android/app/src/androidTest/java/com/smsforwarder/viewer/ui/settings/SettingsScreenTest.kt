package com.smsforwarder.viewer.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smsforwarder.viewer.data.local.ServerConfigStore
import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.local.Tokens
import com.smsforwarder.viewer.data.remote.ApiService
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
import com.smsforwarder.viewer.data.remote.dto.MessageListResponse
import com.smsforwarder.viewer.data.remote.dto.RefreshRequest
import com.smsforwarder.viewer.data.remote.dto.ReissueUploadTokenRequest
import com.smsforwarder.viewer.data.remote.dto.ReissueUploadTokenResponse
import com.smsforwarder.viewer.data.remote.dto.RevokeDownloadTokenResponse
import com.smsforwarder.viewer.data.remote.dto.TokenPairResponse
import com.smsforwarder.viewer.data.repository.AuthRepository
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

private class NoOpApiService : ApiService {
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
        Response.success(MessageListResponse(emptyList(), null))
}

class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(serverUrl: String): SettingsViewModel {
        val tokenStore: TokenStore = mock()
        whenever(tokenStore.read()).thenReturn(Tokens("a", "r"))
        val serverConfigStore: ServerConfigStore = mock()
        whenever(serverConfigStore.getUrl()).thenReturn(serverUrl)
        return SettingsViewModel(AuthRepository(NoOpApiService(), tokenStore), serverConfigStore)
    }

    @Test
    fun displaysCurrentServerUrl() {
        composeRule.setContent {
            SettingsScreen(onLoggedOut = {}, onServerChangeRequested = {}, viewModel = viewModel("https://example.com"))
        }

        composeRule.onNodeWithText("https://example.com").assertExists()
    }

    @Test
    fun logoutButtonNavigatesToLoggedOut() {
        var loggedOut = false
        composeRule.setContent {
            SettingsScreen(
                onLoggedOut = { loggedOut = true },
                onServerChangeRequested = {},
                viewModel = viewModel("https://example.com"),
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.LOGOUT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { loggedOut }
    }

    @Test
    fun changeServerButtonLogsOutAndRequestsServerChange() {
        var serverChangeRequested = false
        composeRule.setContent {
            SettingsScreen(
                onLoggedOut = {},
                onServerChangeRequested = { serverChangeRequested = true },
                viewModel = viewModel("https://example.com"),
            )
        }

        composeRule.onNodeWithTag(SettingsTestTags.CHANGE_SERVER_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { serverChangeRequested }
    }
}
