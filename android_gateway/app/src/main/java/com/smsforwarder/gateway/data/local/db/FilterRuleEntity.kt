package com.smsforwarder.gateway.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FilterStage { RECEPTION, FORWARDING }

enum class FilterMode { BLACKLIST, WHITELIST }

@Entity(tableName = "filter_rules")
data class FilterRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stage: FilterStage,
    val senderPattern: String?,
    val senderIsRegex: Boolean,
    val subscriptionId: Int?,
    val contentPattern: String?,
    val contentIsRegex: Boolean,
    val enabled: Boolean,
    val sortOrder: Int,
)
