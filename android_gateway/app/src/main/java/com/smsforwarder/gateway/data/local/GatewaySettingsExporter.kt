package com.smsforwarder.gateway.data.local

import androidx.work.BackoffPolicy
import com.smsforwarder.gateway.data.local.db.FilterRuleDao
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity
import com.smsforwarder.gateway.data.local.db.FilterStage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportedFilterRule(
    val stage: FilterStage,
    val senderPattern: String?,
    val senderIsRegex: Boolean,
    val subscriptionId: Int?,
    val contentPattern: String?,
    val contentIsRegex: Boolean,
    val enabled: Boolean,
    val sortOrder: Int,
)

/** Server URL/token, retry settings, filter rules - deliberately NOT the newer 0017/0018 toggles (pause, delete-after-forward, hide-contact-name) or delivery_log, see spec 0018's open questions. */
@Serializable
data class ExportedSettings(
    val serverUrl: String?,
    val uploadToken: String?,
    val retryMaxAttempts: Int,
    val retryBaseIntervalSeconds: Long,
    val retryBackoffPolicy: String,
    val filterRules: List<ExportedFilterRule>,
)

/**
 * Export/import for spec 0018 - a JSON snapshot a user can carry to a fresh
 * install via Storage Access Framework (ui/settings/SettingsScreen.kt). No
 * network involved.
 */
@Singleton
open class GatewaySettingsExporter @Inject constructor(
    private val configStore: GatewayConfigStore,
    private val filterRuleDao: FilterRuleDao,
    private val json: Json,
) {
    open suspend fun exportToJson(): String {
        val exported = ExportedSettings(
            serverUrl = configStore.getServerUrl(),
            uploadToken = configStore.getUploadToken(),
            retryMaxAttempts = configStore.retryMaxAttempts(),
            retryBaseIntervalSeconds = configStore.retryBaseIntervalSeconds(),
            retryBackoffPolicy = configStore.retryBackoffPolicy().name,
            filterRules = filterRuleDao.getAll().map {
                ExportedFilterRule(
                    stage = it.stage,
                    senderPattern = it.senderPattern,
                    senderIsRegex = it.senderIsRegex,
                    subscriptionId = it.subscriptionId,
                    contentPattern = it.contentPattern,
                    contentIsRegex = it.contentIsRegex,
                    enabled = it.enabled,
                    sortOrder = it.sortOrder,
                )
            },
        )
        return json.encodeToString(ExportedSettings.serializer(), exported)
    }

    /**
     * Validates everything before applying anything - same ranges/regex rule
     * the UI itself enforces (DeliveryUiState.maxAttemptsError/
     * baseIntervalSecondsError, FilterRuleEditUiState.regexError), so an
     * imported file can never put the app into a state the UI wouldn't have
     * allowed the user to save directly.
     */
    open suspend fun importFromJson(raw: String): Result<Unit> {
        val parsed = runCatching { json.decodeFromString(ExportedSettings.serializer(), raw) }
            .getOrElse { return Result.failure(IllegalArgumentException("Не удалось разобрать файл настроек: ${it.message}")) }

        if (parsed.retryMaxAttempts !in 1..50) {
            return Result.failure(IllegalArgumentException("Максимум попыток вне диапазона 1–50: ${parsed.retryMaxAttempts}"))
        }
        if (parsed.retryBaseIntervalSeconds !in 10..3600) {
            return Result.failure(IllegalArgumentException("Интервал между попытками вне диапазона 10–3600: ${parsed.retryBaseIntervalSeconds}"))
        }
        val backoffPolicy = runCatching { BackoffPolicy.valueOf(parsed.retryBackoffPolicy) }
            .getOrElse { return Result.failure(IllegalArgumentException("Некорректная стратегия backoff: ${parsed.retryBackoffPolicy}")) }
        parsed.filterRules.forEach { rule ->
            if (rule.senderIsRegex && !rule.senderPattern.isNullOrEmpty() && runCatching { Regex(rule.senderPattern) }.isFailure) {
                return Result.failure(IllegalArgumentException("Некорректное регулярное выражение отправителя: ${rule.senderPattern}"))
            }
            if (rule.contentIsRegex && !rule.contentPattern.isNullOrEmpty() && runCatching { Regex(rule.contentPattern) }.isFailure) {
                return Result.failure(IllegalArgumentException("Некорректное регулярное выражение содержимого: ${rule.contentPattern}"))
            }
        }

        if (parsed.serverUrl != null && parsed.uploadToken != null) {
            configStore.save(parsed.serverUrl, parsed.uploadToken)
        }
        configStore.setRetryMaxAttempts(parsed.retryMaxAttempts)
        configStore.setRetryBaseIntervalSeconds(parsed.retryBaseIntervalSeconds)
        configStore.setRetryBackoffPolicy(backoffPolicy)

        // Full replace, not merge - decided with the product owner: importing
        // is meant to restore an exact prior state, not accumulate duplicates
        // across repeated imports of the same file. Atomic (replaceAll is
        // @Transaction) so a crash mid-import can't leave rules wiped but only
        // partially restored.
        filterRuleDao.replaceAll(
            parsed.filterRules.map { rule ->
                FilterRuleEntity(
                    id = 0,
                    stage = rule.stage,
                    senderPattern = rule.senderPattern,
                    senderIsRegex = rule.senderIsRegex,
                    subscriptionId = rule.subscriptionId,
                    contentPattern = rule.contentPattern,
                    contentIsRegex = rule.contentIsRegex,
                    enabled = rule.enabled,
                    sortOrder = rule.sortOrder,
                )
            }
        )
        return Result.success(Unit)
    }
}
