package com.smsforwarder.gateway.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.work.BackoffPolicy
import com.smsforwarder.gateway.data.local.db.FilterMode
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.data.remote.WebhookUrlBuilder

/**
 * Server URL + upload_token this device forwards SMS to. Plain SharedPreferences,
 * matching android/.../ServerConfigStore.kt in the Viewer App - the upload_token
 * is device-scoped, not a user credential, so Keystore encryption (as used for
 * the Viewer App's own auth tokens) isn't warranted here either.
 */
open class GatewayConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    open fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)

    open fun getUploadToken(): String? = prefs.getString(KEY_UPLOAD_TOKEN, null)

    open fun isConfigured(): Boolean = getServerUrl() != null && getUploadToken() != null

    open fun save(serverUrl: String, uploadToken: String) {
        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_UPLOAD_TOKEN, uploadToken)
            .apply()
    }

    open fun webhookUrl(): String? {
        val serverUrl = getServerUrl() ?: return null
        val token = getUploadToken() ?: return null
        return WebhookUrlBuilder.build(serverUrl, token)
    }

    /** Guards the one-time content://sms import so it doesn't re-run on every default-app grant/app restart. */
    open fun isHistoryImported(): Boolean = prefs.getBoolean(KEY_HISTORY_IMPORTED, false)

    open fun markHistoryImported() {
        prefs.edit().putBoolean(KEY_HISTORY_IMPORTED, true).apply()
    }

    /**
     * High-water mark (content://sms _ID) for incremental sync - rows other
     * apps write directly to the system SMS provider (e.g. an OEM dialer's
     * "decline with message") never go through SMS_DELIVER, so this is the
     * only way to notice them without a full re-import.
     */
    open fun lastSyncedSmsRowId(): Long = prefs.getLong(KEY_LAST_SYNCED_SMS_ROW_ID, 0L)

    open fun setLastSyncedSmsRowId(rowId: Long) {
        prefs.edit().putLong(KEY_LAST_SYNCED_SMS_ROW_ID, rowId).apply()
    }

    /** BLACKLIST by default so an app update never silently cuts off forwarding for existing users. */
    open fun filterMode(stage: FilterStage): FilterMode {
        val key = filterModeKey(stage)
        val stored = prefs.getString(key, null) ?: return FilterMode.BLACKLIST
        return runCatching { FilterMode.valueOf(stored) }.getOrDefault(FilterMode.BLACKLIST)
    }

    open fun setFilterMode(stage: FilterStage, mode: FilterMode) {
        prefs.edit().putString(filterModeKey(stage), mode.name).apply()
    }

    private fun filterModeKey(stage: FilterStage): String = when (stage) {
        FilterStage.RECEPTION -> KEY_RECEPTION_FILTER_MODE
        FilterStage.FORWARDING -> KEY_FORWARDING_FILTER_MODE
    }

    /** Defaults match the previously-hardcoded WebhookRequestWorker/MessageRepository constants, so an app update doesn't silently change existing retry behavior. */
    open fun retryMaxAttempts(): Int = prefs.getInt(KEY_RETRY_MAX_ATTEMPTS, 10)

    open fun setRetryMaxAttempts(value: Int) {
        prefs.edit().putInt(KEY_RETRY_MAX_ATTEMPTS, value).apply()
    }

    open fun retryBaseIntervalSeconds(): Long = prefs.getLong(KEY_RETRY_BASE_INTERVAL_SECONDS, 30L)

    open fun setRetryBaseIntervalSeconds(value: Long) {
        prefs.edit().putLong(KEY_RETRY_BASE_INTERVAL_SECONDS, value).apply()
    }

    open fun retryBackoffPolicy(): BackoffPolicy {
        val stored = prefs.getString(KEY_RETRY_BACKOFF_POLICY, null) ?: return BackoffPolicy.EXPONENTIAL
        return runCatching { BackoffPolicy.valueOf(stored) }.getOrDefault(BackoffPolicy.EXPONENTIAL)
    }

    open fun setRetryBackoffPolicy(value: BackoffPolicy) {
        prefs.edit().putString(KEY_RETRY_BACKOFF_POLICY, value.name).apply()
    }

    /** Default false so an app update doesn't silently stop forwarding for existing users. */
    open fun isForwardingPaused(): Boolean = prefs.getBoolean(KEY_FORWARDING_PAUSED, false)

    open fun setForwardingPaused(value: Boolean) {
        prefs.edit().putBoolean(KEY_FORWARDING_PAUSED, value).apply()
    }

    /** Point removals, not .clear() - this prefs file also holds filter modes and import bookkeeping, which must survive a delivery reset untouched. */
    open fun resetDeliverySettings() {
        prefs.edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_UPLOAD_TOKEN)
            .remove(KEY_RETRY_MAX_ATTEMPTS)
            .remove(KEY_RETRY_BASE_INTERVAL_SECONDS)
            .remove(KEY_RETRY_BACKOFF_POLICY)
            .remove(KEY_FORWARDING_PAUSED)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "sms_forwarder_gateway_config"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_UPLOAD_TOKEN = "upload_token"
        const val KEY_HISTORY_IMPORTED = "history_imported"
        const val KEY_LAST_SYNCED_SMS_ROW_ID = "last_synced_sms_row_id"
        const val KEY_RECEPTION_FILTER_MODE = "reception_filter_mode"
        const val KEY_FORWARDING_FILTER_MODE = "forwarding_filter_mode"
        const val KEY_RETRY_MAX_ATTEMPTS = "retry_max_attempts"
        const val KEY_RETRY_BASE_INTERVAL_SECONDS = "retry_base_interval_seconds"
        const val KEY_RETRY_BACKOFF_POLICY = "retry_backoff_policy"
        const val KEY_FORWARDING_PAUSED = "forwarding_paused"
    }
}
