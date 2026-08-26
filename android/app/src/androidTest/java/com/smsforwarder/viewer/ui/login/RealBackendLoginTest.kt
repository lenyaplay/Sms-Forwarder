package com.smsforwarder.viewer.ui.login

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.repository.AuthRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Drives LoginScreen against a REAL backend over a real Retrofit/OkHttp
 * stack - no fake ApiService, no mocked TokenStore. Every other Login test
 * (LoginScreenTest) substitutes a ScriptedApiService that implements the
 * interface directly, which never touches kotlinx.serialization or a real
 * socket - that's exactly how two real bugs shipped undetected: LoginRequest
 * serialized as "username" instead of the backend's "login", and a missing
 * network_security_config silently blocked all cleartext traffic. Both were
 * only caught by a manual run on a physical device against a live backend.
 * This test automates that same check.
 *
 * Requires a real backend reachable at BASE_URL when this test runs - e.g.
 * `docker compose up` in backend/ plus `adb reverse tcp:8080 tcp:8080` to
 * forward the device's localhost to the host machine. Not wired into a
 * default CI run (no backend there yet, see docs/Roadmap.md Milestone 8
 * backlog item on Android+backend local integration testing); intended to
 * be run manually against a local backend before/after touching anything
 * that crosses the wire (DTOs, base URL, network config).
 */
class RealBackendLoginTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val baseUrl = "http://127.0.0.1:8080/"
    private val plainHttpClient = OkHttpClient()

    /** Registers a fresh user directly over HTTP - not on ApiService, since
     * the app deliberately has no registration screen/endpoint (spec 0007
     * assumption 9). Each test run uses a unique login to avoid colliding
     * with a previous run's user. */
    private fun registerTestUser(login: String, password: String) {
        val body = """{"login":"$login","password":"$password"}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(baseUrl + "auth/register").post(body).build()
        plainHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "could not seed test user (is the backend running at $baseUrl ?): ${response.code} ${response.body?.string()}"
            }
        }
    }

    private fun realLoginViewModel(): LoginViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(plainHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val apiService = retrofit.create(ApiService::class.java)
        val tokenStore = TokenStore(context)
        tokenStore.clear()
        return LoginViewModel(AuthRepository(apiService, tokenStore))
    }

    @Test
    fun realBackend_correctCredentialsLogIn() {
        val login = "androidtest-${System.currentTimeMillis()}"
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
        val login = "androidtest-${System.currentTimeMillis()}"
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
