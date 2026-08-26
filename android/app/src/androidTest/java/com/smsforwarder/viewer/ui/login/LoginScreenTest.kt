package com.smsforwarder.viewer.ui.login

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.smsforwarder.viewer.data.repository.AuthRepository
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

private class ScriptedApiService(private val loginResult: Response<TokenPairResponse>) : ApiService {
    override suspend fun login(request: LoginRequest) = loginResult
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)
    override suspend fun listDevices() = Response.success(DeviceListResponse(emptyList()))
    override suspend fun createBinding(request: CreateBindingRequest) = Response.success(CreateBindingResponse(1, "d"))
    override suspend fun listMessages(deviceId: Long, limit: Int?, beforeId: Long?, since: String?, until: String?) =
        Response.success(MessageListResponse(emptyList(), null))
}

class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(loginResult: Response<TokenPairResponse>): LoginViewModel {
        val tokenStore: TokenStore = mock()
        whenever(tokenStore.read()).thenReturn(Tokens("a", "r"))
        return LoginViewModel(AuthRepository(ScriptedApiService(loginResult), tokenStore))
    }

    @Test
    fun successfulLoginNavigatesAway() {
        var loggedIn = false
        composeRule.setContent {
            LoginScreen(
                onLoggedIn = { loggedIn = true },
                viewModel = viewModel(Response.success(TokenPairResponse("acc", "ref"))),
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.USERNAME_FIELD).performTextInput("alice")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("secret")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { loggedIn }
    }

    @Test
    fun invalidCredentialsShowsError() {
        composeRule.setContent {
            LoginScreen(
                onLoggedIn = {},
                viewModel = viewModel(Response.error(401, "{}".toResponseBody())),
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.USERNAME_FIELD).performTextInput("alice")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("wrong")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(LoginTestTags.ERROR_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(LoginTestTags.ERROR_TEXT).assertExists()
    }
}
