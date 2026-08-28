package com.smsforwarder.gateway.ui.thread

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity

object ThreadTestTags {
    const val LIST = "thread_list"
    const val DRAFT_FIELD = "thread_draft_field"
    const val SEND_BUTTON = "thread_send_button"
    fun retryButton(id: Long) = "thread_retry_button_$id"
}

@Composable
fun ThreadScreen(viewModel: ThreadViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    ThreadContent(uiState = uiState, actions = viewModel)
}

@Composable
fun ThreadContent(uiState: ThreadUiState, actions: ThreadActions) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(ThreadTestTags.LIST),
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageRow(message = message, onRetry = { actions.onRetry(message.id) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                OutlinedTextField(
                    value = uiState.draft,
                    onValueChange = actions::onDraftChange,
                    modifier = Modifier.weight(1f).testTag(ThreadTestTags.DRAFT_FIELD),
                )
                Button(
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
private fun MessageRow(message: MessageEntity, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(text = if (message.direction == MessageDirection.OUT) "Вы: ${message.text}" else message.text)
        if (message.deliveryStatus == DeliveryStatus.FAILED) {
            TextButton(onClick = onRetry, modifier = Modifier.testTag(ThreadTestTags.retryButton(message.id))) {
                Text("Повторить")
            }
        }
    }
}
