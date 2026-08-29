package com.smsforwarder.gateway.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebhookConnectionTesterTest {

    private lateinit var server: MockWebServer
    private lateinit var tester: WebhookConnectionTester

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tester = WebhookConnectionTester(OkHttpClient(), Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun successfulResponseReportsSuccessWithHttpCode() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = tester.test(server.url("/webhook?upload_token=tok").toString())

        assertEquals(TestConnectionResult.Success(201), result)
    }

    @Test
    fun errorResponseReportsFailureWithHttpCode() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = tester.test(server.url("/webhook?upload_token=tok").toString())

        assertEquals(TestConnectionResult.Failure("HTTP 401"), result)
    }

    @Test
    fun unreachableServerReportsFailureWithoutCrashing() = runBlocking {
        server.shutdown()

        val result = tester.test(server.url("/webhook?upload_token=tok").toString())

        assertTrue(result is TestConnectionResult.Failure)
    }
}
