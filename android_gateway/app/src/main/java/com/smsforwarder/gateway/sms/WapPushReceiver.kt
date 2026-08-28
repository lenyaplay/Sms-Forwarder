package com.smsforwarder.gateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Required for default-SMS-app eligibility (WAP_PUSH_DELIVER_ACTION). MMS
 * content handling is out of scope for this stage (spec 0013 assumption 5) -
 * this only needs to not crash and not block SMS delivery.
 */
class WapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Intentionally a no-op for now.
    }
}
