package com.smsforwarder.gateway.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// Spec 0036: covers the gap flagged in Metrics.md - the reveal/close thresholds of
// SwipeActionsRow (replacing Material3's SwipeToDismissBox) had never been exercised
// by an actual touch gesture, only via the non-gesture button equivalents.
class SwipeActionsRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var deleteClicks = 0

    private fun setContent() {
        deleteClicks = 0
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(300.dp).height(56.dp)) {
                    SwipeActionsRow(
                        actions = listOf(
                            SwipeAction(
                                icon = Icons.Default.Delete,
                                contentDescription = "Удалить",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                testTag = "swipe_delete",
                                onClick = { deleteClicks++ },
                            ),
                        ),
                    ) {
                        Box(modifier = Modifier.testTag("content").width(300.dp).height(56.dp)) {
                            Text("Row content")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun swipeBelowThreshold_doesNotRevealButton() {
        setContent()
        composeRule.onNodeWithTag("content").performTouchInput {
            down(Offset(280f, 28f))
            moveTo(Offset(275f, 28f))
            up()
        }
        composeRule.onNodeWithTag("swipe_delete").assertDoesNotExist()
    }

    @Test
    fun swipeLeftPastThreshold_revealsButton() {
        setContent()
        composeRule.onNodeWithTag("content").performTouchInput {
            down(Offset(280f, 28f))
            moveTo(Offset(-1000f, 28f))
            up()
        }
        composeRule.onNodeWithTag("swipe_delete").assertIsDisplayed()
    }

    @Test
    fun nearVerticalDrag_doesNotRevealButtonEvenWithLargeHorizontalDelta() {
        setContent()
        composeRule.onNodeWithTag("content").performTouchInput {
            down(Offset(280f, 28f))
            // Large horizontal component too (would cross the reveal threshold on
            // its own), but the vertical component makes the overall angle from
            // the down point exceed maxAngleDegrees (30 by default) - must be
            // rejected as a scroll/gesture noise, not a swipe.
            moveTo(Offset(-1000f, 3000f))
            up()
        }
        composeRule.onNodeWithTag("swipe_delete").assertDoesNotExist()
    }

    @Test
    fun tappingRevealedButton_invokesCallback() {
        setContent()
        composeRule.onNodeWithTag("content").performTouchInput {
            down(Offset(280f, 28f))
            moveTo(Offset(-1000f, 28f))
            up()
        }
        composeRule.onNodeWithTag("swipe_delete").performClick()
        assertEquals(1, deleteClicks)
    }

    @Test
    fun swipeRightFromRevealed_belowCloseThreshold_staysRevealed() {
        setContent()
        composeRule.onNodeWithTag("content").performTouchInput {
            down(Offset(280f, 28f))
            moveTo(Offset(-1000f, 28f))
            up()
        }
        composeRule.onNodeWithTag("swipe_delete").assertIsDisplayed()
        composeRule.onNodeWithTag("content").performTouchInput {
            down(Offset(10f, 28f))
            moveTo(Offset(20f, 28f))
            up()
        }
        composeRule.onNodeWithTag("swipe_delete").assertIsDisplayed()
    }

    @Test
    fun swipeRightFromRevealed_pastCloseThreshold_closes() {
        setContent()
        composeRule.onNodeWithTag("content").performTouchInput {
            down(Offset(280f, 28f))
            moveTo(Offset(-1000f, 28f))
            up()
        }
        composeRule.onNodeWithTag("swipe_delete").assertIsDisplayed()
        composeRule.onNodeWithTag("content").performTouchInput {
            down(Offset(10f, 28f))
            moveTo(Offset(1000f, 28f))
            up()
        }
        composeRule.onNodeWithTag("swipe_delete").assertDoesNotExist()
    }
}
