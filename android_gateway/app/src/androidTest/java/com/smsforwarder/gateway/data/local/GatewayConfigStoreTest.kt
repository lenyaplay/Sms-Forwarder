package com.smsforwarder.gateway.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import com.smsforwarder.gateway.data.local.db.FilterMode
import com.smsforwarder.gateway.data.local.db.FilterStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GatewayConfigStoreTest {

    private lateinit var store: GatewayConfigStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Reset the backing SharedPreferences so a leftover value from another test doesn't leak in.
        context.getSharedPreferences("sms_forwarder_gateway_config", Context.MODE_PRIVATE).edit().clear().commit()
        store = GatewayConfigStore(context)
    }

    @Test
    fun defaultsMatchThePreviouslyHardcodedConstants() {
        assertEquals(10, store.retryMaxAttempts())
        assertEquals(30L, store.retryBaseIntervalSeconds())
        assertEquals(BackoffPolicy.EXPONENTIAL, store.retryBackoffPolicy())
    }

    @Test
    fun retryMaxAttemptsRoundTrips() {
        store.setRetryMaxAttempts(5)
        assertEquals(5, store.retryMaxAttempts())
    }

    @Test
    fun retryBaseIntervalSecondsRoundTrips() {
        store.setRetryBaseIntervalSeconds(120L)
        assertEquals(120L, store.retryBaseIntervalSeconds())
    }

    @Test
    fun retryBackoffPolicyRoundTrips() {
        store.setRetryBackoffPolicy(BackoffPolicy.LINEAR)
        assertEquals(BackoffPolicy.LINEAR, store.retryBackoffPolicy())
    }

    @Test
    fun forwardingPausedDefaultsToFalse() {
        assertEquals(false, store.isForwardingPaused())
    }

    @Test
    fun forwardingPausedRoundTrips() {
        store.setForwardingPaused(true)
        assertEquals(true, store.isForwardingPaused())
    }

    @Test
    fun resetDeliverySettingsClearsOnlyDeliveryKeysNotFilterOrImportKeys() {
        store.save("https://example.com", "tok-123")
        store.setRetryMaxAttempts(5)
        store.setRetryBaseIntervalSeconds(120L)
        store.setRetryBackoffPolicy(BackoffPolicy.LINEAR)
        store.setForwardingPaused(true)
        store.setFilterMode(FilterStage.RECEPTION, FilterMode.WHITELIST)
        store.setFilterMode(FilterStage.FORWARDING, FilterMode.WHITELIST)
        store.markHistoryImported()
        store.setLastSyncedSmsRowId(42L)

        store.resetDeliverySettings()

        assertEquals(null, store.getServerUrl())
        assertEquals(null, store.getUploadToken())
        assertEquals(10, store.retryMaxAttempts())
        assertEquals(30L, store.retryBaseIntervalSeconds())
        assertEquals(BackoffPolicy.EXPONENTIAL, store.retryBackoffPolicy())
        assertEquals(false, store.isForwardingPaused())

        assertEquals(FilterMode.WHITELIST, store.filterMode(FilterStage.RECEPTION))
        assertEquals(FilterMode.WHITELIST, store.filterMode(FilterStage.FORWARDING))
        assertTrue(store.isHistoryImported())
        assertEquals(42L, store.lastSyncedSmsRowId())
    }

    @Test
    fun deleteAfterForwardDefaultsToFalse() {
        assertEquals(false, store.deleteAfterForward())
    }

    @Test
    fun deleteAfterForwardRoundTrips() {
        store.setDeleteAfterForward(true)
        assertEquals(true, store.deleteAfterForward())
    }

    @Test
    fun hideContactNameInPayloadDefaultsToTrue() {
        assertEquals(true, store.hideContactNameInPayload())
    }

    @Test
    fun hideContactNameInPayloadRoundTrips() {
        store.setHideContactNameInPayload(false)
        assertEquals(false, store.hideContactNameInPayload())
    }

    @Test
    fun diagnosticsEnabledDefaultsToFalse() {
        assertEquals(false, store.isDiagnosticsEnabled())
    }

    @Test
    fun diagnosticsEnabledRoundTrips() {
        store.setDiagnosticsEnabled(true)
        assertEquals(true, store.isDiagnosticsEnabled())
    }

    // SharedPreferences dispatches OnSharedPreferenceChangeListener callbacks
    // asynchronously via a main-thread Handler post, even after apply() has
    // already returned - so assertions on the callback must poll, not assert
    // immediately after the triggering write.
    @Test
    fun diagnosticsEnabledChangeListenerFiresOnlyForItsOwnKey() {
        var lastValue: Boolean? = null
        var callCount = 0
        val listener = store.addOnDiagnosticsEnabledChangeListener {
            lastValue = it
            callCount++
        }
        try {
            store.setForwardingPaused(true) // unrelated key - must not fire
            Thread.sleep(200)
            assertEquals(0, callCount)

            store.setDiagnosticsEnabled(true)
            waitUntil { callCount == 1 }
            assertEquals(true, lastValue)

            store.setDiagnosticsEnabled(false)
            waitUntil { callCount == 2 }
            assertEquals(false, lastValue)
        } finally {
            store.removeOnDiagnosticsEnabledChangeListener(listener)
        }
    }

    private fun waitUntil(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !condition()) Thread.sleep(20)
        assertTrue(condition())
    }
}
