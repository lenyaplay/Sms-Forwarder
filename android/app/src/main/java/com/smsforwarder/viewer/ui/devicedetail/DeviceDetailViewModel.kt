package com.smsforwarder.viewer.ui.devicedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.viewer.data.remote.dto.DownloadTokenDto
import com.smsforwarder.viewer.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceDetailUiState(
    val isLoading: Boolean = true,
    val uploadToken: String? = null,
    val downloadTokens: List<DownloadTokenDto> = emptyList(),
    val invitedToken: DownloadTokenDto? = null,
    val isGeneratingInvite: Boolean = false,
    val isReissuing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deviceId: Long = checkNotNull(savedStateHandle["deviceId"])

    private val _uiState = MutableStateFlow(DeviceDetailUiState())
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val uploadToken = deviceRepository.listDevices()
                .getOrNull()
                ?.firstOrNull { it.id == deviceId }
                ?.upload_token
            deviceRepository.listDownloadTokens(deviceId)
                .onSuccess { tokens ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        uploadToken = uploadToken,
                        downloadTokens = tokens,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        uploadToken = uploadToken,
                        errorMessage = error.message ?: "Failed to load device details",
                    )
                }
        }
    }

    fun generateInvite() {
        _uiState.value = _uiState.value.copy(isGeneratingInvite = true, errorMessage = null)
        viewModelScope.launch {
            deviceRepository.createDownloadToken(deviceId)
                .onSuccess { token ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingInvite = false,
                        invitedToken = token,
                        downloadTokens = listOf(token) + _uiState.value.downloadTokens,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingInvite = false,
                        errorMessage = error.message ?: "Failed to generate invite",
                    )
                }
        }
    }

    fun revokeDownloadToken(tokenId: Long) {
        viewModelScope.launch {
            deviceRepository.revokeDownloadToken(deviceId, tokenId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        downloadTokens = _uiState.value.downloadTokens.filterNot { it.id == tokenId },
                        invitedToken = _uiState.value.invitedToken?.takeUnless { it.id == tokenId },
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Failed to revoke token")
                }
        }
    }

    fun reissueUploadToken() {
        _uiState.value = _uiState.value.copy(isReissuing = true, errorMessage = null)
        viewModelScope.launch {
            deviceRepository.reissueUploadToken(deviceId)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(isReissuing = false, uploadToken = response.upload_token)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isReissuing = false,
                        errorMessage = error.message ?: "Failed to reissue upload token",
                    )
                }
        }
    }
}
