package com.smsforwarder.gateway.ui.deliverylog

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.smsforwarder.gateway.data.local.db.DeliveryLogEntity
import org.junit.Rule
import org.junit.Test

class DeliveryLogScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsEmptyStateWhenNoEntries() {
        composeRule.setContent {
            DeliveryLogContent(uiState = DeliveryLogUiState(entries = emptyList()), onBack = {})
        }

        composeRule.onNodeWithTag(DeliveryLogTestTags.EMPTY_STATE).assertExists()
    }

    @Test
    fun showsListWhenEntriesPresent() {
        val entries = listOf(
            DeliveryLogEntity(id = 1, sender = "+15551234", attemptNumber = 1, timestamp = 111L, success = true, errorMessage = null),
            DeliveryLogEntity(id = 2, sender = "+15551234", attemptNumber = 2, timestamp = 222L, success = false, errorMessage = "HTTP 500"),
        )
        composeRule.setContent {
            DeliveryLogContent(uiState = DeliveryLogUiState(entries = entries), onBack = {})
        }

        composeRule.onNodeWithTag(DeliveryLogTestTags.LIST).assertExists()
        composeRule.onNodeWithTag(DeliveryLogTestTags.entryRow(1)).assertExists()
        composeRule.onNodeWithTag(DeliveryLogTestTags.entryRow(2)).assertExists()
    }
}
