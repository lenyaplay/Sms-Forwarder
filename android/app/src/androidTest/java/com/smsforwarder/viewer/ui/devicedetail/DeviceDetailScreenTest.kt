package com.smsforwarder.viewer.ui.devicedetail

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.viewer.data.local.ServerConfigStore
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.dto.CreateBindingRequest
import com.smsforwarder.viewer.data.remote.dto.CreateBindingResponse
import com.smsforwarder.viewer.data.remote.dto.CreateDeviceRequest
import com.smsforwarder.viewer.data.remote.dto.CreateDownloadTokenRequest
import com.smsforwarder.viewer.data.remote.dto.DeviceCreateResponse
import com.smsforwarder.viewer.data.remote.dto.DeviceDto
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
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

private class ScriptedApiService(
    private val devices: List<DeviceDto>,
    private val downloadTokens: MutableList<DownloadTokenDto>,
    private val createDownloadTokenResult: Response<DownloadTokenDto> =
        Response.success(DownloadTokenDto(99, "new-invite-token", null, null, null, "2026-01-01T00:00:00Z")),
) : ApiService {
    override suspend fun register(request: LoginRequest) = Response.success(Unit)
    override suspend fun login(request: LoginRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)
    override suspend fun listDevices() = Response.success(DeviceListResponse(devices))
    override suspend fun createDevice(request: CreateDeviceRequest) =
        Response.success(DeviceCreateResponse(1, "d", "tok", null, "2026-01-01T00:00:00Z"))
    override suspend fun createBinding(request: CreateBindingRequest) = Response.success(CreateBindingResponse(1, "d"))
    override suspend fun createDownloadToken(deviceId: Long, request: CreateDownloadTokenRequest) =
        createDownloadTokenResult
    // .toList() defensively copies - otherwise revokeDownloadToken's in-place
    // removeAll on this same backing list would retroactively mutate any
    // DownloadTokenListResponse/state already handed out from an earlier call.
    override suspend fun listDownloadTokens(deviceId: Long) =
        Response.success(DownloadTokenListResponse(downloadTokens.toList()))
    override suspend fun revokeDownloadToken(deviceId: Long, tokenId: Long): Response<RevokeDownloadTokenResponse> {
        downloadTokens.removeAll { it.id == tokenId }
        return Response.success(RevokeDownloadTokenResponse(1))
    }
    override suspend fun reissueUploadToken(deviceId: Long, request: ReissueUploadTokenRequest) =
        Response.success(ReissueUploadTokenResponse("reissued-token", null))
    override suspend fun listMessages(deviceId: Long, limit: Int?, beforeId: Long?, since: String?, until: String?) =
        Response.success(MessageListResponse(emptyList(), null))
}

class DeviceDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun device(uploadToken: String = "current-upload-token") =
        DeviceDto(1, "Phone", "owner", uploadToken, null, "2026-01-01T00:00:00Z")

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun serverConfigStore(): ServerConfigStore {
        val store = ServerConfigStore(context())
        store.save("http://test-server.example/")
        return store
    }

    @Test
    fun uploadTokenIsDisplayed() {
        val viewModel = DeviceDetailViewModel(
            DeviceRepository(ScriptedApiService(listOf(device()), mutableListOf())),
            serverConfigStore(),
            SavedStateHandle(mapOf("deviceId" to 1L)),
        )
        composeRule.setContent {
            DeviceDetailScreen(deviceName = "Phone", onBack = {}, viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.UPLOAD_TOKEN_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceDetailTestTags.UPLOAD_TOKEN_TEXT).assertExists()
    }

    @Test
    fun generateInviteShowsQrAndToken() {
        val viewModel = DeviceDetailViewModel(
            DeviceRepository(ScriptedApiService(listOf(device()), mutableListOf())),
            serverConfigStore(),
            SavedStateHandle(mapOf("deviceId" to 1L)),
        )
        composeRule.setContent {
            DeviceDetailScreen(deviceName = "Phone", onBack = {}, viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.GENERATE_INVITE_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceDetailTestTags.GENERATE_INVITE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.INVITE_QR_IMAGE).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceDetailTestTags.INVITE_TOKEN_TEXT).assertExists()
    }

    @Test
    fun revokingListedTokenRemovesItFromTheList() {
        val existingToken = DownloadTokenDto(5, "existing-token", null, null, null, "2026-01-01T00:00:00Z")
        val viewModel = DeviceDetailViewModel(
            DeviceRepository(ScriptedApiService(listOf(device()), mutableListOf(existingToken))),
            serverConfigStore(),
            SavedStateHandle(mapOf("deviceId" to 1L)),
        )
        composeRule.setContent {
            DeviceDetailScreen(deviceName = "Phone", onBack = {}, viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.revokeButton(5)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceDetailTestTags.revokeButton(5)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.CONFIRM_REVOKE_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceDetailTestTags.CONFIRM_REVOKE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.downloadTokenItem(5)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun copyUploadTokenButtonCopiesTheExactToken() {
        val viewModel = DeviceDetailViewModel(
            DeviceRepository(ScriptedApiService(listOf(device("up-tok-123")), mutableListOf())),
            serverConfigStore(),
            SavedStateHandle(mapOf("deviceId" to 1L)),
        )
        composeRule.setContent {
            DeviceDetailScreen(deviceName = "Phone", onBack = {}, viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.COPY_UPLOAD_TOKEN_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceDetailTestTags.COPY_UPLOAD_TOKEN_BUTTON).performClick()

        val clipboard = context().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        composeRule.waitUntil(timeoutMillis = 5_000) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() == "up-tok-123"
        }
    }

    @Test
    fun copyWebhookUrlButtonCopiesTheFullUrl() {
        val viewModel = DeviceDetailViewModel(
            DeviceRepository(ScriptedApiService(listOf(device("up-tok-123")), mutableListOf())),
            serverConfigStore(),
            SavedStateHandle(mapOf("deviceId" to 1L)),
        )
        composeRule.setContent {
            DeviceDetailScreen(deviceName = "Phone", onBack = {}, viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.COPY_WEBHOOK_URL_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceDetailTestTags.COPY_WEBHOOK_URL_BUTTON).performClick()

        val clipboard = context().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        composeRule.waitUntil(timeoutMillis = 5_000) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() ==
                "http://test-server.example/webhook?upload_token=up-tok-123"
        }
    }

    @Test
    fun copyDownloadTokenButtonCopiesTheExactToken() {
        val existingToken = DownloadTokenDto(5, "existing-token", null, null, null, "2026-01-01T00:00:00Z")
        val viewModel = DeviceDetailViewModel(
            DeviceRepository(ScriptedApiService(listOf(device()), mutableListOf(existingToken))),
            serverConfigStore(),
            SavedStateHandle(mapOf("deviceId" to 1L)),
        )
        composeRule.setContent {
            DeviceDetailScreen(deviceName = "Phone", onBack = {}, viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.copyDownloadTokenButton(5)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceDetailTestTags.copyDownloadTokenButton(5)).performClick()

        val clipboard = context().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        composeRule.waitUntil(timeoutMillis = 5_000) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() == "existing-token"
        }
    }

    @Test
    fun longDownloadTokenDoesNotPushRevokeButtonOffscreen() {
        val longToken = DownloadTokenDto(5, "t".repeat(160), null, null, null, "2026-01-01T00:00:00Z")
        val viewModel = DeviceDetailViewModel(
            DeviceRepository(ScriptedApiService(listOf(device()), mutableListOf(longToken))),
            serverConfigStore(),
            SavedStateHandle(mapOf("deviceId" to 1L)),
        )
        composeRule.setContent {
            DeviceDetailScreen(deviceName = "Phone", onBack = {}, viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceDetailTestTags.revokeButton(5)).fetchSemanticsNodes().isNotEmpty()
        }
        // assertIsDisplayed (not assertExists) - the semantics tree includes
        // off-screen nodes too, so assertExists alone would pass even if the
        // long token had actually pushed this button outside the viewport.
        composeRule.onNodeWithTag(DeviceDetailTestTags.revokeButton(5)).assertIsDisplayed()
    }
}
