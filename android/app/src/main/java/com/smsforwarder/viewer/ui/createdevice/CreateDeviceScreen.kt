package com.smsforwarder.viewer.ui.createdevice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

object CreateDeviceTestTags {
    const val NAME_FIELD = "create_device_name_field"
    const val CREATE_BUTTON = "create_device_create_button"
    const val ERROR_TEXT = "create_device_error_text"
    const val UPLOAD_TOKEN_TEXT = "create_device_upload_token_text"
    const val DONE_BUTTON = "create_device_done_button"
}

@Composable
fun CreateDeviceScreen(
    onDone: () -> Unit,
    viewModel: CreateDeviceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        if (uiState.createdUploadToken != null) {
            Text(text = "Device created", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Use this upload token to configure the Gateway App on the device:",
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = uiState.createdUploadToken.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(CreateDeviceTestTags.UPLOAD_TOKEN_TEXT),
            )
            Button(
                onClick = onDone,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(CreateDeviceTestTags.DONE_BUTTON),
            ) {
                Text("Done")
            }
        } else {
            Text(text = "Create a device", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Device name") },
                singleLine = true,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(CreateDeviceTestTags.NAME_FIELD),
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(CreateDeviceTestTags.ERROR_TEXT),
                )
            }

            Button(
                onClick = viewModel::create,
                enabled = !uiState.isSubmitting,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(CreateDeviceTestTags.CREATE_BUTTON),
            ) {
                Text("Create")
            }
        }
    }
}
