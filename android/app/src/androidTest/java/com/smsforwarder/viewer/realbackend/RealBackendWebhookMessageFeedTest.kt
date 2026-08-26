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
 * Drives MessageFeedScreen against a real backend: a message is pushed via
 * the real /webhook route (as the Gateway App would) BEFORE the screen opens,
 * then the screen's initial REST page load must surface it through the real
 * ApiService/kotlinx.serialization stack. See
 * docs/specs/0009-real-backend-integration-tests.md.
 */
class RealBackendWebhookMessageFeedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realBackend_webhookMessageAppearsInFeed() {
        val ownerLogin = uniqueLogin("feed-owner")
        val ownerTokens = registerAndLogin(ownerLogin, "owner-password-123")
        val (deviceId, uploadToken) = createDevice(ownerTokens.access_token, "Feed Device ${System.currentTimeMillis()}")

        val messageText = "Real backend test message ${System.currentTimeMillis()}"
        postWebhookMessage(uploadToken, from = "+15550001111", text = messageText)

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
            MessageFeedScreen(deviceName = "Feed Device", viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.messages.any { it.text == messageText }
        }
        composeRule.onNodeWithText(messageText).assertExists()
    }
}
