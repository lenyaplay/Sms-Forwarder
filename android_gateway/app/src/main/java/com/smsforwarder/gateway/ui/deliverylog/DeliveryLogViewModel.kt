package com.smsforwarder.gateway.ui.deliverylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.gateway.data.local.db.DeliveryLogDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DeliveryLogViewModel @Inject constructor(
    deliveryLogDao: DeliveryLogDao,
) : ViewModel() {

    val uiState: StateFlow<DeliveryLogUiState> = deliveryLogDao.observeRecent()
        .map { DeliveryLogUiState(entries = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeliveryLogUiState())
}
