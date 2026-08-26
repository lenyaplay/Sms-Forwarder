package com.smsforwarder.viewer.ui.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.viewer.data.remote.dto.MessageDto
import com.smsforwarder.viewer.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageFeedUiState(
    val isLoadingInitial: Boolean = false,
    val isLoadingMore: Boolean = false,
    val messages: List<MessageDto> = emptyList(),
    val nextBeforeId: Long? = null,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class MessageFeedViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deviceId: Long = checkNotNull(savedStateHandle["deviceId"])

    private val _uiState = MutableStateFlow(MessageFeedUiState())
    val uiState: StateFlow<MessageFeedUiState> = _uiState.asStateFlow()

    private var liveUpdatesJob: Job? = null

    init {
        loadInitialPage()
        startLiveUpdates()
    }

    fun loadInitialPage() {
        _uiState.value = _uiState.value.copy(isLoadingInitial = true, errorMessage = null)
        viewModelScope.launch {
            messageRepository.fetchPage(deviceId)
                .onSuccess { page ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingInitial = false,
                        messages = page.messages,
                        nextBeforeId = page.nextBeforeId,
                        hasMore = page.nextBeforeId != null,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingInitial = false,
                        errorMessage = error.message ?: "Failed to load messages",
                    )
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val beforeId = state.nextBeforeId ?: return
        if (state.isLoadingMore) return

        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            messageRepository.fetchPage(deviceId, beforeId = beforeId)
                .onSuccess { page ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        messages = _uiState.value.messages + page.messages,
                        nextBeforeId = page.nextBeforeId,
                        hasMore = page.nextBeforeId != null,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        errorMessage = error.message ?: "Failed to load more messages",
                    )
                }
        }
    }

    /** Foreground-only, per spec 0007 assumption 7 - stopped in onCleared(). */
    private fun startLiveUpdates() {
        liveUpdatesJob = viewModelScope.launch {
            messageRepository.observeLive(deviceId).collect { newMessage ->
                val current = _uiState.value.messages
                if (current.none { it.id == newMessage.id }) {
                    _uiState.value = _uiState.value.copy(messages = listOf(newMessage) + current)
                }
            }
        }
    }

    fun stopLiveUpdates() {
        liveUpdatesJob?.cancel()
        liveUpdatesJob = null
    }

    fun resumeLiveUpdates() {
        if (liveUpdatesJob == null) {
            startLiveUpdates()
            loadInitialPage()
        }
    }

    override fun onCleared() {
        stopLiveUpdates()
        super.onCleared()
    }
}
