package com.smsforwarder.viewer.ui.serversetup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

object ServerSetupTestTags {
    const val URL_FIELD = "server_setup_url_field"
    const val SAVE_BUTTON = "server_setup_save_button"
    const val ERROR_TEXT = "server_setup_error_text"
}

@Composable
fun ServerSetupScreen(
    onSaved: () -> Unit,
    viewModel: ServerSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Server setup", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Enter the address of your sms_forwarder backend.",
            modifier = Modifier.padding(top = 8.dp),
        )

        OutlinedTextField(
            value = uiState.url,
            onValueChange = viewModel::onUrlChange,
            label = { Text("Server URL") },
            singleLine = true,
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(ServerSetupTestTags.URL_FIELD),
        )

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(ServerSetupTestTags.ERROR_TEXT),
            )
        }

        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(ServerSetupTestTags.SAVE_BUTTON),
        ) {
            Text("Save")
        }
    }
}
