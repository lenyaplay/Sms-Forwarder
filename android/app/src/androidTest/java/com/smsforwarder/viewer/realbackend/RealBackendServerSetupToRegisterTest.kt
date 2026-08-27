package com.smsforwarder.viewer.realbackend

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.viewer.data.local.ServerConfigStore
import com.smsforwarder.viewer.data.local.SessionEvents
import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.repository.AuthRepository
import com.smsforwarder.viewer.di.NetworkModule
import com.smsforwarder.viewer.ui.register.RegisterScreen
import com.smsforwarder.viewer.ui.register.RegisterTestTags
import com.smsforwarder.viewer.ui.register.RegisterViewModel
import com.smsforwarder.viewer.ui.serversetup.ServerSetupScreen
import com.smsforwarder.viewer.ui.serversetup.ServerSetupTestTags
import com.smsforwarder.viewer.ui.serversetup.ServerSetupViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The exact scenario that broke during manual device testing on 2026-08-27:
 * type the server address on the real "Server setup" screen, save it, then
 * register - against the REAL docker-compose backend (spec 0009's pattern),
 * not MockWebServer. ServerSetupThenRegisterFlowTest (outside this package)
 * already proves the NetworkModule wiring in isolation with a fake server;
 * this test proves the same UI flow against the actual backend a developer
 * points a physical device at, closing the gap a fake server can't: real
 * TLS/cleartext config, real network_security_config, real DNS/localhost
 * resolution on-device via `adb reverse`.
 *
 * Requires (see RealBackendTestSupport.kt / docs/DEVELOPMENT.md):
 *   cd backend && docker compose up -d
 *   adb reverse tcp:8080 tcp:8080
 *
 * Excluded from the default connectedAndroidTest run via this package's
 * `notPackage` exclusion in app/build.gradle.kts - run explicitly:
 *   adb shell am instrument -w -e package com.smsforwarder.viewer.realbackend \
 *     com.smsforwarder.viewer.test/com.smsforwarder.viewer.HiltTestRunner
 */
class RealBackendServerSetupToRegisterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var store: ServerConfigStore

    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private sealed interface Screen {
        data class ServerSetup(val viewModel: ServerSetupViewModel, val onDone: () -> Unit) : Screen
        data class Register(val viewModel: RegisterViewModel, val onDone: (String) -> Unit) : Screen
    }

    private var currentScreen by mutableStateOf<Screen?>(null)

    @Before
    fun setUp() {
        store = ServerConfigStore(context())
        clearSavedUrl()
    }

    @After
    fun tearDown() {
        clearSavedUrl()
    }

    private fun clearSavedUrl() {
        context().getSharedPreferences("sms_forwarder_server_config", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun buildRealApiService(baseUrl: String): ApiService {
        val client = NetworkModule.provideOkHttpClient(TokenStore(context()), SessionEvents(), store)
        val retrofit = NetworkModule.provideRetrofit(client, NetworkModule.provideJson(), baseUrl)
        return NetworkModule.provideApiService(retrofit)
    }

    @Test
    fun typingTheRealBackendUrlAndRegisteringWorksThroughTheRealScreens() {
        composeRule.setContent {
            when (val screen = currentScreen) {
                is Screen.ServerSetup -> ServerSetupScreen(onSaved = screen.onDone, viewModel = screen.viewModel)
                is Screen.Register -> RegisterScreen(
                    onRegistered = screen.onDone,
                    onBackToLogin = {},
                    viewModel = screen.viewModel,
                )
                null -> Unit
            }
        }

        // Step 1: type the real backend's address on the real Server setup screen.
        var serverSaved = false
        currentScreen = Screen.ServerSetup(ServerSetupViewModel(store), onDone = { serverSaved = true })
        composeRule.onNodeWithTag(ServerSetupTestTags.URL_FIELD).performTextInput(REAL_BACKEND_BASE_URL)
        composeRule.onNodeWithTag(ServerSetupTestTags.SAVE_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { serverSaved }
        assertEquals(REAL_BACKEND_BASE_URL, store.getUrl())

        // Step 2: build the real network stack the app itself would build on
        // its next launch, from what was just saved.
        val baseUrl = NetworkModule.provideBaseUrl(store)
        assertEquals(REAL_BACKEND_BASE_URL, baseUrl)
        val apiService = buildRealApiService(baseUrl)

        // Step 3: register a fresh user through the real Register screen
        // against the real docker-compose backend.
        val login = uniqueLogin("server-setup-flow")
        var registeredUsername: String? = null
        currentScreen = Screen.Register(
            viewModel = RegisterViewModel(AuthRepository(apiService, TokenStore(context()))),
            onDone = { registeredUsername = it },
        )
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performTextInput(login)
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performTextInput("flow-test-password")
        composeRule.onNodeWithTag(RegisterTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("flow-test-password")
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { registeredUsername == login }
    }
}
