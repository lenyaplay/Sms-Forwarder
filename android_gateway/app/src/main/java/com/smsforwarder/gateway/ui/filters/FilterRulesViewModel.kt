package com.smsforwarder.gateway.ui.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.gateway.data.local.db.FilterMode
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.data.repository.FilterRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilterRulesUiState(
    val selectedStage: FilterStage = FilterStage.RECEPTION,
    val rules: List<FilterRuleEntity> = emptyList(),
    val mode: FilterMode = FilterMode.BLACKLIST,
    val activeSubscriptionIds: Set<Int> = emptySet(),
)

interface FilterRulesActions {
    fun onTabSelected(stage: FilterStage)
    fun onModeChange(mode: FilterMode)
    fun onToggleEnabled(rule: FilterRuleEntity)
    fun onDeleteRule(id: Long)
    fun onMoveUp(rule: FilterRuleEntity)
    fun onMoveDown(rule: FilterRuleEntity)
}

@HiltViewModel
class FilterRulesViewModel @Inject constructor(
    private val repository: FilterRuleRepository,
) : ViewModel(), FilterRulesActions {

    private val _uiState = MutableStateFlow(FilterRulesUiState(mode = repository.getMode(FilterStage.RECEPTION)))
    val uiState: StateFlow<FilterRulesUiState> = _uiState.asStateFlow()

    private var rulesJob: Job? = null

    init {
        observeStage(FilterStage.RECEPTION)
    }

    override fun onTabSelected(stage: FilterStage) {
        _uiState.update { it.copy(selectedStage = stage, mode = repository.getMode(stage)) }
        observeStage(stage)
    }

    override fun onModeChange(mode: FilterMode) {
        val stage = _uiState.value.selectedStage
        repository.setMode(stage, mode)
        _uiState.update { it.copy(mode = mode) }
    }

    override fun onToggleEnabled(rule: FilterRuleEntity) {
        viewModelScope.launch { repository.upsert(rule.copy(enabled = !rule.enabled)) }
    }

    override fun onDeleteRule(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    override fun onMoveUp(rule: FilterRuleEntity) = swapWithNeighbor(rule, offset = -1)

    override fun onMoveDown(rule: FilterRuleEntity) = swapWithNeighbor(rule, offset = 1)

    /** Reorders by swapping sortOrder with the adjacent rule in the currently-displayed (already-sorted) list. */
    private fun swapWithNeighbor(rule: FilterRuleEntity, offset: Int) {
        val rules = _uiState.value.rules
        val index = rules.indexOfFirst { it.id == rule.id }
        val neighborIndex = index + offset
        if (index < 0 || neighborIndex !in rules.indices) return
        val neighbor = rules[neighborIndex]
        viewModelScope.launch {
            repository.upsert(rule.copy(sortOrder = neighbor.sortOrder))
            repository.upsert(neighbor.copy(sortOrder = rule.sortOrder))
        }
    }

    private fun observeStage(stage: FilterStage) {
        rulesJob?.cancel()
        rulesJob = viewModelScope.launch {
            repository.observeRules(stage).collect { rules ->
                _uiState.update { it.copy(rules = rules, activeSubscriptionIds = repository.activeSubscriptionIds()) }
            }
        }
    }
}
