package com.smsforwarder.gateway.ui.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.BackoffPolicy
import com.smsforwarder.gateway.data.remote.TestConnectionResult
import com.smsforwarder.gateway.ui.common.ConfirmDialog

object DeliveryTestTags {
    const val SERVER_URL_FIELD = "delivery_server_url_field"
    const val UPLOAD_TOKEN_FIELD = "delivery_upload_token_field"
    const val COPY_TOKEN_BUTTON = "delivery_copy_token_button"
    const val MAX_ATTEMPTS_FIELD = "delivery_max_attempts_field"
    const val MAX_ATTEMPTS_ERROR = "delivery_max_attempts_error"
    const val BASE_INTERVAL_FIELD = "delivery_base_interval_field"
    const val BASE_INTERVAL_ERROR = "delivery_base_interval_error"
    const val BACKOFF_EXPONENTIAL = "delivery_backoff_exponential"
    const val BACKOFF_LINEAR = "delivery_backoff_linear"
    const val FORWARDING_PAUSED_SWITCH = "delivery_forwarding_paused_switch"
    const val DELETE_AFTER_FORWARD_SWITCH = "delivery_delete_after_forward_switch"
    const val HIDE_CONTACT_NAME_SWITCH = "delivery_hide_contact_name_switch"
    const val TEST_CONNECTION_BUTTON = "delivery_test_connection_button"
    const val TEST_CONNECTION_RESULT = "delivery_test_connection_result"
    const val SAVE_BUTTON = "delivery_save_button"
    const val SAVED_CONFIRMATION = "delivery_saved_confirmation"
    const val RESET_BUTTON = "delivery_reset_button"
}

@Composable
fun DeliveryScreen(viewModel: DeliveryViewModel = hiltViewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    DeliveryContent(uiState = uiState, actions = viewModel, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryContent(uiState: DeliveryUiState, actions: DeliveryActions, onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var showResetConfirm by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Доставка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = actions::onServerUrlChange,
                label = { Text("Адрес сервера") },
                modifier = Modifier.fillMaxWidth().testTag(DeliveryTestTags.SERVER_URL_FIELD),
            )
            Text(
                text = "Например, https://sms.example.com — адрес, куда ваш backend принимает вебхуки.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.uploadToken,
                    onValueChange = actions::onUploadTokenChange,
                    label = { Text("Upload token") },
                    modifier = Modifier.weight(1f).testTag(DeliveryTestTags.UPLOAD_TOKEN_FIELD),
                )
                TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(uiState.uploadToken)) },
                    enabled = uiState.uploadToken.isNotBlank(),
                    modifier = Modifier.testTag(DeliveryTestTags.COPY_TOKEN_BUTTON),
                ) {
                    Text("Скопировать")
                }
            }
            Text(
                text = "Токен выдаётся при создании устройства-шлюза в Viewer App (экран управления устройством).",
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "Повторные попытки доставки",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )

            OutlinedTextField(
                value = uiState.maxAttempts,
                onValueChange = actions::onMaxAttemptsChange,
                label = { Text("Максимум попыток (1–50)") },
                isError = uiState.maxAttemptsError != null,
                modifier = Modifier.fillMaxWidth().testTag(DeliveryTestTags.MAX_ATTEMPTS_FIELD),
            )
            uiState.maxAttemptsError?.let {
                Text(it, modifier = Modifier.testTag(DeliveryTestTags.MAX_ATTEMPTS_ERROR))
            }

            OutlinedTextField(
                value = uiState.baseIntervalSeconds,
                onValueChange = actions::onBaseIntervalSecondsChange,
                label = { Text("Интервал между попытками, сек (10–3600)") },
                isError = uiState.baseIntervalSecondsError != null,
                modifier = Modifier.fillMaxWidth().testTag(DeliveryTestTags.BASE_INTERVAL_FIELD),
            )
            uiState.baseIntervalSecondsError?.let {
                Text(it, modifier = Modifier.testTag(DeliveryTestTags.BASE_INTERVAL_ERROR))
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                SegmentedButton(
                    selected = uiState.backoffPolicy == BackoffPolicy.EXPONENTIAL,
                    onClick = { actions.onBackoffPolicyChange(BackoffPolicy.EXPONENTIAL) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag(DeliveryTestTags.BACKOFF_EXPONENTIAL),
                ) { Text("Экспоненциально") }
                SegmentedButton(
                    selected = uiState.backoffPolicy == BackoffPolicy.LINEAR,
                    onClick = { actions.onBackoffPolicyChange(BackoffPolicy.LINEAR) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag(DeliveryTestTags.BACKOFF_LINEAR),
                ) { Text("Фиксированно") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Пауза форвардинга", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = uiState.forwardingPaused,
                    onCheckedChange = actions::onForwardingPausedChange,
                    modifier = Modifier.testTag(DeliveryTestTags.FORWARDING_PAUSED_SWITCH),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Удалять после успешной пересылки", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = uiState.deleteAfterForward,
                    onCheckedChange = actions::onDeleteAfterForwardChange,
                    modifier = Modifier.testTag(DeliveryTestTags.DELETE_AFTER_FORWARD_SWITCH),
                )
            }
            Text(
                text = "Удаляет сообщение и из приложения, и из системного хранилища SMS сразу после успешной доставки.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Скрывать имя контакта в пересылке", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = uiState.hideContactNameInPayload,
                    onCheckedChange = actions::onHideContactNameInPayloadChange,
                    modifier = Modifier.testTag(DeliveryTestTags.HIDE_CONTACT_NAME_SWITCH),
                )
            }

            TextButton(
                onClick = actions::onTestConnection,
                enabled = uiState.canSave && !uiState.isTestingConnection,
                modifier = Modifier.padding(top = 12.dp).testTag(DeliveryTestTags.TEST_CONNECTION_BUTTON),
            ) {
                Text(if (uiState.isTestingConnection) "Проверка..." else "Проверить соединение")
            }
            when (val result = uiState.testConnectionResult) {
                is TestConnectionResult.Success -> Text(
                    text = "Успешно (HTTP ${result.httpCode})",
                    modifier = Modifier.testTag(DeliveryTestTags.TEST_CONNECTION_RESULT),
                )
                is TestConnectionResult.Failure -> Text(
                    text = "Ошибка: ${result.reason}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(DeliveryTestTags.TEST_CONNECTION_RESULT),
                )
                null -> Unit
            }

            Button(
                onClick = actions::onSave,
                enabled = uiState.canSave,
                modifier = Modifier.padding(top = 12.dp).testTag(DeliveryTestTags.SAVE_BUTTON),
            ) {
                Text("Сохранить")
            }
            if (uiState.isSaved) {
                Text("Сохранено", modifier = Modifier.testTag(DeliveryTestTags.SAVED_CONFIRMATION))
            }

            TextButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.padding(top = 24.dp).testTag(DeliveryTestTags.RESET_BUTTON),
            ) {
                Text("Сбросить настройки доставки", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "Сбросить настройки доставки?",
            text = "Адрес сервера, upload token, настройки повторных попыток и пауза форвардинга будут сброшены. Форвардинг перестанет работать, пока настройки не будут заданы заново. Правила фильтрации и история сообщений не затрагиваются.",
            confirmLabel = "Сбросить",
            onConfirm = {
                actions.onResetDeliverySettings()
                showResetConfirm = false
            },
            onDismiss = { showResetConfirm = false },
        )
    }
}
