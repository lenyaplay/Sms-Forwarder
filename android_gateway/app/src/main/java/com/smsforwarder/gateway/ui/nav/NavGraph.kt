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
import com.smsforwarder.gateway.ui.conversations.ConversationsScreen
import com.smsforwarder.gateway.ui.settings.SettingsScreen
import com.smsforwarder.gateway.ui.thread.ThreadScreen

private object Routes {
    const val CONVERSATIONS = "conversations"
    const val SETTINGS = "settings"
    const val THREAD = "thread/{sender}"
    fun thread(sender: String) = "thread/$sender"
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
                ConversationsScreen(onOpenThread = { sender -> navController.navigate(Routes.thread(sender)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                Routes.THREAD,
                arguments = listOf(navArgument("sender") { type = NavType.StringType }),
            ) { ThreadScreen() }
        }
    }
}
