package com.smsforwarder.gateway.sms

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.smsforwarder.gateway.MainActivity

/**
 * Required for default-SMS-app eligibility (SENDTO intent-filter, `sms:`/`mms:`
 * schemes). Hands off to MainActivity/GatewayNavGraph rather than hosting its
 * own screen, since ThreadScreen's ViewModel expects a Navigation-Compose
 * "sender" nav arg that only exists inside that NavHost.
 */
class SendToActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destination = intent?.data?.schemeSpecificPart?.substringBefore('?')
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_SENDER, destination)
        )
        finish()
    }
}
