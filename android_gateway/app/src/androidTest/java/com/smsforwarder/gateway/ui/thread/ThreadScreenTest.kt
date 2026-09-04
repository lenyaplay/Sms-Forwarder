package com.smsforwarder.gateway.ui.thread

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.smsforwarder.gateway.data.local.SimOption
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThreadScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun message(id: Long, simSlot: Int? = 0) = MessageEntity(
        id = id,
        sender = "+15551234",
        text = "message $id",
        sentStamp = null,
        receivedStamp = id,
        simSlot = simSlot,
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

    // Spec 0028: send button is now an icon (Icons.AutoMirrored.Filled.Send) inside a
    // circular FilledIconButton, not a text Button - covers what a Compose test can
    // actually assert (testTag/click/enabled state/touch target size), since Compose's
    // test APIs have no way to read back the drawn shape/color without a snapshot
    // testing framework (not present in this project - documented in spec 0028 as a
    // known limitation, verified instead by code review + live device check).
    @Test
    fun sendButtonIsAtLeastFortyEightDpAndInvokesOnSendWhenEnabled() {
        var sendCount = 0
        val actions = object : ThreadActions {
            override fun onDraftChange(value: String) {}
            override fun onSend() { sendCount++ }
            override fun onRetry(messageId: Long) {}
            override fun onSelectSim(subscriptionId: Int) {}
            override fun onDeleteMessage(messageId: Long) {}
            override fun onDeleteConversation() {}
        }
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", draft = "hello", messages = emptyList()),
                actions = actions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.SEND_BUTTON)
            .assertIsEnabled()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertTrue(sendCount == 1)
    }

    @Test
    fun sendButtonIsDisabledWhenDraftIsBlank() {
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", draft = "", messages = emptyList()),
                actions = noopActions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.SEND_BUTTON).assertIsNotEnabled()
    }

    // Replaces the old FilterChip row (which read on-device as a solid dark bar over
    // the last messages, per product owner live feedback) with a compact trailing icon
    // inside the draft field's own trailingIcon slot - covers that the icon opens a
    // popup with one item per SIM and that picking one delegates to onSelectSim.
    @Test
    fun simSelectorIconOpensMenuAndSelectingASimDelegatesToOnSelectSim() {
        var selected: Int? = null
        val actions = object : ThreadActions {
            override fun onDraftChange(value: String) {}
            override fun onSend() {}
            override fun onRetry(messageId: Long) {}
            override fun onSelectSim(subscriptionId: Int) { selected = subscriptionId }
            override fun onDeleteMessage(messageId: Long) {}
            override fun onDeleteConversation() {}
        }
        val sims = listOf(
            SimOption(subscriptionId = 1, slotIndex = 0, displayName = "Tinkoff"),
            SimOption(subscriptionId = 2, slotIndex = 1, displayName = "YOTA"),
        )
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(
                    sender = "+15551234",
                    messages = emptyList(),
                    availableSims = sims,
                    selectedSubscriptionId = 1,
                ),
                actions = actions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.SIM_SELECTOR).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(ThreadTestTags.simMenuItem(2)).assertIsDisplayed().performClick()

        assertEquals(2, selected)
    }

    @Test
    fun simSelectorIsNotShownWithOnlyOneSim() {
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(
                    sender = "+15551234",
                    messages = emptyList(),
                    availableSims = listOf(SimOption(subscriptionId = 1, slotIndex = 0, displayName = "Tinkoff")),
                    selectedSubscriptionId = 1,
                ),
                actions = noopActions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.SIM_SELECTOR).assertDoesNotExist()
    }

    // Spec 0029: the SIM label next to a message's timestamp only shows up when there
    // are 2+ active SIMs (same visibility rule as the draft field's SIM selector) AND
    // the message itself carries a resolved simSlot - older, pre-fix history stays null.
    @Test
    fun simIndicatorIsShownOnAMessageWithAKnownSimSlotWhenMultipleSimsAreActive() {
        val sims = listOf(
            SimOption(subscriptionId = 1, slotIndex = 0, displayName = "Tinkoff"),
            SimOption(subscriptionId = 2, slotIndex = 1, displayName = "YOTA"),
        )
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(
                    sender = "+15551234",
                    messages = listOf(message(1L, simSlot = 1)),
                    availableSims = sims,
                    selectedSubscriptionId = 1,
                ),
                actions = noopActions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.simIndicator(1L), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun simIndicatorIsHiddenWhenTheMessageHasNoKnownSimSlot() {
        val sims = listOf(
            SimOption(subscriptionId = 1, slotIndex = 0, displayName = "Tinkoff"),
            SimOption(subscriptionId = 2, slotIndex = 1, displayName = "YOTA"),
        )
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(
                    sender = "+15551234",
                    messages = listOf(message(1L, simSlot = null)),
                    availableSims = sims,
                    selectedSubscriptionId = 1,
                ),
                actions = noopActions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.simIndicator(1L)).assertDoesNotExist()
    }

    @Test
    fun simIndicatorIsHiddenWithOnlyOneSimEvenWhenSimSlotIsKnown() {
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", messages = listOf(message(1L, simSlot = 0))),
                actions = noopActions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.simIndicator(1L)).assertDoesNotExist()
    }

    // Spec 0029: scrolling the message history upward hides the keyboard/clears focus
    // from the draft field, like most messengers - focus itself (rather than IME
    // visibility, which Compose's test APIs can't observe directly) is what's asserted.
    @Test
    fun scrollingTheMessageListUpwardClearsFocusFromTheDraftField() {
        val messages = (1L..50L).map { message(it) }
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", messages = messages, scrollToMessageId = null),
                actions = noopActions,
            )
        }
        composeRule.onNodeWithTag(ThreadTestTags.DRAFT_FIELD).performClick()
        composeRule.onNodeWithTag(ThreadTestTags.DRAFT_FIELD).assertIsFocused()

        composeRule.onNodeWithTag(ThreadTestTags.LIST).performScrollToIndex(0)

        composeRule.onNodeWithTag(ThreadTestTags.DRAFT_FIELD).assertIsNotFocused()
    }
}
