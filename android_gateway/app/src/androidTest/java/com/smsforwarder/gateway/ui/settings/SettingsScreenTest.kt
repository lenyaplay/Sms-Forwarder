package com.smsforwarder.gateway.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smsforwarder.gateway.data.local.GatewayConfigStore
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

    @Test
    fun displaysPreviouslySavedValues() {
        composeRule.setContent {
            SettingsScreen(viewModel = SettingsViewModel(mockStore("https://example.com", "tok-123")))
        }

        composeRule.onNodeWithText("https://example.com").assertExists()
        composeRule.onNodeWithText("tok-123").assertExists()
    }

    @Test
    fun saveButtonDisabledUntilBothFieldsAreFilled() {
        composeRule.setContent {
            SettingsScreen(viewModel = SettingsViewModel(mockStore()))
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
        composeRule.setContent {
            SettingsScreen(viewModel = SettingsViewModel(store))
        }

        composeRule.onNodeWithTag(SettingsTestTags.SERVER_URL_FIELD).performTextInput("https://example.com")
        composeRule.onNodeWithTag(SettingsTestTags.UPLOAD_TOKEN_FIELD).performTextInput("tok-123")
        composeRule.onNodeWithTag(SettingsTestTags.SAVE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SettingsTestTags.SAVED_CONFIRMATION).fetchSemanticsNodes().isNotEmpty()
        }
        verify(store).save("https://example.com", "tok-123")
    }
}
