package com.smsforwarder.gateway.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "delivery_log")
data class DeliveryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val attemptNumber: Int,
    val timestamp: Long,
    val success: Boolean,
    val errorMessage: String?,
)
