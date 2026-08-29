package com.smsforwarder.gateway.ui.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.smsforwarder.gateway.data.local.SimOption

object FilterRuleEditTestTags {
    const val SENDER_FIELD = "filter_rule_edit_sender_field"
    const val SENDER_REGEX_SWITCH = "filter_rule_edit_sender_regex_switch"
    const val SENDER_ERROR = "filter_rule_edit_sender_error"
    const val CONTENT_FIELD = "filter_rule_edit_content_field"
    const val CONTENT_REGEX_SWITCH = "filter_rule_edit_content_regex_switch"
    const val CONTENT_ERROR = "filter_rule_edit_content_error"
    const val SIM_DROPDOWN = "filter_rule_edit_sim_dropdown"
    const val ENABLED_SWITCH = "filter_rule_edit_enabled_switch"
    const val SAVE_BUTTON = "filter_rule_edit_save_button"
}

@Composable
fun FilterRuleEditScreen(
    viewModel: FilterRuleEditViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onBack()
    }

    FilterRuleEditContent(uiState = uiState, actions = viewModel, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterRuleEditContent(uiState: FilterRuleEditUiState, actions: FilterRuleEditActions, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Правило фильтра") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = uiState.senderPattern,
                onValueChange = actions::onSenderPatternChange,
                label = { Text("Отправитель (пусто = любой)") },
                isError = uiState.senderPatternError != null,
                modifier = Modifier.fillMaxWidth().testTag(FilterRuleEditTestTags.SENDER_FIELD),
            )
            uiState.senderPatternError?.let {
                Text(it, modifier = Modifier.testTag(FilterRuleEditTestTags.SENDER_ERROR))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Regex")
                Switch(
                    checked = uiState.senderIsRegex,
                    onCheckedChange = actions::onSenderIsRegexChange,
                    modifier = Modifier.testTag(FilterRuleEditTestTags.SENDER_REGEX_SWITCH),
                )
            }

            SimPicker(
                availableSims = uiState.availableSims,
                selectedSubscriptionId = uiState.subscriptionId,
                onSelected = actions::onSubscriptionIdChange,
            )

            OutlinedTextField(
                value = uiState.contentPattern,
                onValueChange = actions::onContentPatternChange,
                label = { Text("Текст сообщения (пусто = любой)") },
                isError = uiState.contentPatternError != null,
                modifier = Modifier.fillMaxWidth().testTag(FilterRuleEditTestTags.CONTENT_FIELD),
            )
            uiState.contentPatternError?.let {
                Text(it, modifier = Modifier.testTag(FilterRuleEditTestTags.CONTENT_ERROR))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Regex")
                Switch(
                    checked = uiState.contentIsRegex,
                    onCheckedChange = actions::onContentIsRegexChange,
                    modifier = Modifier.testTag(FilterRuleEditTestTags.CONTENT_REGEX_SWITCH),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Включено")
                Switch(
                    checked = uiState.enabled,
                    onCheckedChange = actions::onEnabledChange,
                    modifier = Modifier.testTag(FilterRuleEditTestTags.ENABLED_SWITCH),
                )
            }

            Button(
                onClick = actions::onSave,
                enabled = uiState.canSave,
                modifier = Modifier.testTag(FilterRuleEditTestTags.SAVE_BUTTON),
            ) {
                Text("Сохранить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimPicker(
    availableSims: List<SimOption>,
    selectedSubscriptionId: Int?,
    onSelected: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = availableSims.find { it.subscriptionId == selectedSubscriptionId }?.displayName ?: "Любая SIM"

    Column(modifier = Modifier.testTag(FilterRuleEditTestTags.SIM_DROPDOWN)) {
        TextButton(onClick = { expanded = true }) { Text(selectedLabel) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Любая SIM") },
                onClick = { onSelected(null); expanded = false },
            )
            availableSims.forEach { sim ->
                DropdownMenuItem(
                    text = { Text(sim.displayName) },
                    onClick = { onSelected(sim.subscriptionId); expanded = false },
                )
            }
        }
    }
}
