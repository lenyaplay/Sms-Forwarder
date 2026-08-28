package com.smsforwarder.gateway.data.local.db

import androidx.room.Entity

@Entity(tableName = "conversation_meta", primaryKeys = ["sender"])
data class ConversationMetaEntity(
    val sender: String,
    val isArchived: Boolean,
)
