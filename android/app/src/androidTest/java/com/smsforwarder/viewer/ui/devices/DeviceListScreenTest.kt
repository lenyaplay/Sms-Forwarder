package com.smsforwarder.viewer.ui.devices

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
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

private class ScriptedApiService(var devices: List<DeviceDto>) : ApiService {
    override suspend fun register(request: LoginRequest) = Response.success(Unit)
    override suspend fun login(request: LoginRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)
    override suspend fun listDevices() = Response.success(DeviceListResponse(devices))
    override suspend fun createDevice(request: CreateDeviceRequest) =
        Response.success(DeviceCreateResponse(1, "d", "tok", null, "2026-01-01T00:00:00Z"))
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

class DeviceListScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyStateShownWhenNoDevices() {
        composeRule.setContent {
            DeviceListScreen(
                onAddDevice = {},
                onOpenDevice = { _, _ -> },
                viewModel = DeviceListViewModel(DeviceRepository(ScriptedApiService(emptyList()))),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceListTestTags.EMPTY_STATE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun devicesAreListedAndClickable() {
        var openedDeviceId: Long? = null
        val devices = listOf(DeviceDto(1, "Phone", "owner", null, null, "2026-01-01T00:00:00Z"))
        composeRule.setContent {
            DeviceListScreen(
                onAddDevice = {},
                onOpenDevice = { id, _ -> openedDeviceId = id },
                viewModel = DeviceListViewModel(DeviceRepository(ScriptedApiService(devices))),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceListTestTags.deviceItem(1)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceListTestTags.deviceItem(1)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { openedDeviceId == 1L }
    }

    @Test
    fun addButtonOpensMenuAndJoinByTokenTriggersCallback() {
        var addClicked = false
        composeRule.setContent {
            DeviceListScreen(
                onAddDevice = { addClicked = true },
                onOpenDevice = { _, _ -> },
                viewModel = DeviceListViewModel(DeviceRepository(ScriptedApiService(emptyList()))),
            )
        }

        composeRule.onNodeWithTag(DeviceListTestTags.ADD_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceListTestTags.JOIN_BY_TOKEN_MENU_ITEM).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceListTestTags.JOIN_BY_TOKEN_MENU_ITEM).performClick()

        assert(addClicked)
    }

    @Test
    fun addButtonOpensMenuAndCreateDeviceTriggersCallback() {
        var createClicked = false
        composeRule.setContent {
            DeviceListScreen(
                onAddDevice = {},
                onOpenDevice = { _, _ -> },
                onCreateDevice = { createClicked = true },
                viewModel = DeviceListViewModel(DeviceRepository(ScriptedApiService(emptyList()))),
            )
        }

        composeRule.onNodeWithTag(DeviceListTestTags.ADD_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceListTestTags.CREATE_DEVICE_MENU_ITEM).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DeviceListTestTags.CREATE_DEVICE_MENU_ITEM).performClick()

        assert(createClicked)
    }

    @Test
    fun screenRefreshesDeviceListOnResume() {
        // Regression test: the ViewModel used to only fetch once in init(),
        // so returning here after creating a device or joining one by token
        // (both via popBackStack, which resumes this screen's own
        // NavBackStackEntry lifecycle rather than recreating the ViewModel)
        // kept showing stale data. Found manually while closing out spec
        // 0011 - a freshly-created device didn't appear until the whole app
        // was restarted.
        val api = ScriptedApiService(emptyList())
        composeRule.setContent {
            DeviceListScreen(
                onAddDevice = {},
                onOpenDevice = { _, _ -> },
                viewModel = DeviceListViewModel(DeviceRepository(api)),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceListTestTags.EMPTY_STATE).fetchSemanticsNodes().isNotEmpty()
        }

        // Simulate the backend now having a device the screen doesn't know
        // about yet, then simulate returning to this screen (ON_RESUME).
        api.devices = listOf(DeviceDto(1, "New Phone", "owner", null, null, "2026-01-01T00:00:00Z"))
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeviceListTestTags.deviceItem(1)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
