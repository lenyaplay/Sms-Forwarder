package com.smsforwarder.viewer.ui.serversetup

import androidx.lifecycle.ViewModel
import com.smsforwarder.viewer.data.local.ServerConfigStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ServerSetupUiState(
    val url: String = "",
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    private val serverConfigStore: ServerConfigStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerSetupUiState(url = serverConfigStore.getUrl().orEmpty()))
    val uiState: StateFlow<ServerSetupUiState> = _uiState.asStateFlow()

    fun onUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(url = value, errorMessage = null)
    }

    fun save() {
        val url = _uiState.value.url.trim()
        if (!isValidUrl(url)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter a valid server URL (http:// or https://)")
            return
        }
        serverConfigStore.save(url)
        _uiState.value = _uiState.value.copy(saved = true, errorMessage = null)
    }

    private fun isValidUrl(url: String): Boolean =
        url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))
}
