package com.smsforwarder.gateway.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

interface SettingsActions {
    fun onServerUrlChange(value: String)
    fun onUploadTokenChange(value: String)
    fun onSave()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configStore: GatewayConfigStore,
) : ViewModel(), SettingsActions {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            serverUrl = configStore.getServerUrl().orEmpty(),
            uploadToken = configStore.getUploadToken().orEmpty(),
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    override fun onServerUrlChange(value: String) {
        _uiState.update { it.copy(serverUrl = value, isSaved = false) }
    }

    override fun onUploadTokenChange(value: String) {
        _uiState.update { it.copy(uploadToken = value, isSaved = false) }
    }

    override fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            configStore.save(state.serverUrl, state.uploadToken)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
