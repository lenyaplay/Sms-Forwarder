package com.smsforwarder.gateway.ui.thread

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.smsforwarder.gateway.data.local.ContactNameResolver
import com.smsforwarder.gateway.data.local.SimOption
import com.smsforwarder.gateway.data.local.SimOptionsProvider
import com.smsforwarder.gateway.data.repository.MessageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * ThreadScreenTest only exercises the pure ThreadContent composable with a
 * hand-built ThreadActions - it never proves that ThreadViewModel.onSend()
 * actually forwards the SELECTED sim's subscriptionId/slotIndex to the
 * repository rather than always sending on the default sim. This constructs
 * the real ViewModel (bypassing Hilt, like SettingsScreenTest does).
 */
class ThreadViewModelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sendingUsesTheSelectedSimNotJustTheFirstOne() {
        val sender = "+15551234"
        val repository: MessageRepository = mock()
        whenever(repository.observeThread(sender)).thenReturn(flowOf(emptyList()))
        val contactNameResolver: ContactNameResolver = mock()
        val simOptionsProvider: SimOptionsProvider = mock()
        whenever(simOptionsProvider.activeSims()).thenReturn(
            listOf(SimOption(subscriptionId = 1, slotIndex = 0, displayName = "SIM 1"), SimOption(subscriptionId = 2, slotIndex = 1, displayName = "SIM 2"))
        )

        lateinit var viewModel: ThreadViewModel
        composeRule.setContent {
            viewModel = ThreadViewModel(SavedStateHandle(mapOf("sender" to sender)), repository, contactNameResolver, simOptionsProvider)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().availableSims.isNotEmpty() }
        }

        viewModel.onSelectSim(2)
        viewModel.onDraftChange("hi")
        viewModel.onSend()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { !viewModel.uiState.first().isSending }
        }

        runBlocking { verify(repository).sendMessage(sender, "hi", 2, 1) }
    }
}
