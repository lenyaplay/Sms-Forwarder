package com.smsforwarder.gateway.data.remote

import android.telephony.SmsManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class OutgoingSmsSender @Inject constructor() {
    open fun send(destination: String, text: String, subscriptionId: Int?) {
        val smsManager = if (subscriptionId != null) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getDefault()
        }
        smsManager.sendTextMessage(destination, null, text, null, null)
    }
}
