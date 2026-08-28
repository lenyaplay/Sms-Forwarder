package com.smsforwarder.gateway.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smsforwarder.gateway.data.local.GatewayConfigStore
import com.smsforwarder.gateway.data.repository.MessageRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockStore(serverUrl: String? = null, uploadToken: String? = null): GatewayConfigStore {
        val store: GatewayConfigStore = mock()
        whenever(store.getServerUrl()).thenReturn(serverUrl)
        whenever(store.getUploadToken()).thenReturn(uploadToken)
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
            SettingsScreen(viewModel = SettingsViewModel(mockStore("https://example.com", "tok-123"), mockRepository()))
        }

        composeRule.onNodeWithText("https://example.com").assertExists()
        composeRule.onNodeWithText("tok-123").assertExists()
    }

    @Test
    fun saveButtonDisabledUntilBothFieldsAreFilled() {
        composeRule.setContent {
            SettingsScreen(viewModel = SettingsViewModel(mockStore(), mockRepository()))
        }

        composeRule.onNodeWithTag(SettingsTestTags.SAVE_BUTTON).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.SERVER_URL_FIELD).performTextInput("https://example.com")
        composeRule.onNodeWithTag(SettingsTestTags.SAVE_BUTTON).performClick()
        // Only one field filled - onSave must be a no-op, so no confirmation appears.
        composeRule.onNodeWithTag(SettingsTestTags.SAVED_CONFIRMATION).assertDoesNotExist()
    }

    @Test
    fun savingPersistsBothFieldsToTheConfigStore() {
        val store = mockStore()
        val repository = mockRepository()
        composeRule.setContent {
            SettingsScreen(viewModel = SettingsViewModel(store, repository))
        }

        composeRule.onNodeWithTag(SettingsTestTags.SERVER_URL_FIELD).performTextInput("https://example.com")
        composeRule.onNodeWithTag(SettingsTestTags.UPLOAD_TOKEN_FIELD).performTextInput("tok-123")
        composeRule.onNodeWithTag(SettingsTestTags.SAVE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SettingsTestTags.SAVED_CONFIRMATION).fetchSemanticsNodes().isNotEmpty()
        }
        verify(store).save("https://example.com", "tok-123")
    }

    @Test
    fun copyButtonCopiesTheUploadTokenToTheClipboard() {
        var clipboardManager: androidx.compose.ui.platform.ClipboardManager? = null
        composeRule.setContent {
            clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            SettingsScreen(viewModel = SettingsViewModel(mockStore("https://example.com", "tok-123"), mockRepository()))
        }

        composeRule.onNodeWithTag(SettingsTestTags.COPY_TOKEN_BUTTON).performClick()

        org.junit.Assert.assertEquals("tok-123", clipboardManager!!.getText()?.text)
    }

    @Test
    fun savingRetriesAnyUndeliveredMessages() {
        val repository = mockRepository()
        composeRule.setContent {
            SettingsScreen(viewModel = SettingsViewModel(mockStore(), repository))
        }

        composeRule.onNodeWithTag(SettingsTestTags.SERVER_URL_FIELD).performTextInput("https://example.com")
        composeRule.onNodeWithTag(SettingsTestTags.UPLOAD_TOKEN_FIELD).performTextInput("tok-123")
        composeRule.onNodeWithTag(SettingsTestTags.SAVE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SettingsTestTags.SAVED_CONFIRMATION).fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking { verify(repository).retryUndeliveredMessages() }
    }
}
