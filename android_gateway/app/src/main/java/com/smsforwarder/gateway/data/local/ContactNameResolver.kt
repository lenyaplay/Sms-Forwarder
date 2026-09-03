package com.smsforwarder.gateway.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Best-effort contact name lookup - returns null (never throws) whenever the
 * permission is missing or nothing matches.
 *
 * Spec 0026: [displayNameFor] used to hit `content://com.android.contacts` via
 * [ContactsContract.PhoneLookup] on every single call - one IPC round-trip per
 * sender, ~15-18ms each, measured at ~720-925ms total for ~50 senders (the
 * dominant cost of cold start, ~4-6x the Room query itself). A per-ViewModel
 * in-memory cache didn't help because a cold start creates a fresh ViewModel
 * with an empty cache every time. Caching here instead - at the Singleton
 * level, backed by a JSON file that survives process death - means only the
 * very first resolution of a given sender ever touches ContactsProvider;
 * every cold start after that reads the persisted map (a JSON parse of a few
 * dozen/hundred entries costs low-single-digit ms, not hundreds).
 */
@Singleton
open class ContactNameResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Serializable
    private data class CacheEntry(val sender: String, val displayName: String?)

    // Explicit key presence (via containsKey), not a non-null value, marks "already
    // resolved" - a sender genuinely absent from Contacts resolves to a real null
    // that must itself be cached, or every cold start would re-query ContactsProvider
    // for every unknown number too.
    private val cache: MutableMap<String, String?>
    private val cacheMutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var contactsObserver: ContentObserver? = null

    init {
        cache = loadCacheFromDisk().toMutableMap()
        registerContactsObserverIfPermitted()
    }

    open fun displayNameFor(phoneNumber: String): String? {
        synchronized(cache) {
            if (cache.containsKey(phoneNumber)) return cache[phoneNumber]
        }
        val resolved = queryContactsProvider(phoneNumber)
        synchronized(cache) { cache[phoneNumber] = resolved }
        ioScope.launch { persistCacheToDisk() }
        return resolved
    }

    private fun queryContactsProvider(phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        return context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    /** Drops the whole cache on any address-book change - the dataset is small (personal-scale conversation count), so a full re-resolve on next access is cheap and avoids tracking which specific contact changed. */
    private fun registerContactsObserverIfPermitted() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                synchronized(cache) { cache.clear() }
                ioScope.launch { persistCacheToDisk() }
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            contactsObserver = observer
        }.onFailure { Log.w(TAG, "failed to register contacts observer", it) }
    }

    private fun loadCacheFromDisk(): Map<String, String?> {
        val file = cacheFile()
        if (!file.exists()) return emptyMap()
        return runCatching {
            Json.decodeFromString<List<CacheEntry>>(file.readText()).associate { it.sender to it.displayName }
        }.getOrElse {
            Log.w(TAG, "failed to read contact name cache, starting empty", it)
            emptyMap()
        }
    }

    private suspend fun persistCacheToDisk() {
        cacheMutex.withLock {
            val snapshot = synchronized(cache) { cache.map { (sender, name) -> CacheEntry(sender, name) } }
            runCatching {
                cacheFile().writeText(Json.encodeToString(snapshot))
            }.onFailure { Log.w(TAG, "failed to write contact name cache", it) }
        }
    }

    private fun cacheFile() = File(context.filesDir, CACHE_FILE_NAME)

    companion object {
        private const val TAG = "ContactNameResolver"
        private const val CACHE_FILE_NAME = "contact_name_cache.json"
    }
}
