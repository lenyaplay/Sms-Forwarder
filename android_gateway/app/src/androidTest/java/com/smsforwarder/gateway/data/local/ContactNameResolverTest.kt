package com.smsforwarder.gateway.data.local

import android.content.Context
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 0026: [ContactNameResolver] moved from "query ContactsProvider on every
 * call" to a Singleton-level cache persisted as JSON to filesDir - this is what
 * the real-device measurement (spec 0026) identified as the dominant cold-start
 * cost (~720-925ms for ~50 senders, one ContentResolver IPC round-trip each).
 * These tests exercise the cache/persistence/invalidation layer without
 * depending on real device contacts (which would be flaky/nondeterministic) -
 * they seed the cache file directly and verify [ContactNameResolver] reads it
 * back rather than hitting ContactsProvider.
 */
@RunWith(AndroidJUnit4::class)
class ContactNameResolverTest {

    private lateinit var context: Context
    private lateinit var cacheFile: java.io.File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} android.permission.READ_CONTACTS")
            .close()
        cacheFile = java.io.File(context.filesDir, "contact_name_cache.json")
        cacheFile.delete()
    }

    @After
    fun tearDown() {
        cacheFile.delete()
    }

    @Test
    fun readsAPreExistingCacheEntryWithoutQueryingContactsProvider() {
        // A sender with a phone number guaranteed absent from this test device's real
        // address book - if the resolver ignored the cache and queried
        // ContactsProvider for real, it would find nothing and return null instead of
        // this seeded value, so a match here proves the cache path was actually taken.
        cacheFile.writeText("""[{"sender":"+70000000001","displayName":"Cached Name"}]""")

        val resolver = ContactNameResolver(context)

        assertEquals("Cached Name", resolver.displayNameFor("+70000000001"))
    }

    @Test
    fun cachesANegativeResultSoAnUnknownSenderIsNotReResolvedAsAlwaysMissing() {
        cacheFile.writeText("""[{"sender":"+70000000002","displayName":null}]""")

        val resolver = ContactNameResolver(context)

        assertNull(resolver.displayNameFor("+70000000002"))
    }

    @Test
    fun cacheSurvivesAcrossInstancesAfterAResolution() {
        val firstResolver = ContactNameResolver(context)
        // No real contact for this number - queryContactsProvider returns null, which
        // must itself be persisted as a cached negative result (see next assertion).
        firstResolver.displayNameFor("+70000000003")
        waitForCacheFileToContain("+70000000003")

        val secondResolver = ContactNameResolver(context)
        // If the second instance actually re-queried ContactsProvider instead of
        // reading the persisted file, this would still correctly return null (no such
        // contact exists) - the real assertion is the cache FILE below, which proves
        // persistence happened rather than every call coincidentally returning null.
        assertNull(secondResolver.displayNameFor("+70000000003"))
        assert(cacheFile.exists()) { "cache file was not written after a resolution" }
    }

    @Test
    fun addressBookChangeClearsTheCache() {
        cacheFile.writeText("""[{"sender":"+70000000004","displayName":"Cached Name"}]""")
        val resolver = ContactNameResolver(context)
        assertEquals("Cached Name", resolver.displayNameFor("+70000000004"))

        context.contentResolver.notifyChange(ContactsContract.Contacts.CONTENT_URI, null)

        val deadlineNanos = System.nanoTime() + 5_000_000_000L
        var resolvedAfterInvalidation: String? = "Cached Name"
        while (System.nanoTime() < deadlineNanos) {
            resolvedAfterInvalidation = resolver.displayNameFor("+70000000004")
            if (resolvedAfterInvalidation == null) break
            Thread.sleep(100)
        }
        // No real contact for this number exists on the test device, so once the
        // cache is actually cleared and re-queried for real, this must be null - a
        // continued "Cached Name" would mean the address-book-change notification
        // never invalidated anything.
        assertNull(resolvedAfterInvalidation)
    }

    @Test
    fun preSpec0027CacheFileWithoutPhotoUriStillParsesAndDefaultsPhotoToNull() {
        // Cache file shape written by the Milestone 24 build (spec 0026) - no
        // "photoUri" key at all. Spec 0027 added the field with a default so
        // already-installed builds' cache files don't get silently discarded
        // (loadCacheFromDisk falls back to an empty cache on any parse failure,
        // which would otherwise be indistinguishable from "working as intended"
        // here - the real assertion is that the OLD name is still served).
        cacheFile.writeText("""[{"sender":"+70000000005","displayName":"Pre-0027 Name"}]""")

        val resolver = ContactNameResolver(context)
        val info = resolver.contactInfoFor("+70000000005")

        assertEquals("Pre-0027 Name", info.displayName)
        assertNull(info.photoUri)
    }

    @Test
    fun contactInfoForCachesPhotoUriAlongsideDisplayName() {
        cacheFile.writeText("""[{"sender":"+70000000006","displayName":"With Photo","photoUri":"content://fake/photo"}]""")

        val resolver = ContactNameResolver(context)
        val info = resolver.contactInfoFor("+70000000006")

        assertEquals("With Photo", info.displayName)
        assertEquals("content://fake/photo", info.photoUri)
    }

    private fun waitForCacheFileToContain(needle: String) {
        val deadlineNanos = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadlineNanos) {
            if (cacheFile.exists() && cacheFile.readText().contains(needle)) return
            Thread.sleep(50)
        }
    }
}
