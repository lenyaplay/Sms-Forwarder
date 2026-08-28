package com.smsforwarder.gateway.data.local

import android.content.Context
import android.provider.Telephony
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDao
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time import of the system content://sms provider into our own Room DB,
 * run after this app first becomes the default SMS handler. Imported rows are
 * marked SENT (for outgoing) - they are pre-existing history, not new
 * incoming SMS, so they must not be forwarded to the webhook (0003's contract
 * covers newly-arriving messages only).
 */
@Singleton
open class SmsHistoryImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    private val configStore: GatewayConfigStore,
) {
    open suspend fun importIfNeeded() {
        if (configStore.isHistoryImported()) return
        withContext(Dispatchers.IO) {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                null,
                null,
                null,
            )?.use { cursor ->
                val addressCol = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyCol = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateCol = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeCol = cursor.getColumnIndex(Telephony.Sms.TYPE)
                while (cursor.moveToNext()) {
                    val address = if (addressCol >= 0) cursor.getString(addressCol) else null
                    if (address.isNullOrBlank()) continue
                    val date = if (dateCol >= 0) cursor.getLong(dateCol) else System.currentTimeMillis()
                    val direction = if (typeCol >= 0 && cursor.getInt(typeCol) == Telephony.Sms.MESSAGE_TYPE_SENT) {
                        MessageDirection.OUT
                    } else {
                        MessageDirection.IN
                    }
                    messageDao.insert(
                        MessageEntity(
                            sender = address,
                            text = if (bodyCol >= 0) cursor.getString(bodyCol).orEmpty() else "",
                            sentStamp = date,
                            receivedStamp = date,
                            simSlot = null,
                            deliveryStatus = DeliveryStatus.SENT,
                            createdAt = date,
                            direction = direction,
                        )
                    )
                }
            }
            configStore.markHistoryImported()
        }
    }
}
