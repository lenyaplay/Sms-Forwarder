package com.smsforwarder.gateway.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.gateway.data.local.GatewaySettingsExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface SettingsActions {
    fun onExportConfirmed(uri: Uri)
    fun onImportConfirmed(uri: Uri)
    fun onMessageDismissed()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exporter: GatewaySettingsExporter,
    @ApplicationContext private val context: Context,
) : ViewModel(), SettingsActions {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    override fun onExportConfirmed(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = exporter.exportToJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("Не удалось открыть файл для записи")
                }
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { state.copy(message = "Настройки экспортированы", isMessageError = false) },
                    onFailure = { e -> state.copy(message = "Ошибка экспорта: ${e.message}", isMessageError = true) },
                )
            }
        }
    }

    override fun onImportConfirmed(uri: Uri) {
        viewModelScope.launch {
            val readResult = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("Не удалось открыть файл для чтения")
                }
            }
            val text = readResult.getOrElse { e ->
                _uiState.update { it.copy(message = "Ошибка импорта: ${e.message}", isMessageError = true) }
                return@launch
            }
            val importResult = exporter.importFromJson(text)
            _uiState.update { state ->
                importResult.fold(
                    onSuccess = { state.copy(message = "Настройки импортированы", isMessageError = false) },
                    onFailure = { e -> state.copy(message = "Ошибка импорта: ${e.message}", isMessageError = true) },
                )
            }
        }
    }

    override fun onMessageDismissed() {
        _uiState.update { it.copy(message = null, isMessageError = false) }
    }
}
