package com.smsforwarder.gateway.sms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

/**
 * Required for default-SMS-app eligibility (SENDTO intent-filter, `sms:`/`mms:`
 * schemes). A full compose-a-message UI is a later stage (spec 0013) - this
 * stub only needs to accept the intent without crashing.
 */
class SendToActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destination = intent?.data?.schemeSpecificPart
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Text(
                        text = "Отправка сообщений появится в следующем этапе. Получатель: ${destination.orEmpty()}",
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}
