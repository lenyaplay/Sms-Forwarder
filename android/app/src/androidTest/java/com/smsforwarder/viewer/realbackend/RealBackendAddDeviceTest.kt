package com.smsforwarder.viewer.realbackend

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.viewer.data.repository.DeviceRepository
import com.smsforwarder.viewer.ui.adddevice.AddDeviceScreen
import com.smsforwarder.viewer.ui.adddevice.AddDeviceTestTags
import com.smsforwarder.viewer.ui.adddevice.AddDeviceViewModel
import org.junit.Rule
import org.junit.Test

/**
 * Drives AddDeviceScreen against a real backend: an owner mints a device and
 * a download_token via raw HTTP (no ApiService endpoint exists for that -
 * see ApiService.kt), then a separate viewer redeems it through the real UI
 * + real Retrofit/OkHttp stack. See docs/specs/0009-real-backend-integration-tests.md.
 */
class RealBackendAddDeviceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realBackend_validDownloadTokenAddsDevice() {
        val ownerLogin = uniqueLogin("add-device-owner")
        val ownerTokens = registerAndLogin(ownerLogin, "owner-password-123")
        val (deviceId, _) = createDevice(ownerTokens.access_token, "Test Device ${System.currentTimeMillis()}")
        val downloadToken = createDownloadToken(ownerTokens.access_token, deviceId)

        val viewerLogin = uniqueLogin("add-device-viewer")
        val viewerTokens = registerAndLogin(viewerLogin, "viewer-password-123")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenStore = realTokenStoreLoggedIn(context, viewerTokens)
        val viewModel = AddDeviceViewModel(DeviceRepository(realApiService(tokenStore)))

        var deviceAdded = false
        composeRule.setContent {
            AddDeviceScreen(onDeviceAdded = { deviceAdded = true }, viewModel = viewModel)
        }

        composeRule.onNodeWithTag(AddDeviceTestTags.TOKEN_FIELD).performTextInput(downloadToken)
        composeRule.onNodeWithTag(AddDeviceTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { deviceAdded }
    }
}
