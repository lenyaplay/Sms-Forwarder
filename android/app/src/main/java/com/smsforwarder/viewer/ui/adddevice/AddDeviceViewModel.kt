package com.smsforwarder.viewer.ui.adddevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.viewer.data.repository.AddDeviceResult
import com.smsforwarder.viewer.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddDeviceUiState(
    val token: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val addedDeviceName: String? = null,
)

@HiltViewModel
class AddDeviceViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddDeviceUiState())
    val uiState: StateFlow<AddDeviceUiState> = _uiState.asStateFlow()

    fun onTokenChange(value: String) {
        _uiState.value = _uiState.value.copy(token = value, errorMessage = null)
    }

    /** Used by both the manual-entry field and the QR scan result. */
    fun submitToken(token: String = _uiState.value.token) {
        if (token.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter or scan a download token")
            return
        }
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null, token = token)
        viewModelScope.launch {
            when (val result = deviceRepository.addDeviceByToken(token)) {
                is AddDeviceResult.Success -> _uiState.value =
                    _uiState.value.copy(isSubmitting = false, addedDeviceName = result.deviceName)
                is AddDeviceResult.Failure -> _uiState.value =
                    _uiState.value.copy(isSubmitting = false, errorMessage = result.message)
            }
        }
    }
}
