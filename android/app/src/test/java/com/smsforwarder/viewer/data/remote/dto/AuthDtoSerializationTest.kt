package com.smsforwarder.viewer.data.remote.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Encodes request DTOs to real JSON and checks the wire field names against
 * the backend's contract (backend/internal/handlers/auth_handlers.go).
 * Existing repository/ViewModel tests all use a fake ApiService implementing
 * the interface directly, so they never exercise kotlinx.serialization - a
 * field-name mismatch (LoginRequest.username vs the backend's expected
 * "login") shipped and was only caught by a real device against a real
 * backend. This test would have caught it without needing either.
 */
class AuthDtoSerializationTest {

    @Test
    fun loginRequestSerializesLoginFieldNotUsername() {
        val json = Json.encodeToString(LoginRequest.serializer(), LoginRequest("alice", "hunter2"))
        assertEquals("""{"login":"alice","password":"hunter2"}""", json)
    }

    @Test
    fun refreshRequestSerializesRefreshTokenField() {
        val json = Json.encodeToString(RefreshRequest.serializer(), RefreshRequest("r-token"))
        assertEquals("""{"refresh_token":"r-token"}""", json)
    }

    @Test
    fun logoutRequestSerializesRefreshTokenField() {
        val json = Json.encodeToString(LogoutRequest.serializer(), LogoutRequest("r-token"))
        assertEquals("""{"refresh_token":"r-token"}""", json)
    }

    @Test
    fun tokenPairResponseDeserializesBackendFieldNames() {
        val decoded = Json.decodeFromString<TokenPairResponse>(
            """{"access_token":"a","refresh_token":"r"}""",
        )
        assertEquals(TokenPairResponse("a", "r"), decoded)
    }
}
