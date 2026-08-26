package com.smsforwarder.viewer.ui.adddevice

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.dto.CreateBindingRequest
import com.smsforwarder.viewer.data.remote.dto.CreateBindingResponse
import com.smsforwarder.viewer.data.remote.dto.DeviceListResponse
import com.smsforwarder.viewer.data.remote.dto.LoginRequest
import com.smsforwarder.viewer.data.remote.dto.LogoutRequest
import com.smsforwarder.viewer.data.remote.dto.MessageListResponse
import com.smsforwarder.viewer.data.remote.dto.RefreshRequest
import com.smsforwarder.viewer.data.remote.dto.TokenPairResponse
import com.smsforwarder.viewer.data.repository.DeviceRepository
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

private class ScriptedApiService(private val bindingResult: Response<CreateBindingResponse>) : ApiService {
    override suspend fun login(request: LoginRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)
    override suspend fun listDevices() = Response.success(DeviceListResponse(emptyList()))
    override suspend fun createBinding(request: CreateBindingRequest) = bindingResult
    override suspend fun listMessages(deviceId: Long, limit: Int?, beforeId: Long?, since: String?, until: String?) =
        Response.success(MessageListResponse(emptyList(), null))
}

class AddDeviceScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

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
