package com.smsforwarder.viewer.ui.createdevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.viewer.data.local.ServerConfigStore
import com.smsforwarder.viewer.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateDeviceUiState(
    val name: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val createdUploadToken: String? = null,
    val webhookUrl: String? = null,
)

@HiltViewModel
class CreateDeviceViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val serverConfigStore: ServerConfigStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateDeviceUiState())
    val uiState: StateFlow<CreateDeviceUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value, errorMessage = null)
    }

    fun create() {
        val name = _uiState.value.name.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Device name is required")
            return
        }

        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = deviceRepository.createDevice(name)
            result.fold(
                onSuccess = { response ->
                    val serverUrl = serverConfigStore.getUrl().orEmpty()
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        createdUploadToken = response.upload_token,
                        webhookUrl = "${serverUrl}webhook?upload_token=${response.upload_token}",
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = "Failed to create device: ${error.message ?: "unknown error"}",
                    )
                },
            )
        }
    }
}
