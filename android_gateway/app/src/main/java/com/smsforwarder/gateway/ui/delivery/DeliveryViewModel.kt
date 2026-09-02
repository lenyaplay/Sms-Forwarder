package com.smsforwarder.gateway.ui.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.remote.WebhookConnectionTester
import com.smsforwarder.gateway.data.remote.WebhookUrlBuilder
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
    fun onForwardingPausedChange(value: Boolean)
    fun onDeleteAfterForwardChange(value: Boolean)
    fun onHideContactNameInPayloadChange(value: Boolean)
    fun onSave()
    fun onTestConnection()
    fun onResetDeliverySettings()
}

@HiltViewModel
class DeliveryViewModel @Inject constructor(
    private val configStore: GatewayConfigStore,
    private val messageRepository: MessageRepository,
    private val connectionTester: WebhookConnectionTester,
) : ViewModel(), DeliveryActions {

    private val _uiState = MutableStateFlow(stateFromStore())
    val uiState: StateFlow<DeliveryUiState> = _uiState.asStateFlow()

    private fun stateFromStore() = DeliveryUiState(
        serverUrl = configStore.getServerUrl().orEmpty(),
        uploadToken = configStore.getUploadToken().orEmpty(),
        maxAttempts = configStore.retryMaxAttempts().toString(),
        baseIntervalSeconds = configStore.retryBaseIntervalSeconds().toString(),
        backoffPolicy = configStore.retryBackoffPolicy(),
        forwardingPaused = configStore.isForwardingPaused(),
        deleteAfterForward = configStore.deleteAfterForward(),
        hideContactNameInPayload = configStore.hideContactNameInPayload(),
    )

    override fun onServerUrlChange(value: String) {
        _uiState.update { it.copy(serverUrl = value, isSaved = false, testConnectionResult = null) }
    }

    override fun onUploadTokenChange(value: String) {
        _uiState.update { it.copy(uploadToken = value, isSaved = false, testConnectionResult = null) }
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

    override fun onForwardingPausedChange(value: Boolean) {
        _uiState.update { it.copy(forwardingPaused = value, isSaved = false) }
    }

    override fun onDeleteAfterForwardChange(value: Boolean) {
        _uiState.update { it.copy(deleteAfterForward = value, isSaved = false) }
    }

    override fun onHideContactNameInPayloadChange(value: Boolean) {
        _uiState.update { it.copy(hideContactNameInPayload = value, isSaved = false) }
    }

    override fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            configStore.save(state.serverUrl, state.uploadToken)
            configStore.setRetryMaxAttempts(state.maxAttempts.toInt())
            configStore.setRetryBaseIntervalSeconds(state.baseIntervalSeconds.toLong())
            configStore.setRetryBackoffPolicy(state.backoffPolicy)
            configStore.setForwardingPaused(state.forwardingPaused)
            configStore.setDeleteAfterForward(state.deleteAfterForward)
            configStore.setHideContactNameInPayload(state.hideContactNameInPayload)
            // No-op while still paused - enqueueDelivery withholds the WorkManager
            // job itself (MessageRepository), so this is safe to always call.
            messageRepository.retryUndeliveredMessages()
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    override fun onResetDeliverySettings() {
        configStore.resetDeliverySettings()
        _uiState.update { stateFromStore() }
    }

    override fun onTestConnection() {
        val state = _uiState.value
        if (!state.canSave) return
        val webhookUrl = WebhookUrlBuilder.build(state.serverUrl, state.uploadToken)
        _uiState.update { it.copy(isTestingConnection = true, testConnectionResult = null) }
        viewModelScope.launch {
            val result = connectionTester.test(webhookUrl)
            _uiState.update { it.copy(isTestingConnection = false, testConnectionResult = result) }
        }
    }
}
