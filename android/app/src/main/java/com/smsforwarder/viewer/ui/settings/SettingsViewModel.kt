package com.smsforwarder.viewer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.viewer.data.local.ServerConfigStore
import com.smsforwarder.viewer.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val loggedOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverConfigStore: ServerConfigStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(serverUrl = serverConfigStore.getUrl().orEmpty()))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Used both for a plain logout and as the first step of changing the
     * server URL (spec 0010 assumption 2 - a URL change requires re-login,
     * no live network-stack rebuild). The caller (SettingsScreen) decides
     * where uiState.loggedOut navigates to next.
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = _uiState.value.copy(loggedOut = true)
        }
    }
}
