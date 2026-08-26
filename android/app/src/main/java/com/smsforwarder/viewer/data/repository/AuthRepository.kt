package com.smsforwarder.viewer.data.repository

import com.smsforwarder.viewer.data.local.TokenStore
import com.smsforwarder.viewer.data.local.Tokens
import com.smsforwarder.viewer.data.remote.ApiService
import com.smsforwarder.viewer.data.remote.dto.LoginRequest
import com.smsforwarder.viewer.data.remote.dto.LogoutRequest
import javax.inject.Inject
import javax.inject.Singleton

sealed class LoginResult {
    object Success : LoginResult()
    data class Failure(val message: String) : LoginResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenStore: TokenStore,
) {
    fun isLoggedIn(): Boolean = tokenStore.read() != null

    suspend fun login(username: String, password: String): LoginResult {
        return try {
            val response = apiService.login(LoginRequest(username, password))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                tokenStore.save(Tokens(body.access_token, body.refresh_token))
                LoginResult.Success
            } else {
                LoginResult.Failure(errorMessage(response.code()))
            }
        } catch (e: Exception) {
            LoginResult.Failure("Network error: ${e.message ?: "unknown"}")
        }
    }

    suspend fun logout() {
        val refreshToken = tokenStore.read()?.refreshToken
        if (refreshToken != null) {
            try {
                apiService.logout(LogoutRequest(refreshToken))
            } catch (e: Exception) {
                // Best-effort server-side revocation; local session is cleared regardless.
            }
        }
        tokenStore.clear()
    }

    private fun errorMessage(code: Int): String = when (code) {
        401 -> "Invalid username or password"
        else -> "Login failed (HTTP $code)"
    }
}
