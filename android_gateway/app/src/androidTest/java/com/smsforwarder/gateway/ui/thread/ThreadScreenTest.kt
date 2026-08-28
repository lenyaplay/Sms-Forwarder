package com.smsforwarder.gateway.ui.thread

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smsforwarder.gateway.data.local.SimOption
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThreadScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingActions : ThreadActions {
        var draft: String = ""
        var sent = false
        var retriedId: Long? = null
        var selectedSubscriptionId: Int? = null
        override fun onDraftChange(value: String) { draft = value }
        override fun onSend() { sent = true }
        override fun onRetry(messageId: Long) { retriedId = messageId }
        override fun onSelectSim(subscriptionId: Int) { selectedSubscriptionId = subscriptionId }
    }

    private fun message(id: Long, direction: MessageDirection, status: DeliveryStatus = DeliveryStatus.SENT) =
        MessageEntity(
            id = id,
            sender = "+15551234",
            text = "hello $id",
            sentStamp = 1L,
            receivedStamp = 1L,
            simSlot = null,
            deliveryStatus = status,
            createdAt = id,
            direction = direction,
        )

    @Test
    fun sendButtonDisabledWhenDraftIsBlank() {
        composeRule.setContent {
            ThreadContent(uiState = ThreadUiState(sender = "+15551234"), actions = RecordingActions())
        }

        composeRule.onNodeWithTag(ThreadTestTags.SEND_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun typingAndSendingInvokesActions() {
        val actions = RecordingActions()
        composeRule.setContent {
            ThreadContent(uiState = ThreadUiState(sender = "+15551234", draft = "hi"), actions = actions)
        }

        composeRule.onNodeWithTag(ThreadTestTags.DRAFT_FIELD).performTextInput("!")
        composeRule.onNodeWithTag(ThreadTestTags.SEND_BUTTON).performClick()

        assert(actions.sent)
    }

    @Test
    fun sendingStateHidesSendButtonAndDisablesDraftField() {
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", draft = "hi", isSending = true),
                actions = RecordingActions(),
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.SEND_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(ThreadTestTags.DRAFT_FIELD).assertIsNotEnabled()
    }

    @Test
    fun simSelectorHiddenWithOneOrNoSims() {
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", availableSims = listOf(SimOption(1, 0, "SIM 1"))),
                actions = RecordingActions(),
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.SIM_SELECTOR).assertDoesNotExist()
    }

    @Test
    fun simSelectorShownAndSwitchableWithTwoSims() {
        val actions = RecordingActions()
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(
                    sender = "+15551234",
                    availableSims = listOf(SimOption(1, 0, "SIM 1"), SimOption(2, 1, "SIM 2")),
                    selectedSubscriptionId = 1,
                ),
                actions = actions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.SIM_SELECTOR).assertExists()
        composeRule.onNodeWithTag(ThreadTestTags.simChip(2)).performClick()

        assertEquals(2, actions.selectedSubscriptionId)
    }

    @Test
    fun retryButtonOnlyShownForFailedMessagesAndInvokesOnRetry() {
        val actions = RecordingActions()
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(
                    sender = "+15551234",
                    messages = listOf(
                        message(1L, MessageDirection.OUT, DeliveryStatus.SENT),
                        message(2L, MessageDirection.OUT, DeliveryStatus.FAILED),
                    ),
                ),
                actions = actions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.retryButton(1L)).assertDoesNotExist()
        composeRule.onNodeWithTag(ThreadTestTags.retryButton(2L)).performClick()

        assert(actions.retriedId == 2L)
    }
}
