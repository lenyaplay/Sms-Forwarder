package com.smsforwarder.viewer.data.local

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Requires a real Android Keystore provider, so this runs as an instrumented
 * test rather than a JVM unit test (Robolectric's Keystore support is
 * unreliable across API levels for AES/GCM key generation).
 */
@RunWith(AndroidJUnit4::class)
class TokenStoreTest {

    private lateinit var tokenStore: TokenStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        tokenStore = TokenStore(context)
        tokenStore.clear()
    }

    @Test
    fun readReturnsNullWhenNothingSaved() {
        assertNull(tokenStore.read())
    }

    @Test
    fun savedTokensRoundTripThroughEncryption() {
        val tokens = Tokens(accessToken = "access-123", refreshToken = "refresh-456")

        tokenStore.save(tokens)
        val read = tokenStore.read()

        assertEquals(tokens, read)
    }

    @Test
    fun clearRemovesSavedTokens() {
        tokenStore.save(Tokens("a", "b"))

        tokenStore.clear()

        assertNull(tokenStore.read())
    }

    @Test
    fun storedValueOnDiskIsNotThePlaintextToken() {
        val secretAccessToken = "super-secret-access-token-value"
        tokenStore.save(Tokens(secretAccessToken, "refresh"))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rawPrefs = context.getSharedPreferences("sms_forwarder_secure_prefs", Context.MODE_PRIVATE)
        val rawStoredAccess = rawPrefs.getString("access_token", null)

        assertFalse(
            "raw on-disk value must be ciphertext, not the plaintext token",
            rawStoredAccess == secretAccessToken,
        )
        assertFalse(
            "raw on-disk value must not even contain the plaintext token as a substring",
            rawStoredAccess?.contains(secretAccessToken) == true,
        )
    }

    @Test
    fun savingNewTokensOverwritesPreviousValue() {
        tokenStore.save(Tokens("first-access", "first-refresh"))
        tokenStore.save(Tokens("second-access", "second-refresh"))

        assertEquals(Tokens("second-access", "second-refresh"), tokenStore.read())
    }
}
