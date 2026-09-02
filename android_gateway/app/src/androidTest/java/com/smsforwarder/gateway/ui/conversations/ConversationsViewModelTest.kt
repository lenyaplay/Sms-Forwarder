package com.smsforwarder.gateway.ui.conversations

import androidx.compose.ui.test.junit4.createComposeRule
import com.smsforwarder.gateway.data.local.ContactNameResolver
import com.smsforwarder.gateway.data.local.SmsHistoryImporter
import com.smsforwarder.gateway.data.local.db.ConversationEntity
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableSharedFlow
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

/**
 * Exercises the archive/delete/search wiring at the ViewModel level - the
 * swipe-to-dismiss gesture that triggers these callbacks in production is a
 * UI-only concern already covered visually; this proves the callbacks
 * actually reach the repository with the right arguments.
 */
class ConversationsViewModelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildViewModel(
        repository: MessageRepository = mock(),
        contactNameResolver: ContactNameResolver = mock(),
    ): ConversationsViewModel {
        whenever(repository.observeConversations(false)).thenReturn(flowOf(emptyList<ConversationEntity>()))
        whenever(repository.observeConversations(true)).thenReturn(flowOf(emptyList<ConversationEntity>()))
        whenever(repository.observeFailedCount()).thenReturn(flowOf(0))
        val historyImporter: SmsHistoryImporter = mock()
        whenever(historyImporter.isImporting).thenReturn(MutableStateFlow(false))
        lateinit var viewModel: ConversationsViewModel
        composeRule.setContent {
            viewModel = ConversationsViewModel(repository, contactNameResolver, historyImporter)
        }
        return viewModel
    }

    @Test
    fun archiveToggleArchivesWhenNotCurrentlyArchived() {
        val repository: MessageRepository = mock()
        val viewModel = buildViewModel(repository)

        viewModel.onArchiveToggle("+15551234", currentlyArchived = false)

        runBlocking { verify(repository).archiveConversation("+15551234") }
    }

    @Test
    fun archiveToggleUnarchivesWhenCurrentlyArchived() {
        val repository: MessageRepository = mock()
        val viewModel = buildViewModel(repository)

        viewModel.onArchiveToggle("+15551234", currentlyArchived = true)

        runBlocking { verify(repository).unarchiveConversation("+15551234") }
    }

    @Test
    fun deleteConversationDelegatesToRepository() {
        val repository: MessageRepository = mock()
        val viewModel = buildViewModel(repository)

        viewModel.onDeleteConversation("+15551234")

        runBlocking { verify(repository).deleteConversation("+15551234") }
    }

    @Test
    fun toggleArchivedViewSwitchesTheObservedList() {
        val repository: MessageRepository = mock()
        whenever(repository.observeConversations(false)).thenReturn(
            flowOf(listOf(ConversationEntity("+1", "active", 1L, com.smsforwarder.gateway.data.local.db.DeliveryStatus.SENT, com.smsforwarder.gateway.data.local.db.MessageDirection.IN)))
        )
        whenever(repository.observeConversations(true)).thenReturn(
            flowOf(listOf(ConversationEntity("+2", "archived", 1L, com.smsforwarder.gateway.data.local.db.DeliveryStatus.SENT, com.smsforwarder.gateway.data.local.db.MessageDirection.IN)))
        )
        whenever(repository.observeFailedCount()).thenReturn(flowOf(0))
        val historyImporter: SmsHistoryImporter = mock()
        whenever(historyImporter.isImporting).thenReturn(MutableStateFlow(false))
        lateinit var viewModel: ConversationsViewModel
        composeRule.setContent {
            viewModel = ConversationsViewModel(repository, mock(), historyImporter)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().conversations.isNotEmpty() }
        }
        assertEquals("+1", runBlocking { viewModel.uiState.first().conversations[0].sender })

        viewModel.onToggleArchivedView()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().conversations.any { it.sender == "+2" } }
        }
    }

    @Test
    fun searchPopulatesResultsAndClearingQueryEmptiesThem() {
        val repository: MessageRepository = mock()
        whenever(repository.searchMessages("hello")).thenReturn(
            flowOf(listOf(MessageEntity(id = 1L, sender = "+1", text = "hello there", sentStamp = 1L, receivedStamp = 1L, simSlot = null, deliveryStatus = com.smsforwarder.gateway.data.local.db.DeliveryStatus.SENT, createdAt = 1L)))
        )
        val viewModel = buildViewModel(repository)

        viewModel.onQueryChange("hello")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().searchResults.isNotEmpty() }
        }
        assertEquals(1, runBlocking { viewModel.uiState.first().searchResults.size })

        viewModel.onQueryChange("")
        assertEquals(0, runBlocking { viewModel.uiState.first().searchResults.size })
    }

    @Test
    fun onResendAllFailedDelegatesToRepository() {
        val repository: MessageRepository = mock()
        val viewModel = buildViewModel(repository)

        viewModel.onResendAllFailed()

        runBlocking { verify(repository).retryAllFailed() }
    }

    @Test
    fun hasLoadedOnceStaysFalseUntilFlowEmitsThenBecomesTrue() {
        val repository: MessageRepository = mock()
        val conversationsFlow = MutableSharedFlow<List<ConversationEntity>>() // no replay - subscriber controls timing
        whenever(repository.observeConversations(false)).thenReturn(conversationsFlow)
        whenever(repository.observeFailedCount()).thenReturn(flowOf(0))
        val historyImporter: SmsHistoryImporter = mock()
        whenever(historyImporter.isImporting).thenReturn(MutableStateFlow(false))
        lateinit var viewModel: ConversationsViewModel
        composeRule.setContent {
            viewModel = ConversationsViewModel(repository, mock(), historyImporter)
        }

        assertEquals(false, runBlocking { viewModel.uiState.first().hasLoadedOnce })

        runBlocking { conversationsFlow.emit(emptyList()) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().hasLoadedOnce }
        }
    }

    @Test
    fun failedCountReflectsRepositoryObservation() {
        val repository: MessageRepository = mock()
        whenever(repository.observeConversations(false)).thenReturn(flowOf(emptyList<ConversationEntity>()))
        whenever(repository.observeFailedCount()).thenReturn(flowOf(3))
        val historyImporter: SmsHistoryImporter = mock()
        whenever(historyImporter.isImporting).thenReturn(MutableStateFlow(false))
        lateinit var viewModel: ConversationsViewModel
        composeRule.setContent {
            viewModel = ConversationsViewModel(repository, mock(), historyImporter)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().failedCount == 3 }
        }
    }
}
