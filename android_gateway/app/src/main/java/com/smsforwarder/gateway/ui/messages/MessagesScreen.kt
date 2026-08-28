package com.smsforwarder.gateway.ui.messages

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
import com.smsforwarder.gateway.data.local.db.MessageEntity

object MessagesTestTags {
    const val LIST = "messages_list"
    fun row(id: Long) = "messages_row_$id"
}

@Composable
fun MessagesScreen(viewModel: MessagesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    MessagesContent(messages = uiState.messages)
}

@Composable
fun MessagesContent(messages: List<MessageEntity>) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .testTag(MessagesTestTags.LIST),
        ) {
            items(messages, key = { it.id }) { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .testTag(MessagesTestTags.row(message.id)),
                ) {
                    Text(text = message.sender)
                    Text(text = message.text)
                    Text(text = message.deliveryStatus.name)
                }
            }
        }
    }
}
