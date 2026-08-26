package com.smsforwarder.viewer.ui.serversetup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smsforwarder.viewer.data.local.ServerConfigStore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ServerSetupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun validUrlSavesAndNavigatesAway() {
        val store: ServerConfigStore = mock()
        whenever(store.getUrl()).thenReturn(null)
        var saved = false
        composeRule.setContent {
            ServerSetupScreen(onSaved = { saved = true }, viewModel = ServerSetupViewModel(store))
        }

        composeRule.onNodeWithTag(ServerSetupTestTags.URL_FIELD).performTextInput("https://my-server.example.com")
        composeRule.onNodeWithTag(ServerSetupTestTags.SAVE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { saved }
        verify(store).save("https://my-server.example.com")
    }

    @Test
    fun invalidUrlShowsError() {
        val store: ServerConfigStore = mock()
        whenever(store.getUrl()).thenReturn(null)
        composeRule.setContent {
            ServerSetupScreen(onSaved = {}, viewModel = ServerSetupViewModel(store))
        }

        composeRule.onNodeWithTag(ServerSetupTestTags.URL_FIELD).performTextInput("not-a-url")
        composeRule.onNodeWithTag(ServerSetupTestTags.SAVE_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ServerSetupTestTags.ERROR_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
