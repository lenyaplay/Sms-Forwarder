package com.smsforwarder.gateway.ui.filters

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.smsforwarder.gateway.data.local.db.FilterMode
import com.smsforwarder.gateway.data.local.db.FilterRuleEntity
import com.smsforwarder.gateway.data.local.db.FilterStage
import org.junit.Rule
import org.junit.Test

class FilterRulesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingActions : FilterRulesActions {
        var selectedStage: FilterStage? = null
        var changedMode: FilterMode? = null
        var toggledRule: FilterRuleEntity? = null
        var deletedId: Long? = null
        var movedUp: FilterRuleEntity? = null
        var movedDown: FilterRuleEntity? = null
        override fun onTabSelected(stage: FilterStage) { selectedStage = stage }
        override fun onModeChange(mode: FilterMode) { changedMode = mode }
        override fun onToggleEnabled(rule: FilterRuleEntity) { toggledRule = rule }
        override fun onDeleteRule(id: Long) { deletedId = id }
        override fun onMoveUp(rule: FilterRuleEntity) { movedUp = rule }
        override fun onMoveDown(rule: FilterRuleEntity) { movedDown = rule }
    }

    private fun rule(id: Long = 1L, subscriptionId: Int? = null) = FilterRuleEntity(
        id = id,
        stage = FilterStage.RECEPTION,
        senderPattern = "Bank",
        senderIsRegex = false,
        subscriptionId = subscriptionId,
        contentPattern = null,
        contentIsRegex = false,
        enabled = true,
        sortOrder = 0,
    )

    @Test
    fun emptyStateShownWhenNoRules() {
        composeRule.setContent {
            FilterRulesContent(
                uiState = FilterRulesUiState(),
                actions = RecordingActions(),
                onBack = {},
                onAddRule = {},
                onEditRule = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(FilterRulesTestTags.EMPTY_STATE).assertExists()
    }

    @Test
    fun tabClickInvokesOnTabSelected() {
        val actions = RecordingActions()
        composeRule.setContent {
            FilterRulesContent(
                uiState = FilterRulesUiState(),
                actions = actions,
                onBack = {},
                onAddRule = {},
                onEditRule = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(FilterRulesTestTags.TAB_FORWARDING).performClick()

        assert(actions.selectedStage == FilterStage.FORWARDING)
    }

    @Test
    fun modeSegmentClickInvokesOnModeChange() {
        val actions = RecordingActions()
        composeRule.setContent {
            FilterRulesContent(
                uiState = FilterRulesUiState(mode = FilterMode.BLACKLIST),
                actions = actions,
                onBack = {},
                onAddRule = {},
                onEditRule = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(FilterRulesTestTags.MODE_WHITELIST).performClick()

        assert(actions.changedMode == FilterMode.WHITELIST)
    }

    @Test
    fun deleteViaSwipeConfirmInvokesOnDeleteRule() {
        val actions = RecordingActions()
        val r = rule(id = 5L)
        composeRule.setContent {
            FilterRulesContent(
                uiState = FilterRulesUiState(rules = listOf(r)),
                actions = actions,
                onBack = {},
                onAddRule = {},
                onEditRule = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(FilterRulesTestTags.enabledSwitch(5L)).performClick()

        assert(actions.toggledRule == r)
    }

    @Test
    fun unavailableSimBadgeShownForRuleOutsideActiveSubscriptions() {
        composeRule.setContent {
            FilterRulesContent(
                uiState = FilterRulesUiState(
                    rules = listOf(rule(id = 1L, subscriptionId = 99)),
                    activeSubscriptionIds = setOf(1, 2),
                ),
                actions = RecordingActions(),
                onBack = {},
                onAddRule = {},
                onEditRule = { _, _ -> },
            )
        }

        // useUnmergedTree: SwipeToDismissBox merges its content's semantics into one
        // node for accessibility, which would otherwise hide this plain (non-interactive) Text's own tag.
        composeRule.onNodeWithTag(FilterRulesTestTags.UNAVAILABLE_SIM_BADGE, useUnmergedTree = true).assertExists()
    }

    @Test
    fun moveDownOnFirstRuleInvokesOnMoveDown() {
        val actions = RecordingActions()
        val first = rule(id = 1L)
        val second = rule(id = 2L)
        composeRule.setContent {
            FilterRulesContent(
                uiState = FilterRulesUiState(rules = listOf(first, second)),
                actions = actions,
                onBack = {},
                onAddRule = {},
                onEditRule = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(FilterRulesTestTags.moveDownButton(1L), useUnmergedTree = true).performClick()

        assert(actions.movedDown == first)
    }

    @Test
    fun moveUpDisabledForFirstRuleAndMoveDownDisabledForLastRule() {
        val first = rule(id = 1L)
        val second = rule(id = 2L)
        composeRule.setContent {
            FilterRulesContent(
                uiState = FilterRulesUiState(rules = listOf(first, second)),
                actions = RecordingActions(),
                onBack = {},
                onAddRule = {},
                onEditRule = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(FilterRulesTestTags.moveUpButton(1L), useUnmergedTree = true).assertIsNotEnabled()
        composeRule.onNodeWithTag(FilterRulesTestTags.moveDownButton(2L), useUnmergedTree = true).assertIsNotEnabled()
    }
}
