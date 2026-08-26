package com.smsforwarder.viewer.data.remote

import com.smsforwarder.viewer.data.remote.dto.MessageDto
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SseClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: SseClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = SseClient(OkHttpClient.Builder().build())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses a single message event`() = runBlocking {
        val body = "event: message\n" +
            "data: {\"id\":1,\"device_id\":2,\"sender\":\"+123\",\"text\":\"hi\",\"created_at\":\"2026-01-01T00:00:00Z\"}\n" +
            "\n"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                // Ensure the connection closes so the flow terminates for the test.
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_END),
        )

        val messages = client.stream(server.url("/events").toString()).toList()

        assertEquals(1, messages.size)
        assertEquals(MessageDto(1, 2, "+123", "hi", null, null, null, "2026-01-01T00:00:00Z"), messages[0])
    }

    @Test
    fun `ignores heartbeat ping comments`() = runBlocking {
        val body = ": ping\n\n" +
            "event: message\n" +
            "data: {\"id\":5,\"device_id\":9,\"sender\":\"+1\",\"text\":\"t\",\"created_at\":\"2026-01-01T00:00:00Z\"}\n" +
            "\n"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_END),
        )

        val messages = client.stream(server.url("/events").toString()).toList()

        assertEquals(1, messages.size)
        assertEquals(5L, messages[0].id)
    }

    @Test
    fun `handles multi-line data field`() = runBlocking {
        val body = "event: message\n" +
            "data: {\"id\":1,\"device_id\":2,\n" +
            "data: \"sender\":\"+123\",\"text\":\"hi\",\"created_at\":\"2026-01-01T00:00:00Z\"}\n" +
            "\n"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_END),
        )

        val messages = client.stream(server.url("/events").toString()).toList()

        assertEquals(1, messages.size)
        assertEquals(1L, messages[0].id)
    }

    @Test
    fun `no event emitted on mid-event disconnect without trailing blank line`() = runBlocking {
        val body = "event: message\n" +
            "data: {\"id\":1,\"device_id\":2,\"sender\":\"+123\",\"text\":\"hi\",\"created_at\":\"2026-01-01T00:00:00Z\"}"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_END),
        )

        val messages = client.stream(server.url("/events").toString()).toList()

        assertEquals(0, messages.size)
    }

    @Test(expected = Exception::class)
    fun `non-2xx response closes flow with error`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        client.stream(server.url("/events").toString()).toList()
    }
}
