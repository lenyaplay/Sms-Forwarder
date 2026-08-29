package com.smsforwarder.gateway.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DeliveryStatus { PENDING, SENT, FAILED, NOT_FORWARDED }

enum class MessageDirection { IN, OUT }

@Entity(tableName = "messages", indices = [Index(value = ["sender", "createdAt"])])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val text: String,
    val sentStamp: Long?,
    val receivedStamp: Long,
    val simSlot: Int?,
    val deliveryStatus: DeliveryStatus,
    val createdAt: Long,
    val direction: MessageDirection = MessageDirection.IN,
)
