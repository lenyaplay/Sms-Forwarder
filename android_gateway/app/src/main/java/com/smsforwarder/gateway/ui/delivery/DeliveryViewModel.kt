package com.smsforwarder.gateway.ui.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

interface DeliveryActions {
    fun onServerUrlChange(value: String)
    fun onUploadTokenChange(value: String)
    fun onMaxAttemptsChange(value: String)
    fun onBaseIntervalSecondsChange(value: String)
    fun onBackoffPolicyChange(value: BackoffPolicy)
    fun onSave()
}

@HiltViewModel
class DeliveryViewModel @Inject constructor(
    private val configStore: GatewayConfigStore,
    private val messageRepository: MessageRepository,
) : ViewModel(), DeliveryActions {

    private val _uiState = MutableStateFlow(
        DeliveryUiState(
            serverUrl = configStore.getServerUrl().orEmpty(),
            uploadToken = configStore.getUploadToken().orEmpty(),
            maxAttempts = configStore.retryMaxAttempts().toString(),
            baseIntervalSeconds = configStore.retryBaseIntervalSeconds().toString(),
            backoffPolicy = configStore.retryBackoffPolicy(),
        )
    )
    val uiState: StateFlow<DeliveryUiState> = _uiState.asStateFlow()

    override fun onServerUrlChange(value: String) {
        _uiState.update { it.copy(serverUrl = value, isSaved = false) }
    }

    override fun onUploadTokenChange(value: String) {
        _uiState.update { it.copy(uploadToken = value, isSaved = false) }
    }

    override fun onMaxAttemptsChange(value: String) {
        _uiState.update { it.copy(maxAttempts = value, isSaved = false) }
    }

    override fun onBaseIntervalSecondsChange(value: String) {
        _uiState.update { it.copy(baseIntervalSeconds = value, isSaved = false) }
    }

    override fun onBackoffPolicyChange(value: BackoffPolicy) {
        _uiState.update { it.copy(backoffPolicy = value, isSaved = false) }
    }

    override fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            configStore.save(state.serverUrl, state.uploadToken)
            configStore.setRetryMaxAttempts(state.maxAttempts.toInt())
            configStore.setRetryBaseIntervalSeconds(state.baseIntervalSeconds.toLong())
            configStore.setRetryBackoffPolicy(state.backoffPolicy)
            messageRepository.retryUndeliveredMessages()
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
