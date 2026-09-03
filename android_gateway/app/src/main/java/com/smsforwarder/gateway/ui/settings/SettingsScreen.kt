package com.smsforwarder.gateway.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsforwarder.gateway.ui.common.ConfirmDialog

object SettingsTestTags {
    const val OPEN_DELIVERY_BUTTON = "settings_open_delivery_button"
    const val OPEN_FILTER_RULES_BUTTON = "settings_open_filter_rules_button"
    const val OPEN_DELIVERY_LOG_BUTTON = "settings_open_delivery_log_button"
    const val EXPORT_BUTTON = "settings_export_button"
    const val IMPORT_BUTTON = "settings_import_button"
    const val MESSAGE = "settings_message"
    const val DIAGNOSTICS_SWITCH = "settings_diagnostics_switch"
}

private const val EXPORT_FILE_NAME = "sms-forwarder-gateway-settings.json"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenDelivery: () -> Unit = {},
    onOpenFilterRules: () -> Unit = {},
    onOpenDeliveryLog: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsContent(
        uiState = uiState,
        actions = viewModel,
        onBack = onBack,
        onOpenDelivery = onOpenDelivery,
        onOpenFilterRules = onOpenFilterRules,
        onOpenDeliveryLog = onOpenDeliveryLog,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState = SettingsUiState(),
    actions: SettingsActions = object : SettingsActions {
        override fun onExportConfirmed(uri: android.net.Uri) {}
        override fun onImportConfirmed(uri: android.net.Uri) {}
        override fun onMessageDismissed() {}
        override fun onDiagnosticsEnabledChange(value: Boolean) {}
    },
    onBack: () -> Unit = {},
    onOpenDelivery: () -> Unit = {},
    onOpenFilterRules: () -> Unit = {},
    onOpenDeliveryLog: () -> Unit = {},
) {
    var showExportWarning by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.onExportConfirmed(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.onImportConfirmed(uri)
    }

    Scaffold(
        // Spec 0027: Settings is now pushed onto the back stack from a TopAppBar
        // icon in ConversationsScreen (bottom nav bar removed) - needs its own way
        // back, unlike before when it was a top-level tab switched via bottom nav.
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = onOpenDelivery,
                        modifier = Modifier.testTag(SettingsTestTags.OPEN_DELIVERY_BUTTON),
                    ) {
                        Text("Доставка")
                    }
                    TextButton(
                        onClick = onOpenFilterRules,
                        modifier = Modifier.testTag(SettingsTestTags.OPEN_FILTER_RULES_BUTTON),
                    ) {
                        Text("Фильтрация SMS")
                    }
                    TextButton(
                        onClick = onOpenDeliveryLog,
                        modifier = Modifier.testTag(SettingsTestTags.OPEN_DELIVERY_LOG_BUTTON),
                    ) {
                        Text("Лог доставки")
                    }
                    TextButton(
                        onClick = { showExportWarning = true },
                        modifier = Modifier.testTag(SettingsTestTags.EXPORT_BUTTON),
                    ) {
                        Text("Экспортировать настройки")
                    }
                    TextButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.testTag(SettingsTestTags.IMPORT_BUTTON),
                    ) {
                        Text("Импортировать настройки")
                    }
                    uiState.message?.let { message ->
                        Text(
                            text = message,
                            color = if (uiState.isMessageError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag(SettingsTestTags.MESSAGE),
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Диагностика", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Замер производительности экранов и фоновых операций - пишет в logcat и в файл на устройстве",
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = uiState.isDiagnosticsEnabled,
                            onCheckedChange = { actions.onDiagnosticsEnabledChange(it) },
                            modifier = Modifier.testTag(SettingsTestTags.DIAGNOSTICS_SWITCH),
                        )
                    }
                }
            }
        }
    }

    if (showExportWarning) {
        ConfirmDialog(
            title = "Экспортировать настройки?",
            text = "Файл содержит upload token в открытом виде — храните его так же бережно, как пароль.",
            confirmLabel = "Экспортировать",
            onConfirm = {
                showExportWarning = false
                exportLauncher.launch(EXPORT_FILE_NAME)
            },
            onDismiss = { showExportWarning = false },
        )
    }
}
