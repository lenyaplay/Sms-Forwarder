package com.smsforwarder.gateway.ui.thread

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.gateway.data.local.ContactNameResolver
import com.smsforwarder.gateway.data.local.SimOptionsProvider
import com.smsforwarder.gateway.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface ThreadActions {
    fun onDraftChange(value: String)
    fun onSend()
    fun onRetry(messageId: Long)
    fun onSelectSim(subscriptionId: Int)
    fun onDeleteMessage(messageId: Long)
    fun onDeleteConversation()
}

@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val contactNameResolver: ContactNameResolver,
    private val simOptionsProvider: SimOptionsProvider,
) : ViewModel(), ThreadActions {

    private val sender: String = checkNotNull(savedStateHandle["sender"])

    // Nav route defaults messageId to 0L when absent (NavType.LongType has no
    // nullable variant) - 0L is never a real MessageEntity.id (Room autoGenerate
    // starts at 1), so it unambiguously means "no target message".
    private val scrollToMessageId: Long? = (savedStateHandle.get<Long>("messageId") ?: 0L).takeIf { it != 0L }

    private val _uiState = MutableStateFlow(ThreadUiState(sender = sender, scrollToMessageId = scrollToMessageId))
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeThread(sender).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { contactNameResolver.displayNameFor(sender) }
            _uiState.update { it.copy(contactName = name) }
        }
        viewModelScope.launch {
            val sims = withContext(Dispatchers.IO) { simOptionsProvider.activeSims() }
            _uiState.update { it.copy(availableSims = sims, selectedSubscriptionId = sims.firstOrNull()?.subscriptionId) }
        }
    }

    override fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    override fun onSelectSim(subscriptionId: Int) {
        _uiState.update { it.copy(selectedSubscriptionId = subscriptionId) }
    }

    override fun onSend() {
        val state = _uiState.value
        if (!state.canSend) return
        val text = state.draft
        val selectedSim = state.availableSims.find { it.subscriptionId == state.selectedSubscriptionId }
        _uiState.update { it.copy(draft = "", isSending = true) }
        viewModelScope.launch {
            try {
                repository.sendMessage(sender, text, selectedSim?.subscriptionId, selectedSim?.slotIndex)
            } catch (e: Exception) {
                // SmsManager can throw for reasons outside our control (no SIM,
                // airplane mode, malformed number) - never let that crash the
                // app; restore the draft so the user doesn't lose their text.
                Log.e("ThreadViewModel", "failed to send SMS to $sender", e)
                _uiState.update { it.copy(draft = text) }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    override fun onRetry(messageId: Long) {
        viewModelScope.launch { repository.retryMessage(messageId) }
    }

    override fun onDeleteMessage(messageId: Long) {
        viewModelScope.launch { repository.deleteMessage(messageId) }
    }

    override fun onDeleteConversation() {
        viewModelScope.launch { repository.deleteConversation(sender) }
    }
}
