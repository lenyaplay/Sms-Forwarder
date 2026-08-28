package com.smsforwarder.gateway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.gateway.data.local.SmsHistoryImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val historyImporter: SmsHistoryImporter,
) : ViewModel() {
    fun importHistoryIfNeeded() {
        viewModelScope.launch { historyImporter.importIfNeeded() }
    }
}
