package com.smsforwarder.gateway.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TestConnectionResult {
    data class Success(val httpCode: Int) : TestConnectionResult
    data class Failure(val reason: String) : TestConnectionResult
}

/**
 * Sends a real, clearly-marked test POST to a webhook URL - not a bare ping,
 * so it actually validates upload_token, not just network reachability. The
 * backend stores it as an ordinary message (spec 0017, allowance 3) - the
 * "__test__" sender lets the user tell it apart from a real SMS in Viewer App.
 */
@Singleton
open class WebhookConnectionTester @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    open suspend fun test(webhookUrl: String): TestConnectionResult = withContext(Dispatchers.IO) {
        try {
            val payload = WebhookPayload(
                from = "__test__",
                text = "Проверка соединения из Gateway App",
                receivedStamp = System.currentTimeMillis().toString(),
            )
            val body = json.encodeToString(WebhookPayload.serializer(), payload)
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            // .url() throws IllegalArgumentException (not IOException) for a
            // malformed/schemeless URL - e.g. the user typed "example.com"
            // instead of "https://example.com" - so it must stay inside this
            // try, not just the network call, or an unsaved bad address
            // crashes the app instead of reporting Failure.
            val request = Request.Builder().url(webhookUrl).post(body).build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    TestConnectionResult.Success(response.code)
                } else {
                    TestConnectionResult.Failure("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            TestConnectionResult.Failure(e.message ?: "Сетевая ошибка")
        } catch (e: IllegalArgumentException) {
            TestConnectionResult.Failure(e.message ?: "Некорректный адрес сервера")
        }
    }
}
