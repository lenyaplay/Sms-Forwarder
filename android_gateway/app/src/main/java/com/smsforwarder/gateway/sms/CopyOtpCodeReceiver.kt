package com.smsforwarder.gateway.sms

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Spec 0038: fired by a notification action button. Not @AndroidEntryPoint - needs
 * no injected dependency, just the code to copy (passed as an intent extra by
 * IncomingSmsNotifier, which already did the OTP extraction).
 */
class CopyOtpCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_CODE) ?: return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("OTP code", code))
        Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_CODE = "code"
    }
}
