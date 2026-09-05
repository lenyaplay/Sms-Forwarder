package com.smsforwarder.gateway.ui.thread

import com.smsforwarder.gateway.data.local.SimOption
import com.smsforwarder.gateway.data.local.db.MessageEntity

data class ThreadUiState(
    val sender: String,
    val draft: String = "",
    val messages: List<MessageEntity> = emptyList(),
    val contactName: String? = null,
    val isSending: Boolean = false,
    val availableSims: List<SimOption> = emptyList(),
    val selectedSubscriptionId: Int? = null,
    val scrollToMessageId: Long? = null,
    val selectedMessageIds: Set<Long> = emptySet(),
) {
    val title: String get() = contactName ?: sender
    val canSend: Boolean get() = draft.isNotBlank() && !isSending
    val showSimSelector: Boolean get() = availableSims.size > 1
    val isSelectionMode: Boolean get() = selectedMessageIds.isNotEmpty()
}
