package com.smsforwarder.gateway.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ThreadTestTags {
    const val LIST = "thread_list"
    const val DRAFT_FIELD = "thread_draft_field"
    const val SEND_BUTTON = "thread_send_button"
    const val SIM_SELECTOR = "thread_sim_selector"
    fun retryButton(id: Long) = "thread_retry_button_$id"
    fun simChip(subscriptionId: Int) = "thread_sim_chip_$subscriptionId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(viewModel: ThreadViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        ThreadContent(uiState = uiState, actions = viewModel, modifier = Modifier.padding(padding))
    }
}

@Composable
fun ThreadContent(uiState: ThreadUiState, actions: ThreadActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(ThreadTestTags.LIST),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                MessageBubble(message = message, onRetry = { actions.onRetry(message.id) })
            }
        }
        if (uiState.showSimSelector) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag(ThreadTestTags.SIM_SELECTOR),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.availableSims.forEach { sim ->
                    FilterChip(
                        selected = sim.subscriptionId == uiState.selectedSubscriptionId,
                        onClick = { actions.onSelectSim(sim.subscriptionId) },
                        label = { Text(sim.displayName) },
                        modifier = Modifier.testTag(ThreadTestTags.simChip(sim.subscriptionId)),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.draft,
                onValueChange = actions::onDraftChange,
                enabled = !uiState.isSending,
                modifier = Modifier.weight(1f).testTag(ThreadTestTags.DRAFT_FIELD),
            )
            if (uiState.isSending) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).heightIn(24.dp))
            } else {
                androidx.compose.material3.Button(
                    onClick = actions::onSend,
                    enabled = uiState.canSend,
                    modifier = Modifier.testTag(ThreadTestTags.SEND_BUTTON),
                ) {
                    Text("Отправить")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, onRetry: () -> Unit) {
    val isOutgoing = message.direction == MessageDirection.OUT
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(10.dp),
        ) {
            Text(
                text = message.text,
                color = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (message.deliveryStatus == DeliveryStatus.FAILED) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag(ThreadTestTags.retryButton(message.id)),
                ) {
                    Text("Повторить", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun formatTime(timestampMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMillis))
