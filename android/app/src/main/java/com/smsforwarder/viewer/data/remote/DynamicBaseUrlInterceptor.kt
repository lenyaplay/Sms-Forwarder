package com.smsforwarder.viewer.data.remote

import android.util.Log
import com.smsforwarder.viewer.data.local.ServerConfigStore
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response

/**
 * Rewrites every request's scheme/host/port to whatever ServerConfigStore
 * currently holds, read fresh on each call - not the value baked into
 * Retrofit's baseUrl() at Hilt singleton construction time.
 *
 * Without this, OkHttpClient/Retrofit are built once (eagerly, at
 * MainActivity's field injection, before the user ever sees the "Server
 * setup" screen) and permanently keep whatever baseUrl existed at that
 * moment - saving a new URL later never took effect without killing and
 * restarting the whole app process (spec 0011: this is the real fix,
 * superseding spec 0010 assumption 2's "log out and back in is enough",
 * which turned out not to be true since the singleton network stack
 * outlives navigation within the same process).
 */
class DynamicBaseUrlInterceptor(private val serverConfigStore: ServerConfigStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val configuredUrl = serverConfigStore.getUrl()?.toHttpUrlOrNull()
        if (configuredUrl == null) {
            Log.w(TAG, "no server URL configured yet, request to ${request.url} will use the fallback host")
            return chain.proceed(request)
        }

        val rewritten = request.url.newBuilder()
            .scheme(configuredUrl.scheme)
            .host(configuredUrl.host)
            .port(configuredUrl.port)
            .build()
        Log.d(TAG, "${request.method} ${request.url} -> $rewritten")

        return chain.proceed(request.newBuilder().url(rewritten).build())
    }

    private companion object {
        const val TAG = "NetworkDebug"
    }
}
