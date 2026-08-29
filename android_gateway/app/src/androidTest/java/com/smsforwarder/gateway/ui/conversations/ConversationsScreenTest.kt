package com.smsforwarder.gateway.ui.conversations

import androidx.compose.ui.test.junit4.createComposeRule
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
            ConversationsContent(conversations = emptyList(), isImporting = false, onOpenThread = {})
        }

        composeRule.onNodeWithTag(ConversationsTestTags.EMPTY_STATE).assertExists()
        composeRule.onNodeWithTag(ConversationsTestTags.LIST).assertDoesNotExist()
    }

    @Test
    fun importingIndicatorShownInsteadOfEmptyStateWhileImporting() {
        composeRule.setContent {
            ConversationsContent(conversations = emptyList(), isImporting = true, onOpenThread = {})
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
                isImporting = false,
                onOpenThread = { opened = it },
            )
        }

        composeRule.onNodeWithTag(ConversationsTestTags.row("+15551234")).performClick()

        assertEquals("+15551234", opened)
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
            ConversationsScreen(viewModel = viewModel, onOpenThread = { opened = it })
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
            ConversationsScreen(viewModel = viewModel, onOpenThread = {})
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
            ConversationsScreen(viewModel = viewModel, onOpenThread = {})
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().failedCount == 2 }
        }
        composeRule.onNodeWithTag(ConversationsTestTags.RESEND_ALL_FAILED).performClick()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM_BUTTON).performClick()

        runBlocking { verify(repository).retryAllFailed() }
    }
}
