package com.smsforwarder.viewer.ui.createdevice

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

private class ScriptedApiService(private val createDeviceResult: Response<DeviceCreateResponse>) : ApiService {
    override suspend fun register(request: LoginRequest) = Response.success(Unit)
    override suspend fun login(request: LoginRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)
    override suspend fun listDevices() = Response.success(DeviceListResponse(emptyList()))
    override suspend fun createDevice(request: CreateDeviceRequest) = createDeviceResult
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

class CreateDeviceScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(result: Response<DeviceCreateResponse>) =
        CreateDeviceViewModel(DeviceRepository(ScriptedApiService(result)))

    @Test
    fun successfulCreationShowsUploadTokenAndDone() {
        val response = Response.success(DeviceCreateResponse(9, "Kitchen phone", "up-tok-123", null, "2026-01-01T00:00:00Z"))
        composeRule.setContent {
            CreateDeviceScreen(onDone = {}, viewModel = viewModel(response))
        }

        composeRule.onNodeWithTag(CreateDeviceTestTags.NAME_FIELD).performTextInput("Kitchen phone")
        composeRule.onNodeWithTag(CreateDeviceTestTags.CREATE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CreateDeviceTestTags.UPLOAD_TOKEN_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(CreateDeviceTestTags.UPLOAD_TOKEN_TEXT).assertExists()
        composeRule.onNodeWithTag(CreateDeviceTestTags.DONE_BUTTON).assertExists()
    }

    @Test
    fun failedCreationShowsError() {
        composeRule.setContent {
            CreateDeviceScreen(onDone = {}, viewModel = viewModel(Response.error(500, "{}".toResponseBody())))
        }

        composeRule.onNodeWithTag(CreateDeviceTestTags.NAME_FIELD).performTextInput("Kitchen phone")
        composeRule.onNodeWithTag(CreateDeviceTestTags.CREATE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CreateDeviceTestTags.ERROR_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun blankNameShowsErrorWithoutCallingServer() {
        composeRule.setContent {
            CreateDeviceScreen(
                onDone = {},
                viewModel = viewModel(Response.success(DeviceCreateResponse(1, "d", "tok", null, "2026-01-01T00:00:00Z"))),
            )
        }

        composeRule.onNodeWithTag(CreateDeviceTestTags.CREATE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CreateDeviceTestTags.ERROR_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
