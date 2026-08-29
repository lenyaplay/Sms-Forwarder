package com.smsforwarder.gateway.ui.thread

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import org.junit.Rule
import org.junit.Test

class ThreadScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun message(id: Long) = MessageEntity(
        id = id,
        sender = "+15551234",
        text = "message $id",
        sentStamp = null,
        receivedStamp = id,
        simSlot = 0,
        deliveryStatus = DeliveryStatus.SENT,
        createdAt = id,
        direction = MessageDirection.IN,
    )

    private val noopActions = object : ThreadActions {
        override fun onDraftChange(value: String) {}
        override fun onSend() {}
        override fun onRetry(messageId: Long) {}
        override fun onSelectSim(subscriptionId: Int) {}
        override fun onDeleteMessage(messageId: Long) {}
        override fun onDeleteConversation() {}
    }

    @Test
    fun openingWithNoTargetMessageShowsTheLastMessageImmediatelyWithNoAnimationFrames() {
        val messages = (1L..200L).map { message(it) }
        // Freeze the clock so an animateScrollToItem regression (which needs
        // several frames to reach index 199 from 0) would leave the target
        // bubble NOT displayed yet at this point - only an instant scrollToItem
        // reaches it within the single frame this composition/effect resolves in.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", messages = messages, scrollToMessageId = null),
                actions = noopActions,
            )
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(ThreadTestTags.bubble(200L)).assertIsDisplayed()
    }

    @Test
    fun openingWithATargetMessageShowsThatMessageNotTheLastOneWithNoAnimationFrames() {
        val messages = (1L..200L).map { message(it) }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", messages = messages, scrollToMessageId = 42L),
                actions = noopActions,
            )
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(ThreadTestTags.bubble(42L)).assertIsDisplayed()
    }

    @Test
    fun newMessageArrivingInAnAlreadyOpenThreadStillAnimatesToTheEnd() {
        val initialMessages = (1L..5L).map { message(it) }
        var currentMessages by mutableStateOf(initialMessages)
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", messages = currentMessages, scrollToMessageId = null),
                actions = noopActions,
            )
        }
        composeRule.onNodeWithTag(ThreadTestTags.bubble(5L)).assertIsDisplayed()

        currentMessages = initialMessages + message(6L)

        // Regression coverage for the "second+ emission still animates" branch -
        // the animated path is allowed to take more than one frame, so this
        // asserts the eventual (idle-settled) end state, not immediacy.
        composeRule.onNodeWithTag(ThreadTestTags.bubble(6L)).assertIsDisplayed()
    }
}
