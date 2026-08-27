package com.smsforwarder.viewer.di

import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.viewer.data.local.ServerConfigStore
import com.smsforwarder.viewer.data.local.SessionEvents
import com.smsforwarder.viewer.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Spec 0011: changing the saved server URL must redirect traffic WITHOUT
 * rebuilding the OkHttpClient/Retrofit/ApiService chain - Hilt's singletons
 * are constructed once, eagerly, at MainActivity's field injection, before
 * the user ever sees the "Server setup" screen, so a design that required a
 * rebuild to pick up a new URL was never actually reachable in the live app
 * (this is the bug spec 0011 found and fixed via DynamicBaseUrlInterceptor,
 * which reads ServerConfigStore fresh on every request instead of baking a
 * host into Retrofit's baseUrl() once). This proves the SAME already-built
 * ApiService instance follows a URL change with no rebuild in between.
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

    @Test
    fun changingSavedUrlRedirectsTrafficOnTheSameAlreadyBuiltApiServiceWithNoRebuild() = runBlocking {
        serverA = MockWebServer().apply { start() }
        serverB = MockWebServer().apply { start() }
        serverA.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"devices":[]}"""))
        serverB.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"devices":[]}"""))

        val store = ServerConfigStore(context())
        store.save(serverA.url("/").toString())

        // Build the chain exactly once, like Hilt's singleton graph does.
        val client = NetworkModule.provideOkHttpClient(TokenStore(context()), SessionEvents(), store)
        val retrofit = NetworkModule.provideRetrofit(client, NetworkModule.provideJson(), NetworkModule.provideBaseUrl(store))
        val apiService = NetworkModule.provideApiService(retrofit)

        apiService.listDevices()
        assertEquals(1, serverA.requestCount)
        assertEquals(0, serverB.requestCount)
        val requestToA = serverA.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("expected serverA to receive a request", requestToA)
        assertEquals("/devices", requestToA!!.path)

        // Change the saved URL - no rebuild of client/retrofit/apiService.
        store.save(serverB.url("/").toString())

        apiService.listDevices()
        assertEquals(1, serverA.requestCount)
        assertEquals(1, serverB.requestCount)
        val requestToB = serverB.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("expected serverB to receive a request", requestToB)
        assertEquals("/devices", requestToB!!.path)
    }
}
