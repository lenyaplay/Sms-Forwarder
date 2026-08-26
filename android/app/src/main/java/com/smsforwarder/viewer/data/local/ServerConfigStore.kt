package com.smsforwarder.viewer.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the backend server URL the user configured (spec 0010). Unlike
 * [TokenStore] this is plain SharedPreferences, not Keystore-encrypted - a
 * server URL isn't a secret.
 */
open class ServerConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    open fun getUrl(): String? = prefs.getString(KEY_URL, null)

    open fun hasUrl(): Boolean = getUrl() != null

    open fun save(url: String) {
        prefs.edit().putString(KEY_URL, url).apply()
    }

    private companion object {
        const val PREFS_NAME = "sms_forwarder_server_config"
        const val KEY_URL = "server_url"
    }
}
