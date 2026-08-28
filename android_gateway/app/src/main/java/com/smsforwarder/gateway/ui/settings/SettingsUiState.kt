package com.smsforwarder.gateway.ui.settings

data class SettingsUiState(
    val serverUrl: String = "",
    val uploadToken: String = "",
    val isSaved: Boolean = false,
) {
    val canSave: Boolean get() = serverUrl.isNotBlank() && uploadToken.isNotBlank()
}
