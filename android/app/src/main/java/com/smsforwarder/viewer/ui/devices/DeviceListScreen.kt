package com.smsforwarder.viewer.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsforwarder.viewer.data.remote.dto.DeviceDto

object DeviceListTestTags {
    const val EMPTY_STATE = "device_list_empty_state"
    const val ADD_BUTTON = "device_list_add_button"
    fun deviceItem(id: Long) = "device_list_item_$id"
}

@Composable
fun DeviceListScreen(
    onAddDevice: () -> Unit,
    onOpenDevice: (deviceId: Long, deviceName: String) -> Unit,
    viewModel: DeviceListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDevice,
                modifier = Modifier.testTag(DeviceListTestTags.ADD_BUTTON),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add device")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && uiState.devices.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                uiState.devices.isEmpty() ->
                    Text(
                        text = "No devices yet. Add one using a download token.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .testTag(DeviceListTestTags.EMPTY_STATE),
                    )

                else -> DeviceList(devices = uiState.devices, onOpenDevice = onOpenDevice)
            }
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<DeviceDto>,
    onOpenDevice: (deviceId: Long, deviceName: String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(devices, key = { it.id }) { device ->
            ListItem(
                headlineContent = { Text(device.name) },
                supportingContent = { Text(device.role) },
                modifier = Modifier
                    .testTag(DeviceListTestTags.deviceItem(device.id))
                    .clickable { onOpenDevice(device.id, device.name) },
            )
        }
    }
}
