package com.smsforwarder.gateway.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smsforwarder.gateway.ui.common.ConfirmDialogTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openDeliveryButtonInvokesCallback() {
        var opened = false
        composeRule.setContent {
            SettingsContent(onOpenDelivery = { opened = true }, onOpenFilterRules = {})
        }

        composeRule.onNodeWithTag(SettingsTestTags.OPEN_DELIVERY_BUTTON).performClick()

        assertTrue(opened)
    }

    @Test
    fun openFilterRulesButtonInvokesCallback() {
        var opened = false
        composeRule.setContent {
            SettingsContent(onOpenDelivery = {}, onOpenFilterRules = { opened = true })
        }

        composeRule.onNodeWithTag(SettingsTestTags.OPEN_FILTER_RULES_BUTTON).performClick()

        assertTrue(opened)
    }

    @Test
    fun openDeliveryLogButtonInvokesCallback() {
        var opened = false
        composeRule.setContent {
            SettingsContent(onOpenDelivery = {}, onOpenFilterRules = {}, onOpenDeliveryLog = { opened = true })
        }

        composeRule.onNodeWithTag(SettingsTestTags.OPEN_DELIVERY_LOG_BUTTON).performClick()

        assertTrue(opened)
    }

    @Test
    fun exportButtonShowsTokenWarningBeforeLaunchingPicker() {
        composeRule.setContent {
            SettingsContent()
        }

        composeRule.onNodeWithTag(SettingsTestTags.EXPORT_BUTTON).performClick()

        composeRule.onNodeWithText("Экспортировать настройки?").assertExists()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM_BUTTON).assertExists()
    }

    @Test
    fun dismissingExportWarningDoesNotLaunchPicker() {
        composeRule.setContent {
            SettingsContent()
        }

        composeRule.onNodeWithTag(SettingsTestTags.EXPORT_BUTTON).performClick()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DISMISS_BUTTON).performClick()

        composeRule.onNodeWithText("Экспортировать настройки?").assertDoesNotExist()
    }

    @Test
    fun diagnosticsSwitchReflectsUiStateAndTogglingInvokesAction() {
        var lastValue: Boolean? = null
        val actions = object : SettingsActions {
            override fun onExportConfirmed(uri: android.net.Uri) {}
            override fun onImportConfirmed(uri: android.net.Uri) {}
            override fun onMessageDismissed() {}
            override fun onDiagnosticsEnabledChange(value: Boolean) {
                lastValue = value
            }
        }
        composeRule.setContent {
            SettingsContent(uiState = SettingsUiState(isDiagnosticsEnabled = false), actions = actions)
        }

        composeRule.onNodeWithTag(SettingsTestTags.DIAGNOSTICS_SWITCH).performClick()

        assertEquals(true, lastValue)
    }

    @Test
    fun messageFromUiStateIsDisplayed() {
        composeRule.setContent {
            SettingsContent(uiState = SettingsUiState(message = "Настройки экспортированы", isMessageError = false))
        }

        composeRule.onNodeWithTag(SettingsTestTags.MESSAGE).assertExists()
    }
}
