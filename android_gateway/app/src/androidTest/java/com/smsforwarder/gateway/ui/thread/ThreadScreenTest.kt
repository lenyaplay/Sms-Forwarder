package com.smsforwarder.gateway.ui.thread

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import org.junit.Rule
import org.junit.Test

class ThreadScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingActions : ThreadActions {
        var draft: String = ""
        var sent = false
        var retriedId: Long? = null
        override fun onDraftChange(value: String) { draft = value }
        override fun onSend() { sent = true }
        override fun onRetry(messageId: Long) { retriedId = messageId }
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
