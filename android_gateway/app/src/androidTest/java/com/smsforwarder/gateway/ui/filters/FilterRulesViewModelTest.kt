package com.smsforwarder.gateway.ui.filters

import androidx.compose.ui.test.junit4.createComposeRule
import com.smsforwarder.gateway.data.local.db.FilterMode
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

class FilterRulesViewModelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun rule(stage: FilterStage, id: Long = 1L, sortOrder: Int = 0) = FilterRuleEntity(
        id = id,
        stage = stage,
        senderPattern = "Bank",
        senderIsRegex = false,
        subscriptionId = null,
        contentPattern = null,
        contentIsRegex = false,
        enabled = true,
        sortOrder = sortOrder,
    )

    private fun buildViewModel(repository: FilterRuleRepository): FilterRulesViewModel {
        lateinit var viewModel: FilterRulesViewModel
        composeRule.setContent { viewModel = FilterRulesViewModel(repository) }
        return viewModel
    }

    @Test
    fun tabSelectionSwitchesObservedRulesAndMode() {
        val repository: FilterRuleRepository = mock()
        whenever(repository.observeRules(FilterStage.RECEPTION)).thenReturn(flowOf(listOf(rule(FilterStage.RECEPTION, id = 1))))
        whenever(repository.observeRules(FilterStage.FORWARDING)).thenReturn(flowOf(listOf(rule(FilterStage.FORWARDING, id = 2))))
        whenever(repository.getMode(FilterStage.RECEPTION)).thenReturn(FilterMode.BLACKLIST)
        whenever(repository.getMode(FilterStage.FORWARDING)).thenReturn(FilterMode.WHITELIST)
        val viewModel = buildViewModel(repository)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().rules.any { it.id == 1L } }
        }

        viewModel.onTabSelected(FilterStage.FORWARDING)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().rules.any { it.id == 2L } }
        }
        assertEquals(FilterMode.WHITELIST, runBlocking { viewModel.uiState.first().mode })
    }

    @Test
    fun deleteRuleDelegatesToRepository() {
        val repository: FilterRuleRepository = mock()
        whenever(repository.observeRules(FilterStage.RECEPTION)).thenReturn(flowOf(emptyList()))
        whenever(repository.getMode(FilterStage.RECEPTION)).thenReturn(FilterMode.BLACKLIST)
        val viewModel = buildViewModel(repository)

        viewModel.onDeleteRule(7L)

        runBlocking { verify(repository).delete(7L) }
    }

    @Test
    fun modeChangeDelegatesAndUpdatesState() {
        val repository: FilterRuleRepository = mock()
        whenever(repository.observeRules(FilterStage.RECEPTION)).thenReturn(flowOf(emptyList()))
        whenever(repository.getMode(FilterStage.RECEPTION)).thenReturn(FilterMode.BLACKLIST)
        val viewModel = buildViewModel(repository)

        viewModel.onModeChange(FilterMode.WHITELIST)

        runBlocking { verify(repository).setMode(FilterStage.RECEPTION, FilterMode.WHITELIST) }
        assertEquals(FilterMode.WHITELIST, runBlocking { viewModel.uiState.first().mode })
    }

    @Test
    fun toggleEnabledUpsertsRuleWithFlippedFlag() {
        val repository: FilterRuleRepository = mock()
        whenever(repository.observeRules(FilterStage.RECEPTION)).thenReturn(flowOf(emptyList()))
        whenever(repository.getMode(FilterStage.RECEPTION)).thenReturn(FilterMode.BLACKLIST)
        val viewModel = buildViewModel(repository)
        val existing = rule(FilterStage.RECEPTION, id = 3L)

        viewModel.onToggleEnabled(existing)

        runBlocking { verify(repository).upsert(existing.copy(enabled = false)) }
    }

    @Test
    fun moveDownSwapsSortOrderWithNextRuleInList() {
        val repository: FilterRuleRepository = mock()
        val first = rule(FilterStage.RECEPTION, id = 1L, sortOrder = 0)
        val second = rule(FilterStage.RECEPTION, id = 2L, sortOrder = 1)
        whenever(repository.observeRules(FilterStage.RECEPTION)).thenReturn(flowOf(listOf(first, second)))
        whenever(repository.getMode(FilterStage.RECEPTION)).thenReturn(FilterMode.BLACKLIST)
        val viewModel = buildViewModel(repository)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().rules.size == 2 }
        }

        viewModel.onMoveDown(first)

        runBlocking {
            verify(repository).upsert(first.copy(sortOrder = 1))
            verify(repository).upsert(second.copy(sortOrder = 0))
        }
    }

    @Test
    fun moveUpOnFirstRuleIsANoOp() {
        val repository: FilterRuleRepository = mock()
        val first = rule(FilterStage.RECEPTION, id = 1L, sortOrder = 0)
        whenever(repository.observeRules(FilterStage.RECEPTION)).thenReturn(flowOf(listOf(first)))
        whenever(repository.getMode(FilterStage.RECEPTION)).thenReturn(FilterMode.BLACKLIST)
        val viewModel = buildViewModel(repository)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { viewModel.uiState.first().rules.isNotEmpty() }
        }

        viewModel.onMoveUp(first)

        runBlocking { verify(repository, org.mockito.kotlin.never()).upsert(org.mockito.kotlin.any()) }
    }
}
