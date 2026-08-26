package com.smsforwarder.viewer.realbackend

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.smsforwarder.viewer.data.remote.SseClient
import com.smsforwarder.viewer.data.repository.MessageRepository
import com.smsforwarder.viewer.ui.feed.MessageFeedScreen
import com.smsforwarder.viewer.ui.feed.MessageFeedViewModel
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test

/**
 * Proves realtime push (GET /events, spec 0006) actually works end to end,
 * not just the REST fallback: the feed screen is opened FIRST (empty, no
 * message yet), and only then is a message pushed via /webhook - the screen
 * must show it without being reopened/recomposed. See
 * docs/specs/0009-real-backend-integration-tests.md.
 */
class RealBackendSseLiveUpdateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realBackend_messagePushedAfterScreenOpensAppearsLive() {
        val ownerLogin = uniqueLogin("sse-owner")
        val ownerTokens = registerAndLogin(ownerLogin, "owner-password-123")
        val (deviceId, uploadToken) = createDevice(ownerTokens.access_token, "SSE Device ${System.currentTimeMillis()}")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenStore = realTokenStoreLoggedIn(context, ownerTokens)
        val messageRepository = MessageRepository(
            realApiService(tokenStore),
            SseClient(OkHttpClient()),
            tokenStore,
            REAL_BACKEND_BASE_URL,
        )
        val viewModel = MessageFeedViewModel(messageRepository, SavedStateHandle(mapOf("deviceId" to deviceId)))

        composeRule.setContent {
            MessageFeedScreen(deviceName = "SSE Device", viewModel = viewModel)
        }
        // MessageFeedViewModel.init launches loadInitialPage() and
        // startLiveUpdates() in the same breath, so by the time the initial
        // page finishes loading the SSE collector is already running -
        // no arbitrary delay needed before pushing.
        composeRule.waitUntil(timeoutMillis = 10_000) { !viewModel.uiState.value.isLoadingInitial }

        val messageText = "Live SSE message ${System.currentTimeMillis()}"
        postWebhookMessage(uploadToken, from = "+15550002222", text = messageText)

        composeRule.waitUntil(timeoutMillis = 15_000) {
            viewModel.uiState.value.messages.any { it.text == messageText }
        }
        composeRule.onNodeWithText(messageText).assertExists()
    }
}
