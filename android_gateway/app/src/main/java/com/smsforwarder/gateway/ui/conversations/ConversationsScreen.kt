package com.smsforwarder.gateway.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.ui.common.ConfirmDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationsTestTags {
    const val LIST = "conversations_list"
    const val EMPTY_STATE = "conversations_empty_state"
    const val IMPORTING_INDICATOR = "conversations_importing_indicator"
    const val NEW_MESSAGE_FAB = "conversations_new_message_fab"
    const val SEARCH_FIELD = "conversations_search_field"
    const val ARCHIVE_TOGGLE = "conversations_archive_toggle"
    const val RESEND_ALL_FAILED = "conversations_resend_all_failed"
    const val SEARCH_RESULTS_LIST = "conversations_search_results_list"
    fun row(sender: String) = "conversations_row_$sender"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel = hiltViewModel(),
    onOpenThread: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNewMessageDialog by remember { mutableStateOf(false) }
    var pendingDeleteSender by remember { mutableStateOf<String?>(null) }
    var showResendAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isArchivedView) "Архив" else "SMS Forwarder Gateway") },
                actions = {
                    if (uiState.failedCount > 0) {
                        IconButton(
                            onClick = { showResendAllConfirm = true },
                            modifier = Modifier.testTag(ConversationsTestTags.RESEND_ALL_FAILED),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Повторить неудавшиеся")
                        }
                    }
                    IconButton(
                        onClick = viewModel::onToggleArchivedView,
                        modifier = Modifier.testTag(ConversationsTestTags.ARCHIVE_TOGGLE),
                    ) {
                        Icon(
                            if (uiState.isArchivedView) Icons.Default.Inbox else Icons.Default.Archive,
                            contentDescription = if (uiState.isArchivedView) "Показать активные" else "Показать архив",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewMessageDialog = true },
                modifier = Modifier.testTag(ConversationsTestTags.NEW_MESSAGE_FAB),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новое сообщение")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Поиск по переписке") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag(ConversationsTestTags.SEARCH_FIELD),
            )
            if (uiState.isSearching) {
                SearchResultsList(results = uiState.searchResults, onOpenThread = onOpenThread)
            } else {
                ConversationsContent(
                    conversations = uiState.conversations,
                    isImporting = uiState.isImporting,
                    isArchivedView = uiState.isArchivedView,
                    onOpenThread = onOpenThread,
                    onArchiveToggle = { sender -> viewModel.onArchiveToggle(sender, uiState.isArchivedView) },
                    onDeleteRequested = { sender -> pendingDeleteSender = sender },
                )
            }
        }
    }

    if (showNewMessageDialog) {
        NewMessageDialog(
            onDismiss = { showNewMessageDialog = false },
            onConfirm = { number ->
                showNewMessageDialog = false
                onOpenThread(number)
            },
        )
    }

    pendingDeleteSender?.let { sender ->
        ConfirmDialog(
            title = "Удалить диалог?",
            text = "Все сообщения с $sender будут удалены безвозвратно.",
            onConfirm = {
                viewModel.onDeleteConversation(sender)
                pendingDeleteSender = null
            },
            onDismiss = { pendingDeleteSender = null },
        )
    }

    if (showResendAllConfirm) {
        ConfirmDialog(
            title = "Повторить неудавшиеся?",
            text = "Будет предпринята повторная попытка отправки ${uiState.failedCount} сообщений.",
            confirmLabel = "Повторить",
            onConfirm = {
                viewModel.onResendAllFailed()
                showResendAllConfirm = false
            },
            onDismiss = { showResendAllConfirm = false },
        )
    }
}

@Composable
private fun SearchResultsList(results: List<MessageEntity>, onOpenThread: (String) -> Unit) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ничего не найдено", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag(ConversationsTestTags.SEARCH_RESULTS_LIST),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(results, key = { it.id }) { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onOpenThread(message.sender) },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = message.sender, style = MaterialTheme.typography.titleMedium)
                    Text(text = message.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun ConversationsContent(
    conversations: List<ConversationUi>,
    isImporting: Boolean,
    isArchivedView: Boolean = false,
    onOpenThread: (String) -> Unit,
    onArchiveToggle: (String) -> Unit = {},
    onDeleteRequested: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (isImporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(ConversationsTestTags.IMPORTING_INDICATOR))
        }
        if (conversations.isEmpty() && !isImporting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isArchivedView) "Архив пуст" else "Нет сообщений",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(ConversationsTestTags.EMPTY_STATE),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ConversationsTestTags.LIST),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(conversations, key = { it.sender }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        isArchivedView = isArchivedView,
                        onClick = { onOpenThread(conversation.sender) },
                        onArchiveToggle = { onArchiveToggle(conversation.sender) },
                        onDeleteRequested = { onDeleteRequested(conversation.sender) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationRow(
    conversation: ConversationUi,
    isArchivedView: Boolean,
    onClick: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDeleteRequested: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onArchiveToggle()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteRequested()
                    false
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val (icon, color) = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> (if (isArchivedView) Icons.Default.Inbox else Icons.Default.Archive) to MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete to MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.Settled -> null to MaterialTheme.colorScheme.surface
            }
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                icon?.let { Icon(it, contentDescription = null) }
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .testTag(ConversationsTestTags.row(conversation.sender)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = conversation.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(text = conversation.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
                Text(text = formatConversationTime(conversation.createdAt), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatConversationTime(timestampMillis: Long): String =
    SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(timestampMillis))
