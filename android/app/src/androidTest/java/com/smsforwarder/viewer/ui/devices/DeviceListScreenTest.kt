package com.smsforwarder.viewer.ui.devices

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.dto.CreateBindingRequest
import com.smsforwarder.viewer.data.remote.dto.CreateBindingResponse
import com.smsforwarder.viewer.data.remote.dto.DeviceDto
import com.smsforwarder.viewer.data.remote.dto.DeviceListResponse
import com.smsforwarder.viewer.data.remote.dto.LoginRequest
import com.smsforwarder.viewer.data.remote.dto.LogoutRequest
import com.smsforwarder.viewer.data.remote.dto.MessageListResponse
import com.smsforwarder.viewer.data.remote.dto.RefreshRequest
import com.smsforwarder.viewer.data.remote.dto.TokenPairResponse
import com.smsforwarder.viewer.data.repository.DeviceRepository
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

private class ScriptedApiService(private val devices: List<DeviceDto>) : ApiService {
    override suspend fun login(request: LoginRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun refresh(request: RefreshRequest) = Response.success(TokenPairResponse("a", "r"))
    override suspend fun logout(request: LogoutRequest) = Response.success(Unit)
    override suspend fun listDevices() = Response.success(DeviceListResponse(devices))
    override suspend fun createBinding(request: CreateBindingRequest) = Response.success(CreateBindingResponse(1, "d"))
    override suspend fun listMessages(deviceId: Long, limit: Int?, beforeId: Long?, since: String?, until: String?) =
        Response.success(MessageListResponse(emptyList(), null))
}

class DeviceListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

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
    fun addButtonTriggersCallback() {
        var addClicked = false
        composeRule.setContent {
            DeviceListScreen(
                onAddDevice = { addClicked = true },
                onOpenDevice = { _, _ -> },
                viewModel = DeviceListViewModel(DeviceRepository(ScriptedApiService(emptyList()))),
            )
        }

        composeRule.onNodeWithTag(DeviceListTestTags.ADD_BUTTON).performClick()

        assert(addClicked)
    }
}
