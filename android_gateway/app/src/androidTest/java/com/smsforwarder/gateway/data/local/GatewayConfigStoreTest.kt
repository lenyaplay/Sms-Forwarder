package com.smsforwarder.gateway.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import org.junit.Assert.assertEquals
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
}
