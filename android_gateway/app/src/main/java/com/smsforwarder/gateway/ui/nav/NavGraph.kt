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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smsforwarder.gateway.ui.messages.MessagesScreen
import com.smsforwarder.gateway.ui.settings.SettingsScreen

private object Routes {
    const val MESSAGES = "messages"
    const val SETTINGS = "settings"
}

@Composable
fun GatewayNavGraph() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.MESSAGES } == true,
                    onClick = { navController.navigate(Routes.MESSAGES) { launchSingleTop = true; popUpTo(navController.graph.findStartDestination().id) } },
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
            startDestination = Routes.MESSAGES,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.MESSAGES) { MessagesScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
