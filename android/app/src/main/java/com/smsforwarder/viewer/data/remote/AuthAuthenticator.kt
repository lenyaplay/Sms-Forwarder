package com.smsforwarder.viewer.data.remote

import com.smsforwarder.viewer.data.local.ServerConfigStore
import com.smsforwarder.viewer.data.local.SessionEvents
import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.local.Tokens
import com.smsforwarder.viewer.data.remote.dto.RefreshRequest
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

/**
 * Handles transparent access-token refresh on a 401. Uses a plain
 * (non-authenticated) OkHttp/Retrofit client to call /auth/refresh, to avoid
 * recursively invoking this same Authenticator.
 */
class AuthAuthenticator(
    private val serverConfigStore: ServerConfigStore,
    private val tokenStore: TokenStore,
    private val sessionEvents: SessionEvents,
) : Authenticator {

    private val json = Json { ignoreUnknownKeys = true }

    // A dummy syntactically-valid baseUrl - the real host is rewritten per
    // request by DynamicBaseUrlInterceptor, reading serverConfigStore fresh
    // each call, not whatever URL was configured when this lazy was first
    // touched (spec 0011 - the same "baked at construction time" bug this
    // whole interceptor exists to avoid).
    private val plainApi: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(OkHttpClient.Builder().addInterceptor(DynamicBaseUrlInterceptor(serverConfigStore)).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    // OkHttp can invoke authenticate() concurrently for multiple in-flight
    // requests that each got a 401. The backend's refresh token is single-use
    // with rotation (spec 0001-auth.md): if two callers raced to call
    // /auth/refresh with the same (stale-after-first-success) token, the
    // second would get 401 and force a spurious logout. Serializing here, and
    // re-checking whether another thread already refreshed the token before
    // calling the server again, avoids that race.
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Only attempt one retry per request chain to avoid infinite loops.
        if (responseCount(response) >= 2) return null

        val failedAccessToken = response.request.header("Authorization")

        synchronized(refreshLock) {
            val stored = tokenStore.read() ?: run {
                sessionEvents.notifyLoggedOut()
                return null
            }

            // Another thread already refreshed while we were waiting on the
            // lock - just retry this request with the now-current access
            // token instead of refreshing a second time.
            if (failedAccessToken != null && failedAccessToken != "Bearer ${stored.accessToken}") {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${stored.accessToken}")
                    .build()
            }

            val newTokens = try {
                kotlinx.coroutines.runBlocking {
                    val result = plainApi.refresh(RefreshRequest(stored.refreshToken))
                    if (!result.isSuccessful) null else result.body()
                }
            } catch (e: Exception) {
                null
            }

            if (newTokens == null) {
                tokenStore.clear()
                sessionEvents.notifyLoggedOut()
                return null
            }

            tokenStore.save(Tokens(newTokens.access_token, newTokens.refresh_token))

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.access_token}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
