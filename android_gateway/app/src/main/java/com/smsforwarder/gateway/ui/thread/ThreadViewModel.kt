package com.smsforwarder.gateway.ui.thread

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.gateway.data.local.ContactNameResolver
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
}

@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val contactNameResolver: ContactNameResolver,
) : ViewModel(), ThreadActions {

    private val sender: String = checkNotNull(savedStateHandle["sender"])

    private val _uiState = MutableStateFlow(ThreadUiState(sender = sender))
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
    }

    override fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    override fun onSend() {
        val state = _uiState.value
        if (!state.canSend) return
        val text = state.draft
        _uiState.update { it.copy(draft = "", isSending = true) }
        viewModelScope.launch {
            try {
                repository.sendMessage(sender, text)
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
}
