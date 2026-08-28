package com.smsforwarder.gateway.ui.thread

import com.smsforwarder.gateway.data.local.db.MessageEntity

data class ThreadUiState(
    val sender: String,
    val draft: String = "",
    val messages: List<MessageEntity> = emptyList(),
    val contactName: String? = null,
    val isSending: Boolean = false,
) {
    val title: String get() = contactName ?: sender
    val canSend: Boolean get() = draft.isNotBlank() && !isSending
}
