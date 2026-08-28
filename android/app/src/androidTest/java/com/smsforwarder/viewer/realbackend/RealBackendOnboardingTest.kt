package com.smsforwarder.viewer.realbackend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.viewer.data.local.ServerConfigStore
import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.repository.AuthRepository
import com.smsforwarder.viewer.data.repository.DeviceRepository
import com.smsforwarder.viewer.ui.adddevice.AddDeviceScreen
import com.smsforwarder.viewer.ui.adddevice.AddDeviceTestTags
import com.smsforwarder.viewer.ui.adddevice.AddDeviceViewModel
import com.smsforwarder.viewer.ui.devicedetail.DeviceDetailScreen
import com.smsforwarder.viewer.ui.devicedetail.DeviceDetailTestTags
import com.smsforwarder.viewer.ui.devicedetail.DeviceDetailViewModel
import com.smsforwarder.viewer.ui.login.LoginScreen
import com.smsforwarder.viewer.ui.login.LoginTestTags
import com.smsforwarder.viewer.ui.login.LoginViewModel
import com.smsforwarder.viewer.ui.register.RegisterScreen
import com.smsforwarder.viewer.ui.register.RegisterTestTags
import com.smsforwarder.viewer.ui.register.RegisterViewModel
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end for spec 0010: registration and the QR-invite flow driven
 * entirely through the real screens/ApiService, not raw HTTP - the exact
 * scenario spec 0009's suite didn't cover (it minted users/tokens directly
 * over HTTP, bypassing the UI paths this spec adds). Device creation itself
 * still goes over raw HTTP (createDevice helper) since the app has no
 * "create device" screen - owner-side device setup happens outside the app,
 * per spec 0007 assumption 9 / spec 0010's own scoping.
 *
 * Compose test rules only allow one setContent() call per test, so all
 * screens are swapped through a single composition via [currentScreen]
 * rather than calling setContent() repeatedly.
 */
class RealBackendOnboardingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private sealed interface Screen {
        data class Register(val viewModel: RegisterViewModel, val onDone: (String) -> Unit) : Screen
        data class Login(val viewModel: LoginViewModel, val onDone: () -> Unit) : Screen
        data class DeviceDetail(val viewModel: DeviceDetailViewModel) : Screen
        data class AddDevice(val viewModel: AddDeviceViewModel, val onDone: () -> Unit) : Screen
    }

    private var currentScreen by mutableStateOf<Screen?>(null)

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun registerViaRealScreen(login: String, password: String) {
        var registered = false
        currentScreen = Screen.Register(
            viewModel = RegisterViewModel(AuthRepository(realApiService(), TokenStore(context()))),
            onDone = { registered = true },
        )
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performTextInput(login)
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performTextInput(password)
        composeRule.onNodeWithTag(RegisterTestTags.CONFIRM_PASSWORD_FIELD).performTextInput(password)
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { registered }
    }

    private fun loginViaRealScreen(login: String, password: String): TokenStore {
        val tokenStore = TokenStore(context())
        tokenStore.clear()
        var loggedIn = false
        currentScreen = Screen.Login(
            viewModel = LoginViewModel(AuthRepository(realApiService(), tokenStore)),
            onDone = { loggedIn = true },
        )
        composeRule.onNodeWithTag(LoginTestTags.USERNAME_FIELD).performTextInput(login)
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput(password)
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { loggedIn }
        return tokenStore
    }

    @Test
    fun realBackend_registerGenerateQrInviteAndRedeemThroughRealScreens() {
        composeRule.setContent {
            when (val screen = currentScreen) {
                is Screen.Register -> RegisterScreen(
                    onRegistered = screen.onDone,
                    onBackToLogin = {},
                    viewModel = screen.viewModel,
                )
                is Screen.Login -> LoginScreen(onLoggedIn = screen.onDone, viewModel = screen.viewModel)
                is Screen.DeviceDetail -> DeviceDetailScreen(
                    deviceName = "Onboarding Device",
                    onBack = {},
                    viewModel = screen.viewModel,
                )
                is Screen.AddDevice -> AddDeviceScreen(onDeviceAdded = screen.onDone, viewModel = screen.viewModel)
                null -> Unit
            }
        }

        val ownerLogin = uniqueLogin("onboarding-owner")
        val ownerPassword = "owner-password-123"
        registerViaRealScreen(ownerLogin, ownerPassword)
        val ownerTokenStore = loginViaRealScreen(ownerLogin, ownerPassword)
        val ownerAccessToken = checkNotNull(ownerTokenStore.read()).accessToken

        val (deviceId, _) = createDevice(ownerAccessToken, "Onboarding Device ${System.currentTimeMillis()}")

        val deviceDetailViewModel = DeviceDetailViewModel(
            DeviceRepository(realApiService(ownerTokenStore)),
            ServerConfigStore(context()),
            SavedStateHandle(mapOf("deviceId" to deviceId)),
        )
        currentScreen = Screen.DeviceDetail(deviceDetailViewModel)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.GENERATE_INVITE_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceDetailTestTags.GENERATE_INVITE_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            deviceDetailViewModel.uiState.value.invitedToken != null
        }
        val downloadToken = checkNotNull(deviceDetailViewModel.uiState.value.invitedToken).download_token

        val viewerLogin = uniqueLogin("onboarding-viewer")
        val viewerPassword = "viewer-password-123"
        registerViaRealScreen(viewerLogin, viewerPassword)
        val viewerTokenStore = loginViaRealScreen(viewerLogin, viewerPassword)

        var deviceAdded = false
        currentScreen = Screen.AddDevice(
            viewModel = AddDeviceViewModel(DeviceRepository(realApiService(viewerTokenStore))),
            onDone = { deviceAdded = true },
        )
        composeRule.onNodeWithTag(AddDeviceTestTags.TOKEN_FIELD).performTextInput(downloadToken)
        composeRule.onNodeWithTag(AddDeviceTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { deviceAdded }
    }
}
