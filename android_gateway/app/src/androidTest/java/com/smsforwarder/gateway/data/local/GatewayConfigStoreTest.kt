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

    private data class ConfigField<T>(
        val name: String,
        val default: T,
        val sample: T,
        val getter: (GatewayConfigStore) -> T,
        val setter: (GatewayConfigStore, T) -> Unit,
    )

    private val configFields = listOf(
        ConfigField("retryMaxAttempts", 10, 5, { it.retryMaxAttempts() }, { s, v -> s.setRetryMaxAttempts(v) }),
        ConfigField("retryBaseIntervalSeconds", 30L, 120L, { it.retryBaseIntervalSeconds() }, { s, v -> s.setRetryBaseIntervalSeconds(v) }),
        ConfigField(
            "retryBackoffPolicy",
            BackoffPolicy.EXPONENTIAL,
            BackoffPolicy.LINEAR,
            { it.retryBackoffPolicy() },
            { s, v -> s.setRetryBackoffPolicy(v) },
        ),
        ConfigField("forwardingPaused", false, true, { it.isForwardingPaused() }, { s, v -> s.setForwardingPaused(v) }),
        ConfigField("deleteAfterForward", false, true, { it.deleteAfterForward() }, { s, v -> s.setDeleteAfterForward(v) }),
        ConfigField("hideContactNameInPayload", true, false, { it.hideContactNameInPayload() }, { s, v -> s.setHideContactNameInPayload(v) }),
        ConfigField("diagnosticsEnabled", false, true, { it.isDiagnosticsEnabled() }, { s, v -> s.setDiagnosticsEnabled(v) }),
    )

    private fun <T> verify(field: ConfigField<T>) {
        assertEquals("${field.name} default", field.default, field.getter(store))
        field.setter(store, field.sample)
        assertEquals("${field.name} round-trip", field.sample, field.getter(store))
    }

    @Test
    fun eachConfigFieldDefaultsCorrectlyAndRoundTripsThroughStorage() {
        configFields.forEach { verify(it) }
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
