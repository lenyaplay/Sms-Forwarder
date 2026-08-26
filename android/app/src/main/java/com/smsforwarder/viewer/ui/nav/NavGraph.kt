package com.smsforwarder.viewer.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smsforwarder.viewer.data.local.SessionEvents
import com.smsforwarder.viewer.data.repository.AuthRepository
import com.smsforwarder.viewer.ui.adddevice.AddDeviceScreen
import com.smsforwarder.viewer.ui.devices.DeviceListScreen
import com.smsforwarder.viewer.ui.feed.MessageFeedScreen
import com.smsforwarder.viewer.ui.login.LoginScreen
import kotlinx.coroutines.flow.collectLatest
import java.net.URLDecoder
import java.net.URLEncoder

private object Routes {
    const val LOGIN = "login"
    const val DEVICES = "devices"
    const val ADD_DEVICE = "add_device"
    const val FEED = "feed/{deviceId}/{deviceName}"

    fun feed(deviceId: Long, deviceName: String): String {
        val encodedName = URLEncoder.encode(deviceName, "UTF-8")
        return "feed/$deviceId/$encodedName"
    }
}

@Composable
fun NavGraph(
    authRepository: AuthRepository,
    sessionEvents: SessionEvents,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = if (authRepository.isLoggedIn()) Routes.DEVICES else Routes.LOGIN

    LaunchedEffect(Unit) {
        sessionEvents.loggedOut.collectLatest {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.DEVICES) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.DEVICES) {
            DeviceListScreen(
                onAddDevice = { navController.navigate(Routes.ADD_DEVICE) },
                onOpenDevice = { deviceId, deviceName ->
                    navController.navigate(Routes.feed(deviceId, deviceName))
                },
            )
        }
        composable(Routes.ADD_DEVICE) {
            AddDeviceScreen(onDeviceAdded = { navController.popBackStack() })
        }
        composable(
            route = Routes.FEED,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.LongType },
                navArgument("deviceName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val encodedName = backStackEntry.arguments?.getString("deviceName").orEmpty()
            val deviceName = URLDecoder.decode(encodedName, "UTF-8")
            MessageFeedScreen(deviceName = deviceName)
        }
    }
}
