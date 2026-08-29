package com.smsforwarder.gateway.ui.conversations

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smsforwarder.gateway.data.local.ContactNameResolver
import com.smsforwarder.gateway.data.local.SmsHistoryImporter
import com.smsforwarder.gateway.data.local.db.ConversationEntity
import com.smsforwarder.gateway.data.repository.MessageRepository
import com.smsforwarder.gateway.ui.common.ConfirmDialogTestTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConversationsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun conversation(sender: String, text: String, displayName: String = sender) = ConversationUi(
        sender = sender,
        displayName = displayName,
        text = text,
        createdAt = 1L,
    )

    @Test
    fun emptyStateShownWhenNoConversationsAndNotImporting() {
        composeRule.setContent {
            ConversationsContent(conversations = emptyList(), isImporting = false, onOpenThread = { _, _ -> })
        }

        composeRule.onNodeWithTag(ConversationsTestTags.EMPTY_STATE).assertExists()
        composeRule.onNodeWithTag(ConversationsTestTags.LIST).assertDoesNotExist()
    }

    @Test
    fun importingIndicatorShownInsteadOfEmptyStateWhileImporting() {
        composeRule.setContent {
            ConversationsContent(conversations = emptyList(), isImporting = true, onOpenThread = { _, _ -> })
        }

        composeRule.onNodeWithTag(ConversationsTestTags.IMPORTING_INDICATOR).assertExists()
        composeRule.onNodeWithTag(ConversationsTestTags.EMPTY_STATE).assertDoesNotExist()
    }

    @Test
    fun rendersOneRowPerConversation() {
        composeRule.setContent {
            ConversationsContent(
                conversations = listOf(conversation("+15551234", "hi"), conversation("+15559999", "hello")),
                isImporting = false,
                onOpenThread = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(ConversationsTestTags.row("+15551234")).assertExists()
        composeRule.onNodeWithTag(ConversationsTestTags.row("+15559999")).assertExists()
    }

    @Test
    fun tappingARowOpensItsThreadWithNoTargetMessage() {
        var opened: String? = null
        var openedMessageId: Long? = -1L
        composeRule.setContent {
            ConversationsContent(
                conversations = listOf(conversation("+15551234", "hi")),
                isImporting = false,
                onOpenThread = { sender, messageId -> opened = sender; openedMessageId = messageId },
            )
        }

        composeRule.onNodeWithTag(ConversationsTestTags.row("+15551234")).performClick()

        assertEquals("+15551234", opened)
        assertEquals(null, openedMessageId)
    }

    @Test
    fun tappingASearchResultOpensThreadOnThatMessageNotTheLastOne() {
        val repository: MessageRepository = mock()
        whenever(repository.observeConversations()).thenReturn(flowOf(emptyList<ConversationEntity>()))
        whenever(repository.observeFailedCount()).thenReturn(flowOf(0))
        val foundMessage = com.smsforwarder.gateway.data.local.db.MessageEntity(
            id = 42L, sender = "+15551234", text = "found me", sentStamp = null, receivedStamp = 1L,
            simSlot = 0, deliveryStatus = com.smsforwarder.gateway.data.local.db.DeliveryStatus.SENT, createdAt = 1L,
        )
        whenever(repository.searchMessages("found")).thenReturn(flowOf(listOf(foundMessage)))
        val contactNameResolver: ContactNameResolver = mock()
        val historyImporter: SmsHistoryImporter = mock()
        whenever(historyImporter.isImporting).thenReturn(MutableStateFlow(false))
        val viewModel = ConversationsViewModel(repository, contactNameResolver, historyImporter)

        var openedSender: String? = null
        var openedMessageId: Long? = null
        composeRule.setContent {
            ConversationsScreen(viewModel = viewModel, onOpenThread = { sender, messageId -> openedSender = sender; openedMessageId = messageId })
        }

        composeRule.onNodeWithTag(ConversationsTestTags.SEARCH_FIELD).performTextInput("found")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().searchResults.isNotEmpty() }
        }
        composeRule.onNodeWithTag(ConversationsTestTags.SEARCH_RESULTS_LIST).onChildren()[0].performClick()

        assertEquals("+15551234", openedSender)
        assertEquals(42L, openedMessageId)
    }

    @Test
    fun contactNameIsNotReResolvedOnRepeatedEmissionsForTheSameSender() {
        val repository: MessageRepository = mock()
        val conversationsFlow = MutableStateFlow(
            listOf(ConversationEntity("+15551234", "hi", 1L, com.smsforwarder.gateway.data.local.db.DeliveryStatus.SENT, com.smsforwarder.gateway.data.local.db.MessageDirection.IN))
        )
        whenever(repository.observeConversations()).thenReturn(conversationsFlow)
        whenever(repository.observeFailedCount()).thenReturn(flowOf(0))
        val contactNameResolver: ContactNameResolver = mock()
        whenever(contactNameResolver.displayNameFor("+15551234")).thenReturn("Alice")
        val historyImporter: SmsHistoryImporter = mock()
        whenever(historyImporter.isImporting).thenReturn(MutableStateFlow(false))
        val viewModel = ConversationsViewModel(repository, contactNameResolver, historyImporter)

        composeRule.setContent {
            ConversationsScreen(viewModel = viewModel, onOpenThread = { _, _ -> })
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().conversations.any { it.displayName == "Alice" } }
        }

        // Same sender, new emission (e.g. a new incoming message updating the
        // conversation's last text) - must reuse the cached name, not re-query
        // ContactNameResolver's ContentResolver.query on every list refresh.
        conversationsFlow.value = listOf(
            ConversationEntity("+15551234", "hi again", 2L, com.smsforwarder.gateway.data.local.db.DeliveryStatus.SENT, com.smsforwarder.gateway.data.local.db.MessageDirection.IN)
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().conversations.any { it.text == "hi again" } }
        }

        verify(contactNameResolver, org.mockito.kotlin.times(1)).displayNameFor("+15551234")
    }

    private fun buildScreenViewModel(repository: MessageRepository): ConversationsViewModel {
        whenever(repository.observeConversations()).thenReturn(flowOf(emptyList<ConversationEntity>()))
        whenever(repository.observeFailedCount()).thenReturn(flowOf(0))
        val contactNameResolver: ContactNameResolver = mock()
        val historyImporter: SmsHistoryImporter = mock()
        whenever(historyImporter.isImporting).thenReturn(MutableStateFlow(false))
        return ConversationsViewModel(repository, contactNameResolver, historyImporter)
    }

    @Test
    fun fabOpensNewMessageDialogAndConfirmingNavigatesToThatNumber() {
        val repository: MessageRepository = mock()
        val viewModel = buildScreenViewModel(repository)

        var opened: String? = null
        composeRule.setContent {
            ConversationsScreen(viewModel = viewModel, onOpenThread = { sender, _ -> opened = sender })
        }

        composeRule.onNodeWithTag(ConversationsTestTags.NEW_MESSAGE_FAB).performClick()
        composeRule.onNodeWithTag(NewMessageDialogTestTags.NUMBER_FIELD).performTextInput("+15551234")
        composeRule.onNodeWithTag(NewMessageDialogTestTags.CONFIRM_BUTTON).performClick()

        assertEquals("+15551234", opened)
    }

    @Test
    fun newMessageDialogConfirmInvokesCallbackWithEnteredNumber() {
        var confirmed: String? = null
        composeRule.setContent {
            NewMessageDialog(onDismiss = {}, onConfirm = { confirmed = it })
        }

        composeRule.onNodeWithTag(NewMessageDialogTestTags.NUMBER_FIELD).performTextInput("+15551234")
        composeRule.onNodeWithTag(NewMessageDialogTestTags.CONFIRM_BUTTON).performClick()

        assertEquals("+15551234", confirmed)
    }

    @Test
    fun resendAllFailedButtonHiddenWhenNoFailedMessages() {
        val repository: MessageRepository = mock()
        val viewModel = buildScreenViewModel(repository)
        composeRule.setContent {
            ConversationsScreen(viewModel = viewModel, onOpenThread = { _, _ -> })
        }

        composeRule.onNodeWithTag(ConversationsTestTags.RESEND_ALL_FAILED).assertDoesNotExist()
    }

    @Test
    fun resendAllFailedButtonShownAndConfirmingInvokesRepository() {
        val repository: MessageRepository = mock()
        whenever(repository.observeConversations()).thenReturn(flowOf(emptyList<ConversationEntity>()))
        whenever(repository.observeFailedCount()).thenReturn(flowOf(2))
        val contactNameResolver: ContactNameResolver = mock()
        val historyImporter: SmsHistoryImporter = mock()
        whenever(historyImporter.isImporting).thenReturn(MutableStateFlow(false))
        val viewModel = ConversationsViewModel(repository, contactNameResolver, historyImporter)
        composeRule.setContent {
            ConversationsScreen(viewModel = viewModel, onOpenThread = { _, _ -> })
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().failedCount == 2 }
        }
        composeRule.onNodeWithTag(ConversationsTestTags.RESEND_ALL_FAILED).performClick()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM_BUTTON).performClick()

        runBlocking { verify(repository).retryAllFailed() }
    }
}
