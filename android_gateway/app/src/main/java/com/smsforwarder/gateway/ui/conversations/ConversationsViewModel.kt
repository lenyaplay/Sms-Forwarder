package com.smsforwarder.gateway.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.gateway.data.local.ContactNameResolver
import com.smsforwarder.gateway.data.local.SmsHistoryImporter
import com.smsforwarder.gateway.data.local.db.ConversationEntity
import com.smsforwarder.gateway.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ConversationUi(
    val sender: String,
    val displayName: String,
    val text: String,
    val createdAt: Long,
)

data class ConversationsUiState(
    val conversations: List<ConversationUi> = emptyList(),
    val isImporting: Boolean = false,
)

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val repository: MessageRepository,
    private val contactNameResolver: ContactNameResolver,
    historyImporter: SmsHistoryImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeConversations()
                .combine(historyImporter.isImporting) { conversations, isImporting -> conversations to isImporting }
                .collect { (conversations, isImporting) ->
                    val conversationsUi = withContext(Dispatchers.IO) {
                        conversations.map { entity ->
                            ConversationUi(
                                sender = entity.sender,
                                displayName = contactNameResolver.displayNameFor(entity.sender) ?: entity.sender,
                                text = entity.text,
                                createdAt = entity.createdAt,
                            )
                        }
                    }
                    _uiState.value = ConversationsUiState(conversationsUi, isImporting)
                }
        }
    }
}
