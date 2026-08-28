package com.smsforwarder.viewer.ui.adddevice

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
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
import com.smsforwarder.viewer.data.repository.DeviceRepository
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

private class ScriptedApiService(private val bindingResult: Response<CreateBindingResponse>) : ApiService {
    override suspend fun register(request: LoginRequest) = Response.success(Unit)
    override suspend fun login(request: LoginRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)
    override suspend fun listDevices() = Response.success(DeviceListResponse(emptyList()))
    override suspend fun createDevice(request: CreateDeviceRequest) =
        Response.success(DeviceCreateResponse(1, "d", "tok", null, "2026-01-01T00:00:00Z"))
    override suspend fun createBinding(request: CreateBindingRequest) = bindingResult
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

class AddDeviceScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun validTokenAddsDeviceAndNavigatesBack() {
        var added = false
        composeRule.setContent {
            AddDeviceScreen(
                onDeviceAdded = { added = true },
                viewModel = AddDeviceViewModel(
                    DeviceRepository(ScriptedApiService(Response.success(CreateBindingResponse(1, "Phone")))),
                ),
            )
        }

        composeRule.onNodeWithTag(AddDeviceTestTags.TOKEN_FIELD).performTextInput("valid-token")
        composeRule.onNodeWithTag(AddDeviceTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { added }
    }

    @Test
    fun scannedQrTokenAddsDeviceTheSameWayAsManualEntry() {
        // QrScannerView's onScanned callback and the manual-entry submit
        // button both funnel into AddDeviceViewModel.submitToken(token) - see
        // AddDeviceScreen.kt. Camera hardware/permission isn't available in
        // this test environment, so this exercises that shared entry point
        // directly with a "scanned" value, per spec 0007's acceptance
        // criterion that QR and manual entry produce the same result.
        var added = false
        val viewModel = AddDeviceViewModel(
            DeviceRepository(ScriptedApiService(Response.success(CreateBindingResponse(1, "Phone")))),
        )
        composeRule.setContent {
            AddDeviceScreen(onDeviceAdded = { added = true }, viewModel = viewModel)
        }

        viewModel.submitToken("scanned-qr-token")

        composeRule.waitUntil(timeoutMillis = 5_000) { added }
    }

    @Test
    fun scanButtonIsVisibleRegardlessOfCameraPermissionState() {
        // Spec 0011: the scan button must always be visible (it's the request
        // entry point when permission isn't granted yet), not hidden until
        // permission already exists. Actually driving the system permission
        // dialog isn't automatable in a plain Compose test - see spec's
        // disclosed limitation - so this only asserts the button itself is
        // reachable, independent of whatever the test runner's current grant
        // state happens to be.
        composeRule.setContent {
            AddDeviceScreen(
                onDeviceAdded = {},
                viewModel = AddDeviceViewModel(
                    DeviceRepository(ScriptedApiService(Response.success(CreateBindingResponse(1, "Phone")))),
                ),
            )
        }

        composeRule.onNodeWithTag(AddDeviceTestTags.SCAN_TOGGLE).assertExists()
    }

    @Test
    fun permissionGrantedFromSystemSettingsWhileBackgroundedIsPickedUpOnResume() {
        // Regression test for the same class of bug fixed in
        // DeviceListScreenTest.screenRefreshesDeviceListOnResume: the
        // launcher callback alone only catches the in-app permission dialog,
        // not a grant made from system Settings while the app was
        // backgrounded. Revoke first so the test doesn't depend on whatever
        // grant state an earlier test in this run left behind.
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("pm revoke $packageName android.permission.CAMERA").close()

        composeRule.setContent {
            AddDeviceScreen(
                onDeviceAdded = {},
                viewModel = AddDeviceViewModel(
                    DeviceRepository(ScriptedApiService(Response.success(CreateBindingResponse(1, "Phone")))),
                ),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Scan QR code instead").fetchSemanticsNodes().isNotEmpty()
        }

        // Simulate granting via system Settings while backgrounded, then
        // returning to the app.
        uiAutomation.executeShellCommand("pm grant $packageName android.permission.CAMERA").close()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        // The permission state is now current, but showScanner is still
        // false until the user taps - proves the recheck updated
        // hasCameraPermission (a click now opens the scanner directly
        // instead of firing a redundant permission request).
        composeRule.onNodeWithTag(AddDeviceTestTags.SCAN_TOGGLE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Hide QR scanner").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun invalidTokenShowsError() {
        composeRule.setContent {
            AddDeviceScreen(
                onDeviceAdded = {},
                viewModel = AddDeviceViewModel(
                    DeviceRepository(ScriptedApiService(Response.error(401, "{}".toResponseBody()))),
                ),
            )
        }

        composeRule.onNodeWithTag(AddDeviceTestTags.TOKEN_FIELD).performTextInput("bad-token")
        composeRule.onNodeWithTag(AddDeviceTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(AddDeviceTestTags.ERROR_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AddDeviceTestTags.ERROR_TEXT).assertExists()
    }
}
