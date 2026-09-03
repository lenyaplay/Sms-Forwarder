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

/** Resolved contact data for a sender - `displayName`/`photoUri` are independently nullable (a contact can have a name but no photo, or in principle be matched without a readable name). */
data class ContactInfo(val displayName: String?, val photoUri: String?)

/**
 * Best-effort contact lookup - returns nulls (never throws) whenever the
 * permission is missing or nothing matches.
 *
 * Spec 0026: [contactInfoFor] used to hit `content://com.android.contacts` via
 * [ContactsContract.PhoneLookup] on every single call - one IPC round-trip per
 * sender, ~15-18ms each, measured at ~720-925ms total for ~50 senders (the
 * dominant cost of cold start, ~4-6x the Room query itself). A per-ViewModel
 * in-memory cache didn't help because a cold start creates a fresh ViewModel
 * with an empty cache every time. Caching here instead - at the Singleton
 * level, backed by a JSON file that survives process death - means only the
 * very first resolution of a given sender ever touches ContactsProvider;
 * every cold start after that reads the persisted map (a JSON parse of a few
 * dozen/hundred entries costs low-single-digit ms, not hundreds).
 *
 * Spec 0027: extended to also resolve `PHOTO_THUMBNAIL_URI` for the
 * conversations-list avatar. `CacheEntry.photoUri` defaults to null so a
 * cache file written by the pre-0027 build (sender/displayName only) still
 * decodes - the app isn't in production yet, so no forced one-time
 * recompute was added; a photo simply appears the next time a sender's
 * entry is naturally invalidated (any address-book change clears the whole
 * cache, same as before).
 */
@Singleton
open class ContactNameResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Serializable
    private data class CacheEntry(val sender: String, val displayName: String?, val photoUri: String? = null)

    // Explicit key presence (via containsKey), not a non-null ContactInfo, marks
    // "already resolved" - a sender genuinely absent from Contacts resolves to a
    // real ContactInfo(null, null) that must itself be cached, or every cold start
    // would re-query ContactsProvider for every unknown number too.
    private val cache: MutableMap<String, ContactInfo>
    private val cacheMutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var contactsObserver: ContentObserver? = null

    init {
        cache = loadCacheFromDisk().toMutableMap()
        registerContactsObserverIfPermitted()
    }

    open fun displayNameFor(phoneNumber: String): String? = contactInfoFor(phoneNumber).displayName

    open fun contactInfoFor(phoneNumber: String): ContactInfo {
        synchronized(cache) {
            cache[phoneNumber]?.let { return it }
        }
        val resolved = queryContactsProvider(phoneNumber)
        synchronized(cache) { cache[phoneNumber] = resolved }
        ioScope.launch { persistCacheToDisk() }
        return resolved
    }

    private fun queryContactsProvider(phoneNumber: String): ContactInfo {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ContactInfo(displayName = null, photoUri = null)
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        return context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContactInfo(displayName = cursor.getString(0), photoUri = cursor.getString(1))
            } else {
                ContactInfo(displayName = null, photoUri = null)
            }
        } ?: ContactInfo(displayName = null, photoUri = null)
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

    private fun loadCacheFromDisk(): Map<String, ContactInfo> {
        val file = cacheFile()
        if (!file.exists()) return emptyMap()
        return runCatching {
            Json.decodeFromString<List<CacheEntry>>(file.readText())
                .associate { it.sender to ContactInfo(it.displayName, it.photoUri) }
        }.getOrElse {
            Log.w(TAG, "failed to read contact name cache, starting empty", it)
            emptyMap()
        }
    }

    private suspend fun persistCacheToDisk() {
        cacheMutex.withLock {
            val snapshot = synchronized(cache) {
                cache.map { (sender, info) -> CacheEntry(sender, info.displayName, info.photoUri) }
            }
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
