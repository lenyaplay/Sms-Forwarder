package com.smsforwarder.viewer.data.remote

import com.smsforwarder.viewer.data.remote.dto.MessageDto
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource

interface SseSource {
    fun stream(url: String): Flow<MessageDto>
}

/**
 * Minimal Server-Sent-Events client: opens a streaming GET and parses
 * `event:`/`data:` lines by hand (Android/OkHttp has no built-in EventSource
 * like a browser). Emits one [MessageDto] per `event: message` block;
 * `: ping` heartbeat comments and any other event types are ignored.
 *
 * Implemented as a plain suspend `flow {}` (not `callbackFlow`) so the
 * producer runs entirely on the collector's coroutine: normal/cancelled
 * completion tears the OkHttp call down deterministically via `finally`,
 * with no separate producer coroutine left parked waiting on `awaitClose`.
 */
open class SseClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SseSource {
    override fun stream(url: String): Flow<MessageDto> = flow {
        val request = Request.Builder().url(url).build()
        val call = okHttpClient.newCall(request)

        // source.readUtf8Line()/exhausted() below are blocking OkHttp I/O
        // calls, not suspend functions, so cooperative cancellation
        // (isActive checks) can't interrupt them while parked waiting on the
        // socket. Registering call.cancel() against the collecting
        // coroutine's Job runs it from outside this coroutine the moment
        // cancellation is requested, which unblocks the pending read
        // immediately (OkHttp closes the underlying socket) instead of
        // leaving the connection/thread parked until the next line arrives.
        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }

        try {
            val response = call.execute()
            try {
                if (!response.isSuccessful) {
                    throw IllegalStateException("SSE connection failed: HTTP ${response.code}")
                }
                val source = response.body?.source()
                    ?: throw IllegalStateException("SSE response has no body")

                consumeEvents(source)
            } finally {
                response.close()
            }
        } finally {
            cancelHandle.dispose()
            call.cancel()
        }
    }

    private suspend fun FlowCollector<MessageDto>.consumeEvents(source: BufferedSource) {
        var eventType: String? = null
        val dataBuilder = StringBuilder()

        while (currentCoroutineContext().isActive && !source.exhausted()) {
            val line = source.readUtf8Line() ?: break

            when {
                line.isEmpty() -> {
                    // Blank line terminates the current event.
                    if (eventType == "message" && dataBuilder.isNotEmpty()) {
                        parseMessage(dataBuilder.toString())?.let { emit(it) }
                    }
                    eventType = null
                    dataBuilder.setLength(0)
                }
                line.startsWith(":") -> {
                    // Comment / heartbeat (`: ping`) — ignored.
                }
                line.startsWith("event:") -> {
                    eventType = line.removePrefix("event:").trim()
                }
                line.startsWith("data:") -> {
                    if (dataBuilder.isNotEmpty()) dataBuilder.append('\n')
                    dataBuilder.append(line.removePrefix("data:").trim())
                }
                else -> {
                    // Unknown field — ignored per SSE spec.
                }
            }
        }
    }

    private fun parseMessage(data: String): MessageDto? = try {
        json.decodeFromString(MessageDto.serializer(), data)
    } catch (e: Exception) {
        null
    }
}
