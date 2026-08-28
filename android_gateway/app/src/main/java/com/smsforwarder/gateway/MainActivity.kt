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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.smsforwarder.gateway.ui.nav.GatewayNavGraph
import com.smsforwarder.gateway.ui.theme.SmsForwarderGatewayTheme
import dagger.hilt.android.AndroidEntryPoint

object MainActivityTestTags {
    const val REQUEST_DEFAULT_SMS_BUTTON = "request_default_sms_button"
    const val IS_DEFAULT_LABEL = "is_default_sms_label"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmsForwarderGatewayTheme {
                MainContent()
            }
        }
    }
}

@Composable
private fun MainContent() {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isDefaultSmsApp(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultSmsApp(context)
    }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (isDefault) {
        GatewayNavGraph()
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

private fun isDefaultSmsApp(context: android.content.Context): Boolean =
    Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

private fun defaultSmsRequestIntent(context: android.content.Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
    } else {
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
    }
}
