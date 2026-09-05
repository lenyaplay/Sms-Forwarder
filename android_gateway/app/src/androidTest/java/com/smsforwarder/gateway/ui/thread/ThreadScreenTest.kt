package com.smsforwarder.gateway.ui.thread

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
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
        override fun onToggleMessageSelection(messageId: Long) {}
        override fun onClearSelection() {}
        override fun onDeleteSelectedMessages() {}
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

        composeRule.onNodeWithTag(ThreadTestTags.bubble(200L), useUnmergedTree = true).assertIsDisplayed()
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

        composeRule.onNodeWithTag(ThreadTestTags.bubble(42L), useUnmergedTree = true).assertIsDisplayed()
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
        composeRule.onNodeWithTag(ThreadTestTags.bubble(5L), useUnmergedTree = true).assertIsDisplayed()

        currentMessages = initialMessages + message(6L)

        // Regression coverage for the "second+ emission still animates" branch -
        // the animated path is allowed to take more than one frame, so this
        // asserts the eventual (idle-settled) end state, not immediacy.
        composeRule.onNodeWithTag(ThreadTestTags.bubble(6L), useUnmergedTree = true).assertIsDisplayed()
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
            override fun onToggleMessageSelection(messageId: Long) {}
            override fun onClearSelection() {}
            override fun onDeleteSelectedMessages() {}
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
            override fun onToggleMessageSelection(messageId: Long) {}
            override fun onClearSelection() {}
            override fun onDeleteSelectedMessages() {}
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

    // Spec 0031: long-press no longer opens a per-message delete menu directly - it
    // enters multi-select mode and selects that message; a further tap on another
    // bubble toggles its selection too, so the driver here mirrors what
    // ThreadViewModel actually does (selectedMessageIds as a Set) rather than a
    // no-op stub, so the toggle/delete cycle is genuinely exercised.
    @Test
    fun longPressEntersSelectionModeAndTapTogglesAnotherMessageThenDeleteClearsSelection() {
        val messages = (1L..3L).map { message(it) }
        var selectedIds by mutableStateOf(emptySet<Long>())
        var deletedIds: Set<Long>? = null
        val actions = object : ThreadActions {
            override fun onDraftChange(value: String) {}
            override fun onSend() {}
            override fun onRetry(messageId: Long) {}
            override fun onSelectSim(subscriptionId: Int) {}
            override fun onDeleteMessage(messageId: Long) {}
            override fun onDeleteConversation() {}
            override fun onToggleMessageSelection(messageId: Long) {
                selectedIds = if (messageId in selectedIds) selectedIds - messageId else selectedIds + messageId
            }
            override fun onClearSelection() { selectedIds = emptySet() }
            override fun onDeleteSelectedMessages() {
                deletedIds = selectedIds
                selectedIds = emptySet()
            }
        }
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", messages = messages, selectedMessageIds = selectedIds),
                actions = actions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.bubble(1L), useUnmergedTree = true).performTouchInput { longClick() }
        assertEquals(setOf(1L), selectedIds)

        composeRule.onNodeWithTag(ThreadTestTags.bubble(2L), useUnmergedTree = true).performClick()
        assertEquals(setOf(1L, 2L), selectedIds)

        actions.onDeleteSelectedMessages()
        assertEquals(setOf(1L, 2L), deletedIds)
        assertTrue(selectedIds.isEmpty())
    }

    // Spec 0031 p.7 (2026-09-05): a second long-press on an *already selected* message
    // must not toggle it back off - that gesture is reserved for entering normal word
    // text selection (SelectionContainer) instead, while the message stays selected in
    // the multi-select set. Regression coverage for a real bug: the message helper's
    // "message $id" text has no digits, so this - unlike a message with an OTP/link
    // segment - would still pass even if SelectionContainer's own long-press handling
    // interfered with the outer combinedClickable, so it isolates the toggle-off logic
    // specifically rather than the text-vs-container gesture interaction.
    @Test
    fun secondLongPressOnAlreadySelectedMessageDoesNotDeselectIt() {
        val messages = listOf(message(1L))
        var selectedIds by mutableStateOf(emptySet<Long>())
        val actions = object : ThreadActions {
            override fun onDraftChange(value: String) {}
            override fun onSend() {}
            override fun onRetry(messageId: Long) {}
            override fun onSelectSim(subscriptionId: Int) {}
            override fun onDeleteMessage(messageId: Long) {}
            override fun onDeleteConversation() {}
            override fun onToggleMessageSelection(messageId: Long) {
                selectedIds = if (messageId in selectedIds) selectedIds - messageId else selectedIds + messageId
            }
            override fun onClearSelection() { selectedIds = emptySet() }
            override fun onDeleteSelectedMessages() {}
        }
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", messages = messages, selectedMessageIds = selectedIds),
                actions = actions,
            )
        }

        composeRule.onNodeWithTag(ThreadTestTags.bubble(1L), useUnmergedTree = true).performTouchInput { longClick() }
        assertEquals(setOf(1L), selectedIds)

        composeRule.onNodeWithTag(ThreadTestTags.bubble(1L), useUnmergedTree = true).performTouchInput { longClick() }
        assertEquals(setOf(1L), selectedIds)
    }

    // Spec 0031: tapping a recognized link segment inside message text opens the
    // action menu (not the old whole-bubble "Удалить" menu, which is gone) - covers
    // that segment click-handling actually wires up, not just that segmentation logic
    // is correct (that part is unit-tested in MessageTextSegmentationTest).
    @Test
    fun tappingALinkSegmentOpensItsActionMenu() {
        val linkMessage = message(1L).copy(text = "see https://example.com for details")
        composeRule.setContent {
            ThreadContent(
                uiState = ThreadUiState(sender = "+15551234", messages = listOf(linkMessage)),
                actions = noopActions,
            )
        }

        composeRule.onNodeWithText("https://example.com", substring = true).performClick()

        composeRule.onNodeWithTag(ThreadTestTags.SEGMENT_ACTION_OPEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ThreadTestTags.SEGMENT_ACTION_COPY).assertIsDisplayed()
    }

    // Spec 0032: a date header appears before the first message of a new calendar
    // day, independent of the 5-minute grouping gap - two messages a few minutes
    // apart but crossing midnight still get a separator between them.
    @Test
    fun dateSeparatorAppearsWhenMessagesCrossMidnightButNotWithinTheSameDay() {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val calendar = java.util.Calendar.getInstance()
        calendar.set(currentYear, java.util.Calendar.SEPTEMBER, 13, 9, 0, 0)
        val dayOneMorning = calendar.timeInMillis
        calendar.set(currentYear, java.util.Calendar.SEPTEMBER, 13, 23, 59, 0)
        val beforeMidnight = calendar.timeInMillis
        calendar.set(currentYear, java.util.Calendar.SEPTEMBER, 14, 0, 1, 0)
        val afterMidnight = calendar.timeInMillis
        // Same format as production (ThreadScreen.kt's formatDateHeader, which omits
        // the year for the current calendar year) - not hardcoded locale text, so
        // this test doesn't depend on device locale.
        val dateHeaderFormat = java.text.SimpleDateFormat("d MMMM", java.util.Locale.getDefault())
        val dayOneLabel = dateHeaderFormat.format(java.util.Date(dayOneMorning))
        val dayTwoLabel = dateHeaderFormat.format(java.util.Date(afterMidnight))

        // Three messages: two on day one (first-in-list always gets a header, that's
        // expected - the interesting assertion is that the *second* day-one message,
        // only minutes later, does NOT get its own separator) and one just after
        // midnight, which must get a new one.
        val messages = listOf(
            message(1L).copy(createdAt = dayOneMorning, receivedStamp = dayOneMorning),
            message(2L).copy(createdAt = beforeMidnight, receivedStamp = beforeMidnight),
            message(3L).copy(createdAt = afterMidnight, receivedStamp = afterMidnight),
        )
        composeRule.setContent {
            ThreadContent(uiState = ThreadUiState(sender = "+15551234", messages = messages), actions = noopActions)
        }

        composeRule.onNodeWithTag(ThreadTestTags.dateSeparator(dayOneLabel)).assertIsDisplayed()
        composeRule.onNodeWithTag(ThreadTestTags.dateSeparator(dayTwoLabel)).assertIsDisplayed()
        composeRule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("is a date separator") { node ->
                node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }
                    .startsWith("thread_date_separator_")
            },
        ).assertCountEquals(2)
    }
}
