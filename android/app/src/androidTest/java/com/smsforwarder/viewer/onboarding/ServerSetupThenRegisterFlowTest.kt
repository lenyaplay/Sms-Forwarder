package com.smsforwarder.viewer.onboarding

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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Full onboarding flow through the real screens, end to end: the user types
 * a server URL on the "Server setup" screen, saves it, and that exact URL is
 * what the app's real network stack (NetworkModule's provider chain, not a
 * fake ApiService) then uses for registration. This is the flow that broke
 * manually on a physical device (a stale/wrong URL was still being used for
 * real requests) - this test pins the whole path down so a regression here
 * fails a build instead of only showing up on a device.
 *
 * ServerConfigStore is real SharedPreferences (spec 0010 assumption 9 - not
 * Keystore-encrypted), so this test cleans its own prefs entry before and
 * after running to avoid leaking a MockWebServer URL into a developer's
 * manually-tested app state (exactly the contamination that bit manual
 * testing on 2026-08-27).
 */
class ServerSetupThenRegisterFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var store: ServerConfigStore

    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private sealed interface Screen {
        data class ServerSetup(val viewModel: ServerSetupViewModel, val onDone: () -> Unit) : Screen
        data class Register(val viewModel: RegisterViewModel, val onDone: (String) -> Unit) : Screen
    }

    private var currentScreen by mutableStateOf<Screen?>(null)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = ServerConfigStore(context())
        clearSavedUrl()
    }

    @After
    fun tearDown() {
        server.shutdown()
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
    fun savedServerUrlIsUsedForRealRegistrationRequest() {
        server.enqueue(MockResponse().setResponseCode(201))

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

        // Step 1: type the server URL on the real Server setup screen and save it.
        var serverSaved = false
        currentScreen = Screen.ServerSetup(ServerSetupViewModel(store), onDone = { serverSaved = true })
        val typedUrl = server.url("/").toString()
        composeRule.onNodeWithTag(ServerSetupTestTags.URL_FIELD).performTextInput(typedUrl)
        composeRule.onNodeWithTag(ServerSetupTestTags.SAVE_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { serverSaved }
        assertEquals(typedUrl, store.getUrl())

        // Step 2: build the real network stack from whatever NetworkModule reads
        // back out of the store right now - proving the saved value, not a
        // fallback or stale value, is what drives it.
        val baseUrl = NetworkModule.provideBaseUrl(store)
        assertEquals(typedUrl, baseUrl)
        val apiService = buildRealApiService(baseUrl)

        // Step 3: register through the real Register screen against that stack.
        var registeredUsername: String? = null
        currentScreen = Screen.Register(
            viewModel = RegisterViewModel(AuthRepository(apiService, TokenStore(context()))),
            onDone = { registeredUsername = it },
        )
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performTextInput("flow-test-user")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performTextInput("flow-test-password")
        composeRule.onNodeWithTag(RegisterTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("flow-test-password")
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { registeredUsername == "flow-test-user" }

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("expected the real registration request to reach the configured server", request)
        assertEquals("/auth/register", request!!.path)
    }
}
