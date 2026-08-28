package com.smsforwarder.viewer.ui.createdevice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsforwarder.viewer.ui.common.CopyTextButton
import kotlinx.coroutines.launch

object CreateDeviceTestTags {
    const val NAME_FIELD = "create_device_name_field"
    const val CREATE_BUTTON = "create_device_create_button"
    const val ERROR_TEXT = "create_device_error_text"
    const val UPLOAD_TOKEN_TEXT = "create_device_upload_token_text"
    const val COPY_TOKEN_BUTTON = "create_device_copy_token_button"
    const val COPY_WEBHOOK_URL_BUTTON = "create_device_copy_webhook_url_button"
    const val DONE_BUTTON = "create_device_done_button"
}

@Composable
fun CreateDeviceScreen(
    onDone: () -> Unit,
    viewModel: CreateDeviceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showCopiedSnackbar() {
        coroutineScope.launch { snackbarHostState.showSnackbar("Copied") }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
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
                    .fillMaxWidth()
                    .testTag(CreateDeviceTestTags.UPLOAD_TOKEN_TEXT),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                CopyTextButton(
                    label = "Copy token",
                    textToCopy = uiState.createdUploadToken.orEmpty(),
                    onCopied = ::showCopiedSnackbar,
                    modifier = Modifier.testTag(CreateDeviceTestTags.COPY_TOKEN_BUTTON),
                )
                CopyTextButton(
                    label = "Copy webhook URL",
                    textToCopy = uiState.webhookUrl.orEmpty(),
                    onCopied = ::showCopiedSnackbar,
                    modifier = Modifier.testTag(CreateDeviceTestTags.COPY_WEBHOOK_URL_BUTTON),
                )
            }
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
}
