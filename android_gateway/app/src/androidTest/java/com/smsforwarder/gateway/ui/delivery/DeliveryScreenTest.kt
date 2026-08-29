package com.smsforwarder.gateway.ui.delivery

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.repository.MessageRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DeliveryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockStore(
        serverUrl: String? = null,
        uploadToken: String? = null,
        maxAttempts: Int = 10,
        baseIntervalSeconds: Long = 30L,
        backoffPolicy: androidx.work.BackoffPolicy = androidx.work.BackoffPolicy.EXPONENTIAL,
    ): GatewayConfigStore {
        val store: GatewayConfigStore = mock()
        whenever(store.getServerUrl()).thenReturn(serverUrl)
        whenever(store.getUploadToken()).thenReturn(uploadToken)
        whenever(store.retryMaxAttempts()).thenReturn(maxAttempts)
        whenever(store.retryBaseIntervalSeconds()).thenReturn(baseIntervalSeconds)
        whenever(store.retryBackoffPolicy()).thenReturn(backoffPolicy)
        return store
    }

    private fun mockRepository(): MessageRepository {
        val repository: MessageRepository = mock()
        runBlocking { whenever(repository.retryUndeliveredMessages()).thenReturn(Unit) }
        return repository
    }

    @Test
    fun displaysPreviouslySavedValues() {
        composeRule.setContent {
            DeliveryScreen(
                viewModel = DeliveryViewModel(mockStore("https://example.com", "tok-123"), mockRepository()),
                onBack = {},
            )
        }

        composeRule.onNodeWithText("https://example.com").assertExists()
        composeRule.onNodeWithText("tok-123").assertExists()
        composeRule.onNodeWithText("10").assertExists()
        composeRule.onNodeWithText("30").assertExists()
    }

    @Test
    fun saveButtonDisabledWhenMaxAttemptsOutOfRange() {
        composeRule.setContent {
            DeliveryScreen(
                viewModel = DeliveryViewModel(mockStore("https://example.com", "tok-123"), mockRepository()),
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(DeliveryTestTags.MAX_ATTEMPTS_FIELD).performTextClearance()
        composeRule.onNodeWithTag(DeliveryTestTags.MAX_ATTEMPTS_FIELD).performTextInput("51")

        composeRule.onNodeWithTag(DeliveryTestTags.MAX_ATTEMPTS_ERROR).assertExists()
        composeRule.onNodeWithTag(DeliveryTestTags.SAVE_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun saveButtonDisabledWhenBaseIntervalOutOfRange() {
        composeRule.setContent {
            DeliveryScreen(
                viewModel = DeliveryViewModel(mockStore("https://example.com", "tok-123"), mockRepository()),
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(DeliveryTestTags.BASE_INTERVAL_FIELD).performTextClearance()
        composeRule.onNodeWithTag(DeliveryTestTags.BASE_INTERVAL_FIELD).performTextInput("5")

        composeRule.onNodeWithTag(DeliveryTestTags.BASE_INTERVAL_ERROR).assertExists()
        composeRule.onNodeWithTag(DeliveryTestTags.SAVE_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun savingPersistsRetryConfigToTheConfigStore() {
        val store = mockStore("https://example.com", "tok-123")
        val repository = mockRepository()
        composeRule.setContent {
            DeliveryScreen(viewModel = DeliveryViewModel(store, repository), onBack = {})
        }

        composeRule.onNodeWithTag(DeliveryTestTags.MAX_ATTEMPTS_FIELD).performTextClearance()
        composeRule.onNodeWithTag(DeliveryTestTags.MAX_ATTEMPTS_FIELD).performTextInput("5")
        composeRule.onNodeWithTag(DeliveryTestTags.BASE_INTERVAL_FIELD).performTextClearance()
        composeRule.onNodeWithTag(DeliveryTestTags.BASE_INTERVAL_FIELD).performTextInput("60")
        composeRule.onNodeWithTag(DeliveryTestTags.BACKOFF_LINEAR).performClick()
        composeRule.onNodeWithTag(DeliveryTestTags.SAVE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DeliveryTestTags.SAVED_CONFIRMATION).fetchSemanticsNodes().isNotEmpty()
        }
        verify(store).setRetryMaxAttempts(5)
        verify(store).setRetryBaseIntervalSeconds(60L)
        verify(store).setRetryBackoffPolicy(androidx.work.BackoffPolicy.LINEAR)
    }
}
