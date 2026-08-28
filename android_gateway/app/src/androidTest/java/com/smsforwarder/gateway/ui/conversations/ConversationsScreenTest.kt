package com.smsforwarder.gateway.ui.conversations

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.smsforwarder.gateway.data.local.db.ConversationEntity
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConversationsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun conversation(sender: String, text: String) = ConversationEntity(
        sender = sender,
        text = text,
        createdAt = 1L,
        deliveryStatus = DeliveryStatus.SENT,
        direction = MessageDirection.IN,
    )

    @Test
    fun emptyListShowsNoRows() {
        composeRule.setContent {
            ConversationsContent(conversations = emptyList(), onOpenThread = {})
        }

        composeRule.onNodeWithTag(ConversationsTestTags.LIST).assertExists()
    }

    @Test
    fun rendersOneRowPerConversation() {
        composeRule.setContent {
            ConversationsContent(
                conversations = listOf(conversation("+15551234", "hi"), conversation("+15559999", "hello")),
                onOpenThread = {},
            )
        }

        composeRule.onNodeWithTag(ConversationsTestTags.row("+15551234")).assertExists()
        composeRule.onNodeWithTag(ConversationsTestTags.row("+15559999")).assertExists()
    }

    @Test
    fun tappingARowOpensItsThread() {
        var opened: String? = null
        composeRule.setContent {
            ConversationsContent(
                conversations = listOf(conversation("+15551234", "hi")),
                onOpenThread = { opened = it },
            )
        }

        composeRule.onNodeWithTag(ConversationsTestTags.row("+15551234")).performClick()

        assertEquals("+15551234", opened)
    }
}
