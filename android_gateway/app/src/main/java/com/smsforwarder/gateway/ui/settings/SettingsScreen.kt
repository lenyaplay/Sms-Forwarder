package com.smsforwarder.gateway.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

object SettingsTestTags {
    const val SERVER_URL_FIELD = "settings_server_url_field"
    const val UPLOAD_TOKEN_FIELD = "settings_upload_token_field"
    const val SAVE_BUTTON = "settings_save_button"
    const val SAVED_CONFIRMATION = "settings_saved_confirmation"
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsContent(uiState = uiState, actions = viewModel)
}

@Composable
fun SettingsContent(uiState: SettingsUiState, actions: SettingsActions) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = actions::onServerUrlChange,
                label = { Text("Адрес сервера") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTestTags.SERVER_URL_FIELD),
            )
            OutlinedTextField(
                value = uiState.uploadToken,
                onValueChange = actions::onUploadTokenChange,
                label = { Text("Upload token") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTestTags.UPLOAD_TOKEN_FIELD),
            )
            Button(
                onClick = actions::onSave,
                enabled = uiState.canSave,
                modifier = Modifier.testTag(SettingsTestTags.SAVE_BUTTON),
            ) {
                Text("Сохранить")
            }
            if (uiState.isSaved) {
                Text("Сохранено", modifier = Modifier.testTag(SettingsTestTags.SAVED_CONFIRMATION))
            }
        }
    }
}
