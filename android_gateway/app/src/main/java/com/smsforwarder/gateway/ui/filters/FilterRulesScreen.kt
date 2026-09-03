package com.smsforwarder.gateway.ui.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import com.smsforwarder.gateway.data.local.db.FilterMode
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.ui.common.ConfirmDialog

object FilterRulesTestTags {
    const val TAB_RECEPTION = "filter_rules_tab_reception"
    const val TAB_FORWARDING = "filter_rules_tab_forwarding"
    const val MODE_BLACKLIST = "filter_rules_mode_blacklist"
    const val MODE_WHITELIST = "filter_rules_mode_whitelist"
    const val LIST = "filter_rules_list"
    const val EMPTY_STATE = "filter_rules_empty_state"
    const val ADD_FAB = "filter_rules_add_fab"
    const val UNAVAILABLE_SIM_BADGE = "filter_rules_unavailable_sim_badge"
    fun row(id: Long) = "filter_rules_row_$id"
    fun enabledSwitch(id: Long) = "filter_rules_enabled_switch_$id"
    fun moveUpButton(id: Long) = "filter_rules_move_up_$id"
    fun moveDownButton(id: Long) = "filter_rules_move_down_$id"
    fun deleteButton(id: Long) = "filter_rules_delete_$id"
}

@Composable
fun FilterRulesScreen(
    viewModel: FilterRulesViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onAddRule: (FilterStage) -> Unit,
    onEditRule: (Long, FilterStage) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    FilterRulesContent(uiState = uiState, actions = viewModel, onBack = onBack, onAddRule = onAddRule, onEditRule = onEditRule)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterRulesContent(
    uiState: FilterRulesUiState,
    actions: FilterRulesActions,
    onBack: () -> Unit,
    onAddRule: (FilterStage) -> Unit,
    onEditRule: (Long, FilterStage) -> Unit,
) {
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фильтрация SMS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddRule(uiState.selectedStage) },
                modifier = Modifier.testTag(FilterRulesTestTags.ADD_FAB),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить правило")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = if (uiState.selectedStage == FilterStage.RECEPTION) 0 else 1) {
                Tab(
                    selected = uiState.selectedStage == FilterStage.RECEPTION,
                    onClick = { actions.onTabSelected(FilterStage.RECEPTION) },
                    text = { Text("Приём") },
                    modifier = Modifier.testTag(FilterRulesTestTags.TAB_RECEPTION),
                )
                Tab(
                    selected = uiState.selectedStage == FilterStage.FORWARDING,
                    onClick = { actions.onTabSelected(FilterStage.FORWARDING) },
                    text = { Text("Форвардинг") },
                    modifier = Modifier.testTag(FilterRulesTestTags.TAB_FORWARDING),
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                SegmentedButton(
                    selected = uiState.mode == FilterMode.BLACKLIST,
                    onClick = { actions.onModeChange(FilterMode.BLACKLIST) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag(FilterRulesTestTags.MODE_BLACKLIST),
                ) { Text("Blacklist") }
                SegmentedButton(
                    selected = uiState.mode == FilterMode.WHITELIST,
                    onClick = { actions.onModeChange(FilterMode.WHITELIST) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag(FilterRulesTestTags.MODE_WHITELIST),
                ) { Text("Whitelist") }
            }

            if (uiState.rules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Нет правил",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(FilterRulesTestTags.EMPTY_STATE),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().testTag(FilterRulesTestTags.LIST),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    itemsIndexed(uiState.rules, key = { _, rule -> rule.id }) { index, rule ->
                        FilterRuleRow(
                            rule = rule,
                            isSimUnavailable = rule.subscriptionId != null && rule.subscriptionId !in uiState.activeSubscriptionIds,
                            canMoveUp = index > 0,
                            canMoveDown = index < uiState.rules.lastIndex,
                            onClick = { onEditRule(rule.id, rule.stage) },
                            onToggleEnabled = { actions.onToggleEnabled(rule) },
                            onDeleteRequested = { pendingDeleteId = rule.id },
                            onMoveUp = { actions.onMoveUp(rule) },
                            onMoveDown = { actions.onMoveDown(rule) },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        ConfirmDialog(
            title = "Удалить правило?",
            text = "Действие нельзя отменить.",
            onConfirm = {
                actions.onDeleteRule(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRuleRow(
    rule: FilterRuleEntity,
    isSimUnavailable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDeleteRequested: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        // Default (0.5) triggers on a fairly short drag - raised so an accidental
        // sideways scroll doesn't fire delete; the IconButton below is the
        // non-gesture equivalent (swipe-to-dismiss has no built-in a11y action).
        positionalThreshold = { totalDistance -> totalDistance * 0.75f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onDeleteRequested()
                else -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .testTag(FilterRulesTestTags.row(rule.id)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = ruleSummary(rule), style = MaterialTheme.typography.bodyMedium)
                    if (isSimUnavailable) {
                        Text(
                            text = "SIM недоступна",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(FilterRulesTestTags.UNAVAILABLE_SIM_BADGE),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.testTag(FilterRulesTestTags.moveUpButton(rule.id)),
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Переместить выше")
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.testTag(FilterRulesTestTags.moveDownButton(rule.id)),
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Переместить ниже")
                    }
                }
                IconButton(
                    onClick = onDeleteRequested,
                    modifier = Modifier.testTag(FilterRulesTestTags.deleteButton(rule.id)),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить правило", tint = MaterialTheme.colorScheme.error)
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.testTag(FilterRulesTestTags.enabledSwitch(rule.id)),
                )
            }
        }
    }
}

private fun ruleSummary(rule: FilterRuleEntity): String {
    val parts = buildList {
        rule.senderPattern?.takeIf { it.isNotEmpty() }?.let { add("Отправитель: $it${if (rule.senderIsRegex) " (regex)" else ""}") }
        rule.subscriptionId?.let { add("SIM: $it") }
        rule.contentPattern?.takeIf { it.isNotEmpty() }?.let { add("Текст: $it${if (rule.contentIsRegex) " (regex)" else ""}") }
    }
    return if (parts.isEmpty()) "Любое сообщение" else parts.joinToString(", ")
}
