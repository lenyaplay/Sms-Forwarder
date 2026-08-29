package com.smsforwarder.gateway.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
fun GatewayNavGraph(openSender: String? = null) {
    val navController = rememberNavController()
    LaunchedEffect(openSender) {
        if (openSender != null) navController.navigate(Routes.thread(openSender))
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.CONVERSATIONS } == true,
                    onClick = { navController.navigate(Routes.CONVERSATIONS) { launchSingleTop = true; popUpTo(navController.graph.findStartDestination().id) } },
                    icon = { Icon(Icons.Default.Email, contentDescription = null) },
                    label = { Text("Сообщения") },
                )
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.SETTINGS } == true,
                    onClick = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true; popUpTo(navController.graph.findStartDestination().id) } },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Настройки") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CONVERSATIONS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CONVERSATIONS) {
                ConversationsScreen(onOpenThread = { sender, messageId -> navController.navigate(Routes.thread(sender, messageId)) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
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
}
