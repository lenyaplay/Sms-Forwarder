package com.smsforwarder.viewer.realbackend

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.repository.AuthRepository
import com.smsforwarder.viewer.ui.login.LoginScreen
import com.smsforwarder.viewer.ui.login.LoginTestTags
import com.smsforwarder.viewer.ui.login.LoginViewModel
import org.junit.Rule
import org.junit.Test

/**
 * Drives LoginScreen against a REAL backend over a real Retrofit/OkHttp
 * stack - no fake ApiService, no mocked TokenStore. See
 * docs/specs/0009-real-backend-integration-tests.md for the full rationale
 * (two real bugs shipped undetected because every other Login test
 * substitutes a fake ApiService, bypassing kotlinx.serialization and
 * network_security_config entirely).
 */
class RealBackendLoginTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun realLoginViewModel(): LoginViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenStore = TokenStore(context)
        tokenStore.clear()
        return LoginViewModel(AuthRepository(realApiService(), tokenStore))
    }

    @Test
    fun realBackend_correctCredentialsLogIn() {
        val login = uniqueLogin("login")
        val password = "test-password-123"
        registerTestUser(login, password)

        var loggedIn = false
        composeRule.setContent {
            LoginScreen(onLoggedIn = { loggedIn = true }, viewModel = realLoginViewModel())
        }

        composeRule.onNodeWithTag(LoginTestTags.USERNAME_FIELD).performTextInput(login)
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput(password)
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { loggedIn }
    }

    @Test
    fun realBackend_wrongPasswordShowsError() {
        val login = uniqueLogin("login-wrong-pw")
        registerTestUser(login, "correct-password")

        composeRule.setContent {
            LoginScreen(onLoggedIn = {}, viewModel = realLoginViewModel())
        }

        composeRule.onNodeWithTag(LoginTestTags.USERNAME_FIELD).performTextInput(login)
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("wrong-password")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(LoginTestTags.ERROR_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(LoginTestTags.ERROR_TEXT).assertExists()
    }
}
