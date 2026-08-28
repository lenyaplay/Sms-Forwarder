package com.smsforwarder.viewer.ui.createdevice

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.viewer.data.local.ServerConfigStore
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

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun viewModel(result: Response<DeviceCreateResponse>): CreateDeviceViewModel {
        val serverConfigStore = ServerConfigStore(context())
        serverConfigStore.save("http://test-server.example/")
        return CreateDeviceViewModel(DeviceRepository(ScriptedApiService(result)), serverConfigStore)
    }

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
    fun copyTokenButtonCopiesTheExactToken() {
        val response = Response.success(DeviceCreateResponse(9, "Kitchen phone", "up-tok-123", null, "2026-01-01T00:00:00Z"))
        composeRule.setContent {
            CreateDeviceScreen(onDone = {}, viewModel = viewModel(response))
        }

        composeRule.onNodeWithTag(CreateDeviceTestTags.NAME_FIELD).performTextInput("Kitchen phone")
        composeRule.onNodeWithTag(CreateDeviceTestTags.CREATE_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CreateDeviceTestTags.COPY_TOKEN_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(CreateDeviceTestTags.COPY_TOKEN_BUTTON).performClick()

        val clipboard = context().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        composeRule.waitUntil(timeoutMillis = 5_000) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() == "up-tok-123"
        }
    }

    @Test
    fun copyWebhookUrlButtonCopiesTheFullUrl() {
        val response = Response.success(DeviceCreateResponse(9, "Kitchen phone", "up-tok-123", null, "2026-01-01T00:00:00Z"))
        composeRule.setContent {
            CreateDeviceScreen(onDone = {}, viewModel = viewModel(response))
        }

        composeRule.onNodeWithTag(CreateDeviceTestTags.NAME_FIELD).performTextInput("Kitchen phone")
        composeRule.onNodeWithTag(CreateDeviceTestTags.CREATE_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CreateDeviceTestTags.COPY_WEBHOOK_URL_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(CreateDeviceTestTags.COPY_WEBHOOK_URL_BUTTON).performClick()

        val clipboard = context().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        composeRule.waitUntil(timeoutMillis = 5_000) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() ==
                "http://test-server.example/webhook?upload_token=up-tok-123"
        }
    }

    @Test
    fun longTokenDoesNotPushDoneButtonOffscreen() {
        val longToken = "t".repeat(160)
        val response = Response.success(DeviceCreateResponse(9, "Kitchen phone", longToken, null, "2026-01-01T00:00:00Z"))
        composeRule.setContent {
            CreateDeviceScreen(onDone = {}, viewModel = viewModel(response))
        }

        composeRule.onNodeWithTag(CreateDeviceTestTags.NAME_FIELD).performTextInput("Kitchen phone")
        composeRule.onNodeWithTag(CreateDeviceTestTags.CREATE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CreateDeviceTestTags.DONE_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }
        // assertIsDisplayed (not assertExists) - the semantics tree includes
        // off-screen nodes too, so assertExists alone would pass even if the
        // long token had actually pushed this button outside the viewport.
        composeRule.onNodeWithTag(CreateDeviceTestTags.DONE_BUTTON).assertIsDisplayed()
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
