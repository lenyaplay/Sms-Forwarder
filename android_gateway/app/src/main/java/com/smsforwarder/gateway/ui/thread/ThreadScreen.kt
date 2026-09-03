package com.smsforwarder.gateway.ui.thread

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.ui.common.ConfirmDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ThreadTestTags {
    const val LIST = "thread_list"
    const val DRAFT_FIELD = "thread_draft_field"
    const val SEND_BUTTON = "thread_send_button"
    const val SIM_SELECTOR = "thread_sim_selector"
    const val DELETE_CONVERSATION_BUTTON = "thread_delete_conversation_button"
    const val DELETE_MESSAGE_MENU_ITEM = "thread_delete_message_menu_item"
    fun retryButton(id: Long) = "thread_retry_button_$id"
    fun simMenuItem(subscriptionId: Int) = "thread_sim_menu_item_$subscriptionId"
    fun bubble(id: Long) = "thread_bubble_$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(viewModel: ThreadViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConversationConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteConversationConfirm = true },
                        modifier = Modifier.testTag(ThreadTestTags.DELETE_CONVERSATION_BUTTON),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить диалог")
                    }
                },
            )
        },
    ) { padding ->
        ThreadContent(uiState = uiState, actions = viewModel, modifier = Modifier.padding(padding))
    }

    if (showDeleteConversationConfirm) {
        ConfirmDialog(
            title = "Удалить диалог?",
            text = "Все сообщения с ${uiState.title} будут удалены безвозвратно.",
            onConfirm = {
                viewModel.onDeleteConversation()
                showDeleteConversationConfirm = false
                onBack()
            },
            onDismiss = { showDeleteConversationConfirm = false },
        )
    }
}

@Composable
fun ThreadContent(uiState: ThreadUiState, actions: ThreadActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isEmpty()) return@LaunchedEffect
        if (!initialScrollDone) {
            // First appearance of a (possibly large) history - jump straight to
            // the target with no visible scroll-through, not an animation.
            val targetIndex = uiState.scrollToMessageId
                ?.let { id -> uiState.messages.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?: uiState.messages.lastIndex
            listState.scrollToItem(targetIndex)
            initialScrollDone = true
        } else {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    // Memoized once per `actions` identity (stable - the ViewModel instance doesn't
    // change), not recreated per item per recomposition - passing a fresh closure to
    // MessageBubble on every keystroke/state update would defeat LazyColumn's ability
    // to skip recomposing bubbles whose own message data hasn't changed.
    val onRetry: (Long) -> Unit = remember(actions) { { id -> actions.onRetry(id) } }
    val onDeleteMessage: (Long) -> Unit = remember(actions) { { id -> actions.onDeleteMessage(id) } }

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
                MessageBubble(
                    message = message,
                    onRetry = onRetry,
                    onDelete = onDeleteMessage,
                )
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
                shape = MaterialTheme.shapes.small,
                // Replaces the old FilterChip row that used to sit above the draft
                // field (spec 0028 fixed its selected-chip fill color, but the product
                // owner then flagged, live on-device, that the row itself still read as
                // a solid dark bar over the last messages) - moving SIM selection into
                // the field's own trailing slot keeps it compact and inside the field's
                // bounds instead of a separate full-width element.
                trailingIcon = {
                    if (uiState.showSimSelector) {
                        var showSimMenu by remember { mutableStateOf(false) }
                        val selectedSim = uiState.availableSims.firstOrNull { it.subscriptionId == uiState.selectedSubscriptionId }
                        Box {
                            Column(
                                modifier = Modifier
                                    .clickable { showSimMenu = true }
                                    .padding(4.dp)
                                    .testTag(ThreadTestTags.SIM_SELECTOR),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Default.SimCard, contentDescription = "Выбор SIM")
                                selectedSim?.let {
                                    Text(
                                        text = "SIM ${it.slotIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            DropdownMenu(expanded = showSimMenu, onDismissRequest = { showSimMenu = false }) {
                                uiState.availableSims.forEach { sim ->
                                    DropdownMenuItem(
                                        text = { Text("SIM ${sim.slotIndex + 1} · ${sim.displayName}") },
                                        onClick = {
                                            actions.onSelectSim(sim.subscriptionId)
                                            showSimMenu = false
                                        },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = sim.subscriptionId == uiState.selectedSubscriptionId,
                                                onClick = null,
                                            )
                                        },
                                        modifier = Modifier.testTag(ThreadTestTags.simMenuItem(sim.subscriptionId)),
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).testTag(ThreadTestTags.DRAFT_FIELD),
            )
            if (uiState.isSending) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).heightIn(24.dp))
            } else {
                FilledIconButton(
                    onClick = actions::onSend,
                    enabled = uiState.canSend,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp)
                        .testTag(ThreadTestTags.SEND_BUTTON),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: MessageEntity, onRetry: (Long) -> Unit, onDelete: (Long) -> Unit) {
    val isOutgoing = message.direction == MessageDirection.OUT
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                )
                .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
                .padding(12.dp)
                .testTag(ThreadTestTags.bubble(message.id)),
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
                    onClick = { onRetry(message.id) },
                    modifier = Modifier.testTag(ThreadTestTags.retryButton(message.id)),
                ) {
                    Text("Повторить", color = MaterialTheme.colorScheme.error)
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    onClick = {
                        showMenu = false
                        showDeleteConfirm = true
                    },
                    modifier = Modifier.testTag(ThreadTestTags.DELETE_MESSAGE_MENU_ITEM),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Удалить сообщение?",
            text = "Сообщение будет удалено безвозвратно.",
            onConfirm = {
                onDelete(message.id)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

// Single shared formatter, not a fresh SimpleDateFormat per bubble per recomposition -
// safe because all calls happen on the main/Compose UI thread.
private val messageTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(timestampMillis: Long): String =
    messageTimeFormat.format(Date(timestampMillis))
