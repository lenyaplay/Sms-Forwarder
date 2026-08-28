package com.smsforwarder.gateway.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsforwarder.gateway.data.local.db.ConversationEntity

object ConversationsTestTags {
    const val LIST = "conversations_list"
    fun row(sender: String) = "conversations_row_$sender"
}

@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel = hiltViewModel(),
    onOpenThread: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    ConversationsContent(conversations = uiState.conversations, onOpenThread = onOpenThread)
}

@Composable
fun ConversationsContent(conversations: List<ConversationEntity>, onOpenThread: (String) -> Unit) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .testTag(ConversationsTestTags.LIST),
        ) {
            items(conversations, key = { it.sender }) { conversation ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable { onOpenThread(conversation.sender) }
                        .testTag(ConversationsTestTags.row(conversation.sender)),
                ) {
                    Text(text = conversation.sender)
                    Text(text = conversation.text)
                }
            }
        }
    }
}
