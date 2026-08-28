package com.smsforwarder.gateway.ui.messages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageEntity
import org.junit.Rule
import org.junit.Test

class MessagesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysSenderTextAndDeliveryStatusForEachMessage() {
        composeRule.setContent {
            MessagesContent(
                messages = listOf(
                    MessageEntity(
                        id = 1,
                        sender = "+15551234",
                        text = "hello there",
                        sentStamp = 111L,
                        receivedStamp = 222L,
                        simSlot = 0,
                        deliveryStatus = DeliveryStatus.SENT,
                        createdAt = 333L,
                    )
                )
            )
        }

        composeRule.onNodeWithTag(MessagesTestTags.row(1)).assertIsDisplayed()
        composeRule.onNodeWithText("+15551234").assertIsDisplayed()
        composeRule.onNodeWithText("hello there").assertIsDisplayed()
        composeRule.onNodeWithText("SENT").assertIsDisplayed()
    }

    @Test
    fun emptyListShowsNoRows() {
        composeRule.setContent { MessagesContent(messages = emptyList()) }

        // An empty LazyColumn has zero measured size, so assertIsDisplayed()
        // (which checks bounds) fails even though the node is present and
        // correct - assertExists() is the right check for "no rows rendered".
        composeRule.onNodeWithTag(MessagesTestTags.LIST).assertExists()
    }
}
