package com.smsforwarder.gateway.ui.filters

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smsforwarder.gateway.data.local.db.FilterStage
import org.junit.Rule
import org.junit.Test

class FilterRuleEditScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingActions : FilterRuleEditActions {
        var senderPattern: String = ""
        var senderIsRegex: Boolean? = null
        var subscriptionId: Int? = null
        var contentPattern: String = ""
        var contentIsRegex: Boolean? = null
        var enabled: Boolean? = null
        var saved = false
        override fun onSenderPatternChange(value: String) { senderPattern = value }
        override fun onSenderIsRegexChange(value: Boolean) { senderIsRegex = value }
        override fun onSubscriptionIdChange(value: Int?) { subscriptionId = value }
        override fun onContentPatternChange(value: String) { contentPattern = value }
        override fun onContentIsRegexChange(value: Boolean) { contentIsRegex = value }
        override fun onEnabledChange(value: Boolean) { enabled = value }
        override fun onSave() { saved = true }
    }

    @Test
    fun savingValidRuleInvokesOnSave() {
        val actions = RecordingActions()
        composeRule.setContent {
            FilterRuleEditContent(
                uiState = FilterRuleEditUiState(stage = FilterStage.RECEPTION, senderPattern = "Bank"),
                actions = actions,
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(FilterRuleEditTestTags.SAVE_BUTTON).performClick()

        assert(actions.saved)
    }

    @Test
    fun invalidRegexDisablesSaveAndShowsError() {
        composeRule.setContent {
            FilterRuleEditContent(
                uiState = FilterRuleEditUiState(
                    stage = FilterStage.RECEPTION,
                    senderPattern = "[unclosed",
                    senderIsRegex = true,
                ),
                actions = RecordingActions(),
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(FilterRuleEditTestTags.SENDER_ERROR).assertExists()
        composeRule.onNodeWithTag(FilterRuleEditTestTags.SAVE_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun typingIntoSenderFieldInvokesOnSenderPatternChange() {
        val actions = RecordingActions()
        composeRule.setContent {
            FilterRuleEditContent(
                uiState = FilterRuleEditUiState(stage = FilterStage.RECEPTION),
                actions = actions,
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(FilterRuleEditTestTags.SENDER_FIELD).performTextInput("Bank")

        assert(actions.senderPattern == "Bank")
    }
}
