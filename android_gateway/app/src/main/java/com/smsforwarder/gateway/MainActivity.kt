package com.smsforwarder.gateway

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smsforwarder.gateway.ui.nav.GatewayNavGraph
import com.smsforwarder.gateway.ui.theme.SmsForwarderGatewayTheme
import dagger.hilt.android.AndroidEntryPoint

object MainActivityTestTags {
    const val REQUEST_DEFAULT_SMS_BUTTON = "request_default_sms_button"
    const val IS_DEFAULT_LABEL = "is_default_sms_label"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Mutable state, not a local val: SendToActivity launches this Activity with
    // FLAG_ACTIVITY_CLEAR_TOP, which Android delivers to an already-running
    // instance via onNewIntent() instead of a fresh onCreate() - without this,
    // the extra would silently be dropped whenever the app is already open.
    private val openSender = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openSender.value = intent?.getStringExtra(EXTRA_OPEN_SENDER)
        setContent {
            SmsForwarderGatewayTheme {
                MainContent(openSender = openSender.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSender.value = intent.getStringExtra(EXTRA_OPEN_SENDER)
    }

    companion object {
        const val EXTRA_OPEN_SENDER = "open_sender"
    }
}

@Composable
private fun MainContent(openSender: String? = null) {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isDefaultSmsApp(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultSmsApp(context)
    }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Recheck on every resume, not only via the role-request launcher callback -
    // the user can also change the default SMS app from system Settings
    // directly while this Activity is merely backgrounded, not recreated.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefault = isDefaultSmsApp(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (isDefault) {
        val mainViewModel: MainViewModel = hiltViewModel()
        LaunchedEffect(Unit) { mainViewModel.importHistoryIfNeeded() }
        GatewayNavGraph(openSender = openSender)
    } else {
        Scaffold { padding ->
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text(
                    "Чтобы приложение надёжно принимало SMS, назначьте его приложением по умолчанию для SMS.",
                    modifier = Modifier.testTag(MainActivityTestTags.IS_DEFAULT_LABEL),
                )
                Button(
                    onClick = { launcher.launch(defaultSmsRequestIntent(context)) },
                    modifier = Modifier.testTag(MainActivityTestTags.REQUEST_DEFAULT_SMS_BUTTON),
                ) {
                    Text("Сделать приложением по умолчанию")
                }
            }
        }
    }
}

/**
 * Telephony.Sms.getDefaultSmsPackage() reads the legacy
 * Settings.Secure.SMS_DEFAULT_APPLICATION value, which at least one real
 * device tested this session (TECNO LI9 / HiOS) never populates even after
 * RoleManager has genuinely granted ROLE_SMS to this app (confirmed via
 * `adb shell cmd role get-role-holders android.app.role.SMS` while
 * `settings get secure sms_default_application` stayed null) - relying on it
 * alone would permanently show the "not default" screen on such a device
 * even though SMS_DELIVER is actually being delivered correctly. RoleManager
 * is the actual source of truth on API 29+; the legacy check is only a
 * fallback for API < 29, where RoleManager doesn't exist.
 */
private fun isDefaultSmsApp(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
    } else {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}

private fun defaultSmsRequestIntent(context: android.content.Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
    } else {
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
    }
}
