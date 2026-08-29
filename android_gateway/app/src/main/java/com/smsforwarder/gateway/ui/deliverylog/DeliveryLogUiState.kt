package com.smsforwarder.gateway.ui.deliverylog

import com.smsforwarder.gateway.data.local.db.DeliveryLogEntity

data class DeliveryLogUiState(
    val entries: List<DeliveryLogEntity> = emptyList(),
)
