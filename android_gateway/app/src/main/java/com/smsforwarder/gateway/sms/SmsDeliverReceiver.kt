package com.smsforwarder.gateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.smsforwarder.gateway.data.repository.MessageRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SMS_DELIVER_ACTION - only delivered to the current default SMS app, and
 * (unlike SMS_RECEIVED) not an ordered broadcast another app can abort before
 * this receiver runs. See docs/specs/0013-android-gateway-app.md context.
 */
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {

    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var notifier: IncomingSmsNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val text = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val sentStamp = messages[0].timestampMillis
        val receivedStamp = System.currentTimeMillis()
        val simSlot = detectSimSlot(intent)

        val pendingResult = goAsync()
        scope.launch {
            try {
                messageRepository.storeAndForward(sender, text, sentStamp, receivedStamp, simSlot)
                notifier.notifyIncoming(sender, text)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * `subscription`/`android.telephony.extra.SLOT_INDEX` are the modern,
     * officially documented extras SMS_DELIVER carries the SIM slot in - no
     * need for the third-party Gateway App's ~9-key Bundle-name heuristic,
     * which existed only to compensate for its use of the unofficial
     * SMS_RECEIVED extras rather than the standard SmsMessage/telephony APIs.
     */
    private fun detectSimSlot(intent: Intent): Int? {
        if (intent.hasExtra("android.telephony.extra.SLOT_INDEX")) {
            val slot = intent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1)
            if (slot >= 0) return slot
        }
        if (intent.hasExtra("slot")) {
            val slot = intent.getIntExtra("slot", -1)
            if (slot >= 0) return slot
        }
        return null
    }
}
