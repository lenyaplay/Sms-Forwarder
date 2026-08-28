package com.smsforwarder.gateway.ui.thread

import com.smsforwarder.gateway.data.local.db.MessageEntity

data class ThreadUiState(
    val sender: String,
    val draft: String = "",
    val messages: List<MessageEntity> = emptyList(),
) {
    val canSend: Boolean get() = draft.isNotBlank()
}
