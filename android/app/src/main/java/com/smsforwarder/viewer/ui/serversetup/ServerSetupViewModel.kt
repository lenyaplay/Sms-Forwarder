package com.smsforwarder.viewer.ui.serversetup

import android.util.Log
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
        Log.d(TAG, "save() called with entered url=\"$url\"")
        if (!isValidUrl(url)) {
            Log.w(TAG, "rejected \"$url\": not a valid http(s) URL")
            _uiState.value = _uiState.value.copy(errorMessage = "Enter a valid server URL (http:// or https://)")
            return
        }
        serverConfigStore.save(url)
        Log.d(TAG, "saved url=\"$url\" (readback: \"${serverConfigStore.getUrl()}\")")
        _uiState.value = _uiState.value.copy(saved = true, errorMessage = null)
    }

    private companion object {
        const val TAG = "ServerSetupDebug"
    }

    private fun isValidUrl(url: String): Boolean =
        url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))
}
