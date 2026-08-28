package com.smsforwarder.gateway.data.local

import android.content.Context
import android.content.SharedPreferences
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

    private companion object {
        const val PREFS_NAME = "sms_forwarder_gateway_config"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_UPLOAD_TOKEN = "upload_token"
        const val KEY_HISTORY_IMPORTED = "history_imported"
        const val KEY_LAST_SYNCED_SMS_ROW_ID = "last_synced_sms_row_id"
    }
}
