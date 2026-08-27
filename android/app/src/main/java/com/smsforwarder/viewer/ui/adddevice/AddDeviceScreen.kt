package com.smsforwarder.viewer.ui.adddevice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel

object AddDeviceTestTags {
    const val TOKEN_FIELD = "add_device_token_field"
    const val SUBMIT_BUTTON = "add_device_submit_button"
    const val ERROR_TEXT = "add_device_error_text"
    const val SCAN_TOGGLE = "add_device_scan_toggle"
}

@Composable
fun AddDeviceScreen(
    onDeviceAdded: () -> Unit,
    viewModel: AddDeviceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) showScanner = true
    }

    LaunchedEffect(uiState.addedDeviceName) {
        if (uiState.addedDeviceName != null) onDeviceAdded()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Add a device", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = uiState.token,
            onValueChange = viewModel::onTokenChange,
            label = { Text("Download token") },
            singleLine = true,
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(AddDeviceTestTags.TOKEN_FIELD),
        )

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(AddDeviceTestTags.ERROR_TEXT),
            )
        }

        Button(
            onClick = { viewModel.submitToken() },
            enabled = !uiState.isSubmitting,
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(AddDeviceTestTags.SUBMIT_BUTTON),
        ) {
            Text("Add device")
        }

        TextButton(
            onClick = {
                when {
                    !hasCameraPermission -> permissionLauncher.launch(Manifest.permission.CAMERA)
                    else -> showScanner = !showScanner
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(AddDeviceTestTags.SCAN_TOGGLE),
        ) {
            Text(if (hasCameraPermission && showScanner) "Hide QR scanner" else "Scan QR code instead")
        }

        when {
            hasCameraPermission && showScanner -> QrScannerView(
                onScanned = { scanned ->
                    showScanner = false
                    viewModel.submitToken(scanned)
                },
                modifier = Modifier.padding(top = 16.dp),
            )
            !hasCameraPermission -> Text(
                text = "Grant camera permission to scan a QR code, or enter the token manually.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
