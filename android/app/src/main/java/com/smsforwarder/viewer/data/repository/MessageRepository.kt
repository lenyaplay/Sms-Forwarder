package com.smsforwarder.viewer.data.repository

import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.SseSource
import com.smsforwarder.viewer.data.remote.dto.MessageDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class MessagePage(val messages: List<MessageDto>, val nextBeforeId: Long?)

/**
 * Fetches the message feed for a device: paginated REST reads for history,
 * plus a live-update stream that prefers SSE (GET /events) and transparently
 * falls back to REST polling (`since=<last created_at>`) when the SSE
 * connection fails or closes — per spec 0006 assumption 9 / spec 0007
 * assumption 7 (foreground-only realtime). De-duplicates by message `id`
 * since a message can arrive from both a polling page and a subsequently
 * reconnected SSE stream.
 */
@Singleton
class MessageRepository @Inject constructor(
    private val apiService: ApiService,
    private val sseClient: SseSource,
    private val tokenStore: TokenStore,
    @Named("baseUrl") private val baseUrl: String,
) {
    /** Overridable in tests to avoid real multi-second delays. */
    var pollIntervalMs: Long = POLL_INTERVAL_MS
    suspend fun fetchPage(deviceId: Long, beforeId: Long? = null, since: String? = null): Result<MessagePage> = try {
        val response = apiService.listMessages(deviceId, beforeId = beforeId, since = since)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(MessagePage(body.messages, body.next_before_id))
        } else {
            Result.failure(IllegalStateException("Failed to load messages (HTTP ${response.code()})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Emits every new message for [deviceId] exactly once, regardless of
     * whether it arrived via SSE push or the polling fallback.
     */
    fun observeLive(deviceId: Long): Flow<MessageDto> = flow {
        val seenIds = HashSet<Long>()
        var lastCreatedAt: String? = null

        while (currentCoroutineContext().isActive) {
            val accessToken = tokenStore.read()?.accessToken ?: break
            val url = "${baseUrl.trimEnd('/')}/events?device_ids=$deviceId&access_token=$accessToken"

            var streamFailed = false
            try {
                sseClient.stream(url).collect { message ->
                    if (seenIds.add(message.id)) {
                        lastCreatedAt = message.created_at
                        emit(message)
                    }
                }
                // Server closed the stream cleanly - still treat as a
                // disconnect that should fall back to polling and retry.
                streamFailed = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                streamFailed = true
            }

            if (streamFailed && currentCoroutineContext().isActive) {
                pollUntilReconnect(deviceId, seenIds, lastCreatedAt).collect { message ->
                    lastCreatedAt = message.created_at
                    emit(message)
                }
            }
        }
    }

    private fun pollUntilReconnect(
        deviceId: Long,
        seenIds: MutableSet<Long>,
        initialSince: String?,
    ): Flow<MessageDto> = flow {
        var since = initialSince
        var attempts = 0
        while (currentCoroutineContext().isActive && attempts < MAX_POLL_ATTEMPTS_BEFORE_RECONNECT) {
            delay(pollIntervalMs)
            val page = fetchPage(deviceId, since = since).getOrNull()
            page?.messages?.sortedBy { it.id }?.forEach { message ->
                if (seenIds.add(message.id)) {
                    since = message.created_at
                    emit(message)
                }
            }
            attempts++
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val MAX_POLL_ATTEMPTS_BEFORE_RECONNECT = 3
    }
}
