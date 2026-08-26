package com.smsforwarder.viewer.realbackend

import android.content.Context
import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.local.Tokens
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.AuthInterceptor
import com.smsforwarder.viewer.data.remote.dto.TokenPairResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Shared setup for the docs/specs/0009-real-backend-integration-tests.md
 * suite: every test here drives a real ApiService/OkHttp stack against a
 * live backend, never a fake ApiService - see the spec for why (two real
 * wire-format/config bugs shipped undetected because every other test
 * substitutes a fake).
 *
 * Requires (see docs/DEVELOPMENT.md for the full command sequence):
 *   cd backend && docker compose up -d
 *   adb reverse tcp:8080 tcp:8080
 *
 * Excluded from the default `connectedAndroidTest` run via the
 * `notPackage` testInstrumentationRunnerArgument in app/build.gradle.kts -
 * run explicitly with:
 *   adb shell am instrument -w -e package com.smsforwarder.viewer.realbackend \
 *     com.smsforwarder.viewer.test/com.smsforwarder.viewer.HiltTestRunner
 */
internal const val REAL_BACKEND_BASE_URL = "http://127.0.0.1:8080/"

private val plainHttpClient = OkHttpClient()
private val json = Json { ignoreUnknownKeys = true }
private val jsonMediaType = "application/json".toMediaType()

@Serializable
private data class DeviceCreateResponse(val id: Long, val upload_token: String)

@Serializable
private data class DownloadTokenCreateResponse(val download_token: String)

/** Unique per test run so parallel/repeated runs never collide on a login. */
internal fun uniqueLogin(scenario: String) = "androidtest-$scenario-${System.currentTimeMillis()}"

/**
 * Registers a fresh user directly over HTTP - not on ApiService, since the
 * app has no registration screen (spec 0007 assumption 9). Each test run
 * must use a unique [login] to avoid colliding with a previous run's user.
 */
internal fun registerTestUser(login: String, password: String) {
    val body = """{"login":"$login","password":"$password"}""".toRequestBody(jsonMediaType)
    val request = Request.Builder().url(REAL_BACKEND_BASE_URL + "auth/register").post(body).build()
    plainHttpClient.newCall(request).execute().use { response ->
        check(response.isSuccessful) {
            "could not register test user (is the backend running at $REAL_BACKEND_BASE_URL ?): " +
                "${response.code} ${response.body?.string()}"
        }
    }
}

/** Registers a fresh user (see [registerTestUser]) and logs in over raw HTTP, returning both tokens. */
internal fun registerAndLogin(login: String, password: String): TokenPairResponse {
    registerTestUser(login, password)

    val body = """{"login":"$login","password":"$password"}""".toRequestBody(jsonMediaType)
    val loginRequest = Request.Builder().url(REAL_BACKEND_BASE_URL + "auth/login").post(body).build()
    plainHttpClient.newCall(loginRequest).execute().use { response ->
        val responseBody = response.body?.string()
        check(response.isSuccessful && responseBody != null) {
            "could not log in test user: ${response.code} $responseBody"
        }
        return json.decodeFromString(TokenPairResponse.serializer(), responseBody)
    }
}

/** Mints a device + upload_token, as the owner would via the (not yet built) admin UI/API directly. */
internal fun createDevice(ownerAccessToken: String, name: String): Pair<Long, String> {
    val body = """{"name":"$name"}""".toRequestBody(jsonMediaType)
    val request = Request.Builder()
        .url(REAL_BACKEND_BASE_URL + "devices")
        .header("Authorization", "Bearer $ownerAccessToken")
        .post(body)
        .build()
    plainHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string()
        check(response.isSuccessful && responseBody != null) {
            "could not create test device: ${response.code} $responseBody"
        }
        val parsed = json.decodeFromString(DeviceCreateResponse.serializer(), responseBody)
        return parsed.id to parsed.upload_token
    }
}

/** Mints a download_token for [deviceId] - only the owner can do this. */
internal fun createDownloadToken(ownerAccessToken: String, deviceId: Long): String {
    val body = "{}".toRequestBody(jsonMediaType)
    val request = Request.Builder()
        .url(REAL_BACKEND_BASE_URL + "devices/$deviceId/download_tokens")
        .header("Authorization", "Bearer $ownerAccessToken")
        .post(body)
        .build()
    plainHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string()
        check(response.isSuccessful && responseBody != null) {
            "could not create download token: ${response.code} $responseBody"
        }
        return json.decodeFromString(DownloadTokenCreateResponse.serializer(), responseBody).download_token
    }
}

/**
 * Emulates the Gateway App pushing a real SMS. [text] should be varied per
 * call (e.g. include a timestamp/counter) - identical bodies are deduped by
 * the backend and would return 200 instead of a fresh 201.
 */
internal fun postWebhookMessage(uploadToken: String, from: String, text: String) {
    val body = """{"from":"$from","text":"$text"}""".toRequestBody(jsonMediaType)
    val request = Request.Builder()
        .url("${REAL_BACKEND_BASE_URL}webhook?upload_token=$uploadToken")
        .post(body)
        .build()
    plainHttpClient.newCall(request).execute().use { response ->
        check(response.isSuccessful) {
            "could not post webhook message: ${response.code} ${response.body?.string()}"
        }
    }
}

/**
 * Builds a real Retrofit-backed ApiService - no fake, exercises real
 * kotlinx.serialization + OkHttp. Pass [tokenStore] for any call that needs
 * auth (everything except auth/register and auth/login) so requests carry a
 * real `Authorization: Bearer` header via the app's own AuthInterceptor.
 */
internal fun realApiService(tokenStore: TokenStore? = null): ApiService {
    val client = if (tokenStore != null) {
        OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokenStore)).build()
    } else {
        plainHttpClient
    }
    val retrofit = Retrofit.Builder()
        .baseUrl(REAL_BACKEND_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(jsonMediaType))
        .build()
    return retrofit.create(ApiService::class.java)
}

/** Clears any prior session and stores [tokens] as if the user had just logged in via the app. */
internal fun realTokenStoreLoggedIn(context: Context, tokens: TokenPairResponse): TokenStore {
    val tokenStore = TokenStore(context)
    tokenStore.clear()
    tokenStore.save(Tokens(accessToken = tokens.access_token, refreshToken = tokens.refresh_token))
    return tokenStore
}
