package com.smsforwarder.gateway.realbackend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Shared setup for this app's real-backend suite, mirroring
 * android/.../realbackend/RealBackendTestSupport.kt (spec 0009) - a real
 * OkHttp stack against a live backend, never a fake. Requires:
 *   cd backend && docker compose up -d
 *   adb reverse tcp:8080 tcp:8080
 *
 * Excluded from the default connectedAndroidTest run via the notPackage
 * testInstrumentationRunnerArgument in app/build.gradle.kts.
 */
internal const val REAL_BACKEND_BASE_URL = "http://127.0.0.1:8080/"

private val plainHttpClient = OkHttpClient()
private val json = Json { ignoreUnknownKeys = true }
private val jsonMediaType = "application/json".toMediaType()

@Serializable
private data class DeviceCreateResponse(val id: Long, val upload_token: String)

@Serializable
private data class TokenPairResponse(val access_token: String, val refresh_token: String)

internal fun uniqueLogin(scenario: String) = "androidtest-gateway-$scenario-${System.currentTimeMillis()}"

internal fun registerAndLoginOwner(login: String, password: String): String {
    val registerBody = """{"login":"$login","password":"$password"}""".toRequestBody(jsonMediaType)
    val registerRequest = Request.Builder().url(REAL_BACKEND_BASE_URL + "auth/register").post(registerBody).build()
    plainHttpClient.newCall(registerRequest).execute().use { response ->
        check(response.isSuccessful) {
            "could not register test user (is the backend running at $REAL_BACKEND_BASE_URL ?): " +
                "${response.code} ${response.body?.string()}"
        }
    }

    val loginBody = """{"login":"$login","password":"$password"}""".toRequestBody(jsonMediaType)
    val loginRequest = Request.Builder().url(REAL_BACKEND_BASE_URL + "auth/login").post(loginBody).build()
    plainHttpClient.newCall(loginRequest).execute().use { response ->
        val responseBody = response.body?.string()
        check(response.isSuccessful && responseBody != null) { "could not log in test user: ${response.code} $responseBody" }
        return json.decodeFromString(TokenPairResponse.serializer(), responseBody).access_token
    }
}

/** Mints a device + upload_token, as the owner would via the admin UI/API. */
internal fun createDevice(ownerAccessToken: String, name: String): Pair<Long, String> {
    val body = """{"name":"$name"}""".toRequestBody(jsonMediaType)
    val request = Request.Builder()
        .url(REAL_BACKEND_BASE_URL + "devices")
        .header("Authorization", "Bearer $ownerAccessToken")
        .post(body)
        .build()
    plainHttpClient.newCall(request).execute().use { response ->
        val responseBody = response.body?.string()
        check(response.isSuccessful && responseBody != null) { "could not create test device: ${response.code} $responseBody" }
        val parsed = json.decodeFromString(DeviceCreateResponse.serializer(), responseBody)
        return parsed.id to parsed.upload_token
    }
}
