package com.smsforwarder.viewer.data.remote

import com.smsforwarder.viewer.data.local.SessionEvents
import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.local.Tokens
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AuthAuthenticatorTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenStore: TokenStore
    private lateinit var sessionEvents: SessionEvents

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenStore = mock()
        sessionEvents = mock()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful refresh retries original request with new access token`() = runBlocking {
        whenever(tokenStore.read()).thenReturn(Tokens("old-access", "old-refresh"))

        server.enqueue(MockResponse().setResponseCode(401)) // first attempt on the protected endpoint
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"new-access","refresh_token":"new-refresh"}"""),
        ) // /auth/refresh
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok")) // retried original request

        val client = OkHttpClient.Builder()
            .authenticator(AuthAuthenticator(server.url("/").toString(), tokenStore, sessionEvents))
            .build()

        val response = client.newCall(Request.Builder().url(server.url("/protected")).build()).execute()

        assertEquals(200, response.code)
        verify(tokenStore).save(Tokens("new-access", "new-refresh"))
    }

    @Test
    fun `failed refresh clears tokens and notifies logout`() = runBlocking {
        whenever(tokenStore.read()).thenReturn(Tokens("old-access", "old-refresh"))

        server.enqueue(MockResponse().setResponseCode(401)) // protected endpoint
        server.enqueue(MockResponse().setResponseCode(401)) // /auth/refresh rejects too

        val client = OkHttpClient.Builder()
            .authenticator(AuthAuthenticator(server.url("/").toString(), tokenStore, sessionEvents))
            .build()

        val response = client.newCall(Request.Builder().url(server.url("/protected")).build()).execute()

        assertEquals(401, response.code)
        verify(tokenStore).clear()
        verify(sessionEvents).notifyLoggedOut()
    }

    @Test
    fun `no stored refresh token notifies logout without calling server`() = runBlocking {
        whenever(tokenStore.read()).thenReturn(null)
        server.enqueue(MockResponse().setResponseCode(401))

        val client = OkHttpClient.Builder()
            .authenticator(AuthAuthenticator(server.url("/").toString(), tokenStore, sessionEvents))
            .build()

        val response = client.newCall(Request.Builder().url(server.url("/protected")).build()).execute()

        assertEquals(401, response.code)
        verify(sessionEvents).notifyLoggedOut()
        assertEquals(1, server.requestCount)
    }
}
