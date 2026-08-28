package com.smsforwarder.gateway.sms

import android.content.Intent

/**
 * `subscription`/`android.telephony.extra.SLOT_INDEX` are the modern,
 * officially documented extras SMS_DELIVER carries the SIM slot in - no
 * need for the third-party Gateway App's ~9-key Bundle-name heuristic,
 * which existed only to compensate for its use of the unofficial
 * SMS_RECEIVED extras rather than the standard SmsMessage/telephony APIs.
 *
 * `resolve(Int?, Int?)` is the pure, JVM-testable core; `resolve(Intent)` is a
 * thin adapter so callers don't have to read extras themselves.
 */
object SimSlotResolver {
    fun resolve(slotIndexExtra: Int?, legacySlotExtra: Int?): Int? {
        if (slotIndexExtra != null && slotIndexExtra >= 0) return slotIndexExtra
        if (legacySlotExtra != null && legacySlotExtra >= 0) return legacySlotExtra
        return null
    }

    fun resolve(intent: Intent): Int? = resolve(
        slotIndexExtra = if (intent.hasExtra("android.telephony.extra.SLOT_INDEX")) {
            intent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1)
        } else null,
        legacySlotExtra = if (intent.hasExtra("slot")) {
            intent.getIntExtra("slot", -1)
        } else null,
    )
}
