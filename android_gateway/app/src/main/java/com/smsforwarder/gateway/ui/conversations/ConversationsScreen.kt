package com.smsforwarder.gateway.ui.conversations

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationsTestTags {
    const val LIST = "conversations_list"
    const val EMPTY_STATE = "conversations_empty_state"
    const val IMPORTING_INDICATOR = "conversations_importing_indicator"
    const val NEW_MESSAGE_FAB = "conversations_new_message_fab"
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("SMS Forwarder Gateway") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewMessageDialog = true },
                modifier = Modifier.testTag(ConversationsTestTags.NEW_MESSAGE_FAB),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новое сообщение")
            }
        },
    ) { padding ->
        ConversationsContent(
            conversations = uiState.conversations,
            isImporting = uiState.isImporting,
            onOpenThread = onOpenThread,
            modifier = Modifier.padding(padding),
        )
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
}

@Composable
fun ConversationsContent(
    conversations: List<ConversationUi>,
    isImporting: Boolean,
    onOpenThread: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (isImporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(ConversationsTestTags.IMPORTING_INDICATOR))
        }
        if (conversations.isEmpty() && !isImporting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Нет сообщений",
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
                    ConversationRow(conversation = conversation, onClick = { onOpenThread(conversation.sender) })
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationUi, onClick: () -> Unit) {
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
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = conversation.displayName, style = MaterialTheme.typography.titleMedium)
                Text(text = conversation.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Text(text = formatConversationTime(conversation.createdAt), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatConversationTime(timestampMillis: Long): String =
    SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(timestampMillis))
