package com.smsforwarder.gateway.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smsforwarder.gateway.OpenSenderRequest
import com.smsforwarder.gateway.data.local.db.FilterStage
import com.smsforwarder.gateway.ui.conversations.ConversationsScreen
import com.smsforwarder.gateway.ui.delivery.DeliveryScreen
import com.smsforwarder.gateway.ui.deliverylog.DeliveryLogScreen
import com.smsforwarder.gateway.ui.filters.FilterRuleEditScreen
import com.smsforwarder.gateway.ui.filters.FilterRulesScreen
import com.smsforwarder.gateway.ui.settings.SettingsScreen
import com.smsforwarder.gateway.ui.thread.ThreadScreen

private object Routes {
    const val CONVERSATIONS = "conversations"
    const val SETTINGS = "settings"
    const val DELIVERY = "delivery"
    const val DELIVERY_LOG = "delivery_log"
    const val THREAD = "thread/{sender}?messageId={messageId}"
    const val FILTER_RULES = "filter_rules"
    const val FILTER_RULE_EDIT = "filter_rules/edit/{stage}?id={id}"
    fun thread(sender: String, messageId: Long? = null) = "thread/$sender?messageId=${messageId ?: 0}"
    fun filterRuleEdit(stage: FilterStage, id: Long?) = "filter_rules/edit/${stage.name}?id=${id ?: 0}"
}

@Composable
fun GatewayNavGraph(openSender: OpenSenderRequest? = null) {
    val navController = rememberNavController()
    LaunchedEffect(openSender) {
        if (openSender != null) navController.navigate(Routes.thread(openSender.sender))
    }
    // Spec 0027: bottom NavigationBar removed - conversations is the sole top-level
    // screen, Settings is reached via a TopAppBar icon (like Delivery/FilterRules/
    // DeliveryLog already were) and popped back to, not tab-switched.
    NavHost(
        navController = navController,
        startDestination = Routes.CONVERSATIONS,
    ) {
        composable(Routes.CONVERSATIONS) {
            ConversationsScreen(
                onOpenThread = { sender, messageId -> navController.navigate(Routes.thread(sender, messageId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDelivery = { navController.navigate(Routes.DELIVERY) },
                onOpenFilterRules = { navController.navigate(Routes.FILTER_RULES) },
                onOpenDeliveryLog = { navController.navigate(Routes.DELIVERY_LOG) },
            )
        }
        composable(Routes.DELIVERY) {
            DeliveryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DELIVERY_LOG) {
            DeliveryLogScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.THREAD,
            arguments = listOf(
                navArgument("sender") { type = NavType.StringType },
                navArgument("messageId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { ThreadScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.FILTER_RULES) {
            FilterRulesScreen(
                onBack = { navController.popBackStack() },
                onAddRule = { stage -> navController.navigate(Routes.filterRuleEdit(stage, null)) },
                onEditRule = { id, stage -> navController.navigate(Routes.filterRuleEdit(stage, id)) },
            )
        }
        composable(
            Routes.FILTER_RULE_EDIT,
            arguments = listOf(
                navArgument("stage") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType; defaultValue = "0" },
            ),
        ) { FilterRuleEditScreen(onBack = { navController.popBackStack() }) }
    }
}
