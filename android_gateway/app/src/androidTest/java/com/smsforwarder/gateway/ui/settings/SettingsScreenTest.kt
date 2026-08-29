package com.smsforwarder.gateway.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
            SettingsScreen(onOpenDelivery = { opened = true }, onOpenFilterRules = {})
        }

        composeRule.onNodeWithTag(SettingsTestTags.OPEN_DELIVERY_BUTTON).performClick()

        assertTrue(opened)
    }

    @Test
    fun openFilterRulesButtonInvokesCallback() {
        var opened = false
        composeRule.setContent {
            SettingsScreen(onOpenDelivery = {}, onOpenFilterRules = { opened = true })
        }

        composeRule.onNodeWithTag(SettingsTestTags.OPEN_FILTER_RULES_BUTTON).performClick()

        assertTrue(opened)
    }
}
