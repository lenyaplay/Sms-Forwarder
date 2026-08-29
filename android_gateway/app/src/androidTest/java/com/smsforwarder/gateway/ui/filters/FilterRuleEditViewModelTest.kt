package com.smsforwarder.gateway.ui.filters

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.data.repository.FilterRuleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Regression coverage for a real gap found in peer review: new rules used to always save with sortOrder=0, and editing an existing rule silently reset its position - both broke the spec's "порядок можно менять в UI" acceptance criterion. */
class FilterRuleEditViewModelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newRuleGetsNextSortOrderAfterExistingRulesInTheSameStage() {
        val repository: FilterRuleRepository = mock()
        whenever(repository.observeRules(FilterStage.RECEPTION)).thenReturn(
            flowOf(
                listOf(
                    FilterRuleEntity(id = 1, stage = FilterStage.RECEPTION, senderPattern = null, senderIsRegex = false, subscriptionId = null, contentPattern = null, contentIsRegex = false, enabled = true, sortOrder = 3),
                )
            )
        )
        whenever(repository.availableSims()).thenReturn(emptyList())
        val savedStateHandle = SavedStateHandle(mapOf("id" to "0", "stage" to "RECEPTION"))
        lateinit var viewModel: FilterRuleEditViewModel
        composeRule.setContent { viewModel = FilterRuleEditViewModel(savedStateHandle, repository) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().sortOrder == 4 }
        }
    }

    @Test
    fun editingExistingRulePreservesItsSortOrderOnSave() {
        val repository: FilterRuleRepository = mock()
        val existing = FilterRuleEntity(id = 9, stage = FilterStage.RECEPTION, senderPattern = "Bank", senderIsRegex = false, subscriptionId = null, contentPattern = null, contentIsRegex = false, enabled = true, sortOrder = 7)
        runBlocking { whenever(repository.getRule(9L)).thenReturn(existing) }
        whenever(repository.availableSims()).thenReturn(emptyList())
        val savedStateHandle = SavedStateHandle(mapOf("id" to "9", "stage" to "RECEPTION"))
        lateinit var viewModel: FilterRuleEditViewModel
        composeRule.setContent { viewModel = FilterRuleEditViewModel(savedStateHandle, repository) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().sortOrder == 7 }
        }

        viewModel.onSave()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().saved }
        }
        runBlocking { verify(repository).upsert(existing.copy(sortOrder = 7)) }
        assertEquals(7, existing.sortOrder)
    }
}
