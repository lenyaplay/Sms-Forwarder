package com.smsforwarder.viewer.ui.register

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smsforwarder.viewer.data.local.TokenStore
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
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import retrofit2.Response

private class ScriptedApiService(private val registerResult: Response<Unit>) : ApiService {
    override suspend fun register(request: LoginRequest) = registerResult
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

class RegisterScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(registerResult: Response<Unit>): RegisterViewModel {
        val tokenStore: TokenStore = mock()
        return RegisterViewModel(AuthRepository(ScriptedApiService(registerResult), tokenStore))
    }

    @Test
    fun successfulRegistrationReturnsUsername() {
        var registered: String? = null
        composeRule.setContent {
            RegisterScreen(
                onRegistered = { registered = it },
                onBackToLogin = {},
                viewModel = viewModel(Response.success(Unit)),
            )
        }

        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performTextInput("newuser")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performTextInput("secretpw")
        composeRule.onNodeWithTag(RegisterTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("secretpw")
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { registered == "newuser" }
    }

    @Test
    fun mismatchedPasswordsShowsErrorWithoutCallingServer() {
        composeRule.setContent {
            RegisterScreen(
                onRegistered = {},
                onBackToLogin = {},
                viewModel = viewModel(Response.success(Unit)),
            )
        }

        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performTextInput("newuser")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performTextInput("secretpw")
        composeRule.onNodeWithTag(RegisterTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("different")
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(RegisterTestTags.ERROR_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun takenLoginShowsError() {
        composeRule.setContent {
            RegisterScreen(
                onRegistered = {},
                onBackToLogin = {},
                viewModel = viewModel(Response.error(409, "{}".toResponseBody())),
            )
        }

        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performTextInput("existing")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performTextInput("secretpw")
        composeRule.onNodeWithTag(RegisterTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("secretpw")
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(RegisterTestTags.ERROR_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun passwordAndConfirmPasswordVisibilityToggleIndependently() {
        composeRule.setContent {
            RegisterScreen(
                onRegistered = {},
                onBackToLogin = {},
                viewModel = viewModel(Response.success(Unit)),
            )
        }

        composeRule.onNodeWithTag(RegisterTestTags.TOGGLE_PASSWORD_VISIBILITY).assertTextEquals("Show")
        composeRule.onNodeWithTag(RegisterTestTags.TOGGLE_CONFIRM_PASSWORD_VISIBILITY).assertTextEquals("Show")

        composeRule.onNodeWithTag(RegisterTestTags.TOGGLE_PASSWORD_VISIBILITY).performClick()

        composeRule.onNodeWithTag(RegisterTestTags.TOGGLE_PASSWORD_VISIBILITY).assertTextEquals("Hide")
        composeRule.onNodeWithTag(RegisterTestTags.TOGGLE_CONFIRM_PASSWORD_VISIBILITY).assertTextEquals("Show")
    }
}
