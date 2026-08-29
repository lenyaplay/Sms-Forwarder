package com.smsforwarder.gateway.ui.filters

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.gateway.data.local.SimOption
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.data.repository.FilterRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class FilterRuleEditUiState(
    val id: Long = 0,
    val stage: FilterStage = FilterStage.RECEPTION,
    val senderPattern: String = "",
    val senderIsRegex: Boolean = false,
    val subscriptionId: Int? = null,
    val contentPattern: String = "",
    val contentIsRegex: Boolean = false,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val availableSims: List<SimOption> = emptyList(),
    val saved: Boolean = false,
) {
    val senderPatternError: String?
        get() = regexError(senderPattern, senderIsRegex)

    val contentPatternError: String?
        get() = regexError(contentPattern, contentIsRegex)

    val canSave: Boolean get() = senderPatternError == null && contentPatternError == null

    private fun regexError(pattern: String, isRegex: Boolean): String? {
        if (!isRegex || pattern.isEmpty()) return null
        return if (runCatching { Regex(pattern) }.isFailure) "Некорректное регулярное выражение" else null
    }
}

interface FilterRuleEditActions {
    fun onSenderPatternChange(value: String)
    fun onSenderIsRegexChange(value: Boolean)
    fun onSubscriptionIdChange(value: Int?)
    fun onContentPatternChange(value: String)
    fun onContentIsRegexChange(value: Boolean)
    fun onEnabledChange(value: Boolean)
    fun onSave()
}

@HiltViewModel
class FilterRuleEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FilterRuleRepository,
) : ViewModel(), FilterRuleEditActions {

    private val ruleId: Long = (savedStateHandle.get<String>("id") ?: "0").toLongOrNull() ?: 0
    private val stage: FilterStage = FilterStage.valueOf(checkNotNull(savedStateHandle["stage"]))

    private val _uiState = MutableStateFlow(FilterRuleEditUiState(id = ruleId, stage = stage))
    val uiState: StateFlow<FilterRuleEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val sims = withContext(Dispatchers.IO) { repository.availableSims() }
            _uiState.update { it.copy(availableSims = sims) }
        }
        if (ruleId != 0L) {
            viewModelScope.launch {
                repository.getRule(ruleId)?.let { rule ->
                    _uiState.update {
                        it.copy(
                            senderPattern = rule.senderPattern.orEmpty(),
                            senderIsRegex = rule.senderIsRegex,
                            subscriptionId = rule.subscriptionId,
                            contentPattern = rule.contentPattern.orEmpty(),
                            contentIsRegex = rule.contentIsRegex,
                            enabled = rule.enabled,
                            sortOrder = rule.sortOrder,
                        )
                    }
                }
            }
        } else {
            // New rule goes to the end of this stage's list by default -
            // reordering afterwards is done from FilterRulesScreen's up/down controls.
            viewModelScope.launch {
                val rules = withContext(Dispatchers.IO) { repository.observeRules(stage).first() }
                val nextSortOrder = (rules.maxOfOrNull { it.sortOrder } ?: -1) + 1
                _uiState.update { it.copy(sortOrder = nextSortOrder) }
            }
        }
    }

    override fun onSenderPatternChange(value: String) = _uiState.update { it.copy(senderPattern = value) }

    override fun onSenderIsRegexChange(value: Boolean) = _uiState.update { it.copy(senderIsRegex = value) }

    override fun onSubscriptionIdChange(value: Int?) = _uiState.update { it.copy(subscriptionId = value) }

    override fun onContentPatternChange(value: String) = _uiState.update { it.copy(contentPattern = value) }

    override fun onContentIsRegexChange(value: Boolean) = _uiState.update { it.copy(contentIsRegex = value) }

    override fun onEnabledChange(value: Boolean) = _uiState.update { it.copy(enabled = value) }

    override fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            repository.upsert(
                FilterRuleEntity(
                    id = state.id,
                    stage = state.stage,
                    senderPattern = state.senderPattern.ifEmpty { null },
                    senderIsRegex = state.senderIsRegex,
                    subscriptionId = state.subscriptionId,
                    contentPattern = state.contentPattern.ifEmpty { null },
                    contentIsRegex = state.contentIsRegex,
                    enabled = state.enabled,
                    sortOrder = state.sortOrder,
                )
            )
            _uiState.update { it.copy(saved = true) }
        }
    }
}
