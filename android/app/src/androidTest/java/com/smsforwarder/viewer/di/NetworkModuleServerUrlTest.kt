package com.smsforwarder.viewer.di

import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.viewer.data.local.ServerConfigStore
import com.smsforwarder.viewer.data.local.SessionEvents
import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.remote.ApiService
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Spec 0010's assumption 2: a server-URL change takes effect on the next
 * DI-graph build (app restart/relogin), not live. ServerSetupScreenTest only
 * covers URL *format* validation - this proves a saved URL actually drives
 * real traffic through the exact NetworkModule provider chain, and that
 * rebuilding that chain after a URL change redirects traffic to the new
 * server rather than silently keeping the old one.
 */
class NetworkModuleServerUrlTest {

    private lateinit var serverA: MockWebServer
    private lateinit var serverB: MockWebServer

    @After
    fun tearDown() {
        serverA.shutdown()
        serverB.shutdown()
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun buildApiService(baseUrl: String): ApiService {
        val client = NetworkModule.provideOkHttpClient(TokenStore(context()), SessionEvents(), baseUrl)
        val retrofit = NetworkModule.provideRetrofit(client, NetworkModule.provideJson(), baseUrl)
        return NetworkModule.provideApiService(retrofit)
    }

    @Test
    fun changingSavedUrlAndRebuildingTheGraphRedirectsTrafficToTheNewServer() = runBlocking {
        serverA = MockWebServer().apply { start() }
        serverB = MockWebServer().apply { start() }
        serverA.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"devices":[]}"""))
        serverB.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"devices":[]}"""))

        val store = ServerConfigStore(context())
        store.save(serverA.url("/").toString())

        val baseUrlA = NetworkModule.provideBaseUrl(store)
        assertEquals(serverA.url("/").toString(), baseUrlA)
        buildApiService(baseUrlA).listDevices()

        assertEquals(1, serverA.requestCount)
        assertEquals(0, serverB.requestCount)
        val requestToA = serverA.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("expected serverA to receive a request", requestToA)
        assertEquals("/devices", requestToA!!.path)

        // Simulate the user changing servers in Settings, then the required
        // relogin/app-restart that rebuilds the whole DI graph from scratch -
        // a fresh chain must be built, not the one from above reused.
        store.save(serverB.url("/").toString())
        val baseUrlB = NetworkModule.provideBaseUrl(store)
        assertEquals(serverB.url("/").toString(), baseUrlB)
        buildApiService(baseUrlB).listDevices()

        assertEquals(1, serverA.requestCount)
        assertEquals(1, serverB.requestCount)
        val requestToB = serverB.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("expected serverB to receive a request", requestToB)
        assertEquals("/devices", requestToB!!.path)
    }
}
