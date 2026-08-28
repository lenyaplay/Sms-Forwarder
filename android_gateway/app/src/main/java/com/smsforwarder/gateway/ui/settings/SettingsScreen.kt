package com.smsforwarder.gateway.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

object SettingsTestTags {
    const val SERVER_URL_FIELD = "settings_server_url_field"
    const val UPLOAD_TOKEN_FIELD = "settings_upload_token_field"
    const val SAVE_BUTTON = "settings_save_button"
    const val SAVED_CONFIRMATION = "settings_saved_confirmation"
    const val COPY_TOKEN_BUTTON = "settings_copy_token_button"
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsContent(uiState = uiState, actions = viewModel)
}

@Composable
fun SettingsContent(uiState: SettingsUiState, actions: SettingsActions) {
    val clipboardManager = LocalClipboardManager.current
    Scaffold { padding ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = actions::onServerUrlChange,
                    label = { Text("Адрес сервера") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SettingsTestTags.SERVER_URL_FIELD),
                )
                Text(
                    text = "Например, https://sms.example.com — адрес, куда ваш backend принимает вебхуки.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = uiState.uploadToken,
                        onValueChange = actions::onUploadTokenChange,
                        label = { Text("Upload token") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(SettingsTestTags.UPLOAD_TOKEN_FIELD),
                    )
                    TextButton(
                        onClick = { clipboardManager.setText(AnnotatedString(uiState.uploadToken)) },
                        enabled = uiState.uploadToken.isNotBlank(),
                        modifier = Modifier.testTag(SettingsTestTags.COPY_TOKEN_BUTTON),
                    ) {
                        Text("Скопировать")
                    }
                }
                Text(
                    text = "Токен выдаётся при создании устройства-шлюза в Viewer App (экран управления устройством).",
                    style = MaterialTheme.typography.bodySmall,
                )

                Button(
                    onClick = actions::onSave,
                    enabled = uiState.canSave,
                    modifier = Modifier.padding(top = 12.dp).testTag(SettingsTestTags.SAVE_BUTTON),
                ) {
                    Text("Сохранить")
                }
                if (uiState.isSaved) {
                    Text("Сохранено", modifier = Modifier.testTag(SettingsTestTags.SAVED_CONFIRMATION))
                }
            }
        }
    }
}
