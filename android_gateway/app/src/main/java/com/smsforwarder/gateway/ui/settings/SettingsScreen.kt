package com.smsforwarder.gateway.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object SettingsTestTags {
    const val OPEN_DELIVERY_BUTTON = "settings_open_delivery_button"
    const val OPEN_FILTER_RULES_BUTTON = "settings_open_filter_rules_button"
    const val OPEN_DELIVERY_LOG_BUTTON = "settings_open_delivery_log_button"
}

@Composable
fun SettingsScreen(
    onOpenDelivery: () -> Unit = {},
    onOpenFilterRules: () -> Unit = {},
    onOpenDeliveryLog: () -> Unit = {},
) {
    SettingsContent(
        onOpenDelivery = onOpenDelivery,
        onOpenFilterRules = onOpenFilterRules,
        onOpenDeliveryLog = onOpenDeliveryLog,
    )
}

@Composable
fun SettingsContent(
    onOpenDelivery: () -> Unit = {},
    onOpenFilterRules: () -> Unit = {},
    onOpenDeliveryLog: () -> Unit = {},
) {
    Scaffold { padding ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = onOpenDelivery,
                    modifier = Modifier.testTag(SettingsTestTags.OPEN_DELIVERY_BUTTON),
                ) {
                    Text("Доставка")
                }
                TextButton(
                    onClick = onOpenFilterRules,
                    modifier = Modifier.testTag(SettingsTestTags.OPEN_FILTER_RULES_BUTTON),
                ) {
                    Text("Фильтрация SMS")
                }
                TextButton(
                    onClick = onOpenDeliveryLog,
                    modifier = Modifier.testTag(SettingsTestTags.OPEN_DELIVERY_LOG_BUTTON),
                ) {
                    Text("Лог доставки")
                }
            }
        }
    }
}
