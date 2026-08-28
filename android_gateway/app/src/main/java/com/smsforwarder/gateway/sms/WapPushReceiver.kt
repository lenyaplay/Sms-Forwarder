package com.smsforwarder.gateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smsforwarder.gateway.data.local.db.MessageDao
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Required for default-SMS-app eligibility (WAP_PUSH_DELIVER_ACTION). MMS
 * content parsing is out of scope for this stage (spec 0013 assumption 5) -
 * but a silent no-op made an arriving MMS indistinguishable from a lost
 * message, so this stores a placeholder row instead of dropping it entirely.
 * Not forwarded to the webhook - 0003's contract covers text SMS only.
 */
@AndroidEntryPoint
class WapPushReceiver : BroadcastReceiver() {

    @Inject lateinit var messageDao: MessageDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender") ?: "MMS"
        val pendingResult = goAsync()
        scope.launch {
            try {
                messageDao.insert(
                    MessageEntity(
                        sender = sender,
                        text = "[MMS] содержимое не поддерживается",
                        sentStamp = null,
                        receivedStamp = System.currentTimeMillis(),
                        simSlot = null,
                        deliveryStatus = DeliveryStatus.SENT,
                        createdAt = System.currentTimeMillis(),
                        direction = MessageDirection.IN,
                    )
                )
            } catch (e: Exception) {
                Log.e("WapPushReceiver", "failed to store MMS placeholder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
