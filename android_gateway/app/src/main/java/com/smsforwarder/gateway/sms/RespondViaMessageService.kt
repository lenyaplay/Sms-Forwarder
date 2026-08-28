package com.smsforwarder.gateway.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log

/**
 * Required for default-SMS-app eligibility (RESPOND_VIA_MESSAGE, e.g. "quick
 * reply" when declining a call). Sends the given text with no UI - a full
 * quick-reply UI is out of scope for this stage.
 */
class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destination = intent?.data?.schemeSpecificPart
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (destination != null && !text.isNullOrEmpty()) {
            try {
                SmsManager.getDefault().sendTextMessage(destination, null, text, null, null)
            } catch (e: Exception) {
                Log.e("RespondViaMessage", "failed to send quick reply", e)
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
