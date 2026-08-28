package com.smsforwarder.gateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
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
        val simSlot = SimSlotResolver.resolve(intent)

        val pendingResult = goAsync()
        scope.launch {
            try {
                // Catches broadly (not just IOException/etc.) - a Room or
                // notification failure must never crash the process here: the
                // SMS_DELIVER broadcast has already been consumed at this
                // point, so an uncaught exception wouldn't retry the delivery,
                // it would just take down the app for what should be a
                // logged, recoverable failure.
                messageRepository.storeAndForward(sender, text, sentStamp, receivedStamp, simSlot)
                notifier.notifyIncoming(sender, text)
            } catch (e: Exception) {
                Log.e("SmsDeliverReceiver", "failed to store/forward incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
