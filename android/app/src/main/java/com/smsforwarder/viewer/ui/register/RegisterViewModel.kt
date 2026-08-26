package com.smsforwarder.viewer.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.viewer.data.repository.AuthRepository
import com.smsforwarder.viewer.data.repository.RegisterResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registeredUsername: String? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null)
    }

    fun register() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Enter username and password")
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(errorMessage = "Passwords do not match")
            return
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = authRepository.register(state.username, state.password)) {
                is RegisterResult.Success -> _uiState.value =
                    _uiState.value.copy(isLoading = false, registeredUsername = state.username)
                is RegisterResult.Failure -> _uiState.value =
                    _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }
}
