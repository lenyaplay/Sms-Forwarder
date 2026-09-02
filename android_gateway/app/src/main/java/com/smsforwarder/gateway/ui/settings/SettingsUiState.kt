package com.smsforwarder.gateway.ui.settings

data class SettingsUiState(
    val message: String? = null,
    val isMessageError: Boolean = false,
    val isDiagnosticsEnabled: Boolean = false,
)
