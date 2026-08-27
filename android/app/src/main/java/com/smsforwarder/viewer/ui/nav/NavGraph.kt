package com.smsforwarder.viewer.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smsforwarder.viewer.data.local.ServerConfigStore
import com.smsforwarder.viewer.data.local.SessionEvents
import com.smsforwarder.viewer.data.repository.AuthRepository
import com.smsforwarder.viewer.ui.adddevice.AddDeviceScreen
import com.smsforwarder.viewer.ui.createdevice.CreateDeviceScreen
import com.smsforwarder.viewer.ui.devicedetail.DeviceDetailScreen
import com.smsforwarder.viewer.ui.devices.DeviceListScreen
import com.smsforwarder.viewer.ui.feed.MessageFeedScreen
import com.smsforwarder.viewer.ui.login.LoginScreen
import com.smsforwarder.viewer.ui.register.RegisterScreen
import com.smsforwarder.viewer.ui.serversetup.ServerSetupScreen
import com.smsforwarder.viewer.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.collectLatest
import java.net.URLDecoder
import java.net.URLEncoder

private object Routes {
    const val SERVER_SETUP = "server_setup"
    const val LOGIN = "login/{initialUsername}"
    const val REGISTER = "register"
    const val DEVICES = "devices"
    const val ADD_DEVICE = "add_device"
    const val CREATE_DEVICE = "create_device"
    const val FEED = "feed/{deviceId}/{deviceName}"
    const val DEVICE_DETAIL = "device_detail/{deviceId}/{deviceName}"
    const val SETTINGS = "settings"

    fun login(initialUsername: String = "") = "login/${URLEncoder.encode(initialUsername, "UTF-8")}"

    fun feed(deviceId: Long, deviceName: String): String {
        val encodedName = URLEncoder.encode(deviceName, "UTF-8")
        return "feed/$deviceId/$encodedName"
    }

    fun deviceDetail(deviceId: Long, deviceName: String): String {
        val encodedName = URLEncoder.encode(deviceName, "UTF-8")
        return "device_detail/$deviceId/$encodedName"
    }
}

@Composable
fun NavGraph(
    authRepository: AuthRepository,
    sessionEvents: SessionEvents,
    serverConfigStore: ServerConfigStore,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = when {
        !serverConfigStore.hasUrl() -> Routes.SERVER_SETUP
        authRepository.isLoggedIn() -> Routes.DEVICES
        else -> Routes.login()
    }

    LaunchedEffect(Unit) {
        sessionEvents.loggedOut.collectLatest {
            navController.navigate(Routes.login()) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SERVER_SETUP) {
            ServerSetupScreen(onSaved = {
                navController.navigate(Routes.login()) {
                    popUpTo(Routes.SERVER_SETUP) { inclusive = true }
                }
            })
        }
        composable(
            route = Routes.LOGIN,
            arguments = listOf(navArgument("initialUsername") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("initialUsername").orEmpty()
            LoginScreen(
                initialUsername = URLDecoder.decode(encoded, "UTF-8"),
                onLoggedIn = {
                    navController.navigate(Routes.DEVICES) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onCreateAccount = { navController.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = { username ->
                    navController.navigate(Routes.login(username)) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onBackToLogin = { navController.popBackStack() },
            )
        }
        composable(Routes.DEVICES) {
            DeviceListScreen(
                onAddDevice = { navController.navigate(Routes.ADD_DEVICE) },
                onOpenDevice = { deviceId, deviceName ->
                    navController.navigate(Routes.feed(deviceId, deviceName))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onManageDevice = { deviceId, deviceName ->
                    navController.navigate(Routes.deviceDetail(deviceId, deviceName))
                },
                onCreateDevice = { navController.navigate(Routes.CREATE_DEVICE) },
            )
        }
        composable(Routes.ADD_DEVICE) {
            AddDeviceScreen(onDeviceAdded = { navController.popBackStack() })
        }
        composable(Routes.CREATE_DEVICE) {
            CreateDeviceScreen(onDone = { navController.popBackStack() })
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
        composable(
            route = Routes.DEVICE_DETAIL,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.LongType },
                navArgument("deviceName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val encodedName = backStackEntry.arguments?.getString("deviceName").orEmpty()
            val deviceName = URLDecoder.decode(encodedName, "UTF-8")
            DeviceDetailScreen(deviceName = deviceName, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onLoggedOut = {
                    navController.navigate(Routes.login()) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onServerChangeRequested = {
                    navController.navigate(Routes.SERVER_SETUP) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
