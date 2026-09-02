package com.smsforwarder.gateway.data.local

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import android.util.Log
import com.smsforwarder.gateway.data.local.db.DeliveryStatus
import com.smsforwarder.gateway.data.local.db.MessageDao
import com.smsforwarder.gateway.data.local.db.MessageDirection
import com.smsforwarder.gateway.data.local.db.MessageEntity
import com.smsforwarder.gateway.data.perf.PerfMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val HISTORY_PROJECTION = arrayOf(
    Telephony.Sms._ID,
    Telephony.Sms.ADDRESS,
    Telephony.Sms.BODY,
    Telephony.Sms.DATE,
    Telephony.Sms.TYPE,
)

/** Maps one content://sms row to a MessageEntity - shared by the full import and the incremental sync. */
internal fun Cursor.toMessageEntity(idCol: Int, addressCol: Int, bodyCol: Int, dateCol: Int, typeCol: Int): MessageEntity? {
    val address = if (addressCol >= 0) getString(addressCol) else null
    if (address.isNullOrBlank()) return null
    val date = if (dateCol >= 0) getLong(dateCol) else System.currentTimeMillis()
    val direction = if (typeCol >= 0 && getInt(typeCol) == Telephony.Sms.MESSAGE_TYPE_SENT) {
        MessageDirection.OUT
    } else {
        MessageDirection.IN
    }
    return MessageEntity(
        sender = address,
        text = if (bodyCol >= 0) getString(bodyCol).orEmpty() else "",
        sentStamp = date,
        receivedStamp = date,
        simSlot = null,
        deliveryStatus = DeliveryStatus.SENT,
        createdAt = date,
        direction = direction,
    )
}

/**
 * A row already stored by SmsDeliverReceiver/sendMessage (approximately
 * matching sender+timestamp, see MessageDao.findUnmatchedForBackfill) gets
 * its systemSmsId linked instead of being duplicated; a row with no local
 * match (direct third-party writes to content://sms, or plain history import
 * on first run) is inserted fresh, carrying the system row id from the start.
 */
private suspend fun MessageDao.upsertFromSystemProvider(rowId: Long, entity: MessageEntity) {
    val existing = findUnmatchedForBackfill(entity.sender, entity.createdAt)
    if (existing != null) {
        update(existing.copy(systemSmsId = rowId))
    } else {
        insert(entity.copy(systemSmsId = rowId))
    }
}

/**
 * Imports the system content://sms provider into our own Room DB - once in
 * full after this app first becomes the default SMS handler, and
 * incrementally afterwards for rows other apps write directly to that
 * provider (an OEM dialer's "decline with message" quick-reply was found to
 * do exactly this, bypassing SMS_DELIVER/RESPOND_VIA_MESSAGE entirely).
 * Imported/synced rows are marked SENT and never forwarded to the webhook -
 * 0003's contract covers newly-arriving SMS_DELIVER messages only.
 */
@Singleton
open class SmsHistoryImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    private val configStore: GatewayConfigStore,
    private val perfMonitor: PerfMonitor,
) {
    private val _isImporting = MutableStateFlow(false)
    open val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    // Serializes importIfNeeded()/syncNewMessages() - both read-then-write the
    // same watermark, and the ContentObserver callback plus MainActivity's
    // ON_RESUME fallback can otherwise both fire syncNewMessages() at once,
    // double-inserting whatever content://sms rows arrived since the last sync.
    private val mutex = Mutex()

    open suspend fun importIfNeeded() {
        if (configStore.isHistoryImported()) return
        _isImporting.value = true
        try {
            perfMonitor.measure("history_import") {
                mutex.withLock {
                    withContext(Dispatchers.IO) {
                        var maxRowId = 0L
                        context.contentResolver.query(Telephony.Sms.CONTENT_URI, HISTORY_PROJECTION, null, null, null)?.use { cursor ->
                            val idCol = cursor.getColumnIndex(Telephony.Sms._ID)
                            val addressCol = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                            val bodyCol = cursor.getColumnIndex(Telephony.Sms.BODY)
                            val dateCol = cursor.getColumnIndex(Telephony.Sms.DATE)
                            val typeCol = cursor.getColumnIndex(Telephony.Sms.TYPE)
                            while (cursor.moveToNext()) {
                                val rowId = if (idCol >= 0) cursor.getLong(idCol) else 0L
                                if (rowId > maxRowId) maxRowId = rowId
                                cursor.toMessageEntity(idCol, addressCol, bodyCol, dateCol, typeCol)?.let { messageDao.upsertFromSystemProvider(rowId, it) }
                            }
                        }
                        configStore.setLastSyncedSmsRowId(maxRowId)
                        configStore.markHistoryImported()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsHistoryImporter", "content://sms history import failed", e)
        } finally {
            _isImporting.value = false
        }
    }

    /** Picks up rows written directly to content://sms since the last full import/sync, without rescanning the whole provider. */
    open suspend fun syncNewMessages() {
        if (!configStore.isHistoryImported()) return
        try {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    val since = configStore.lastSyncedSmsRowId()
                    var maxRowId = since
                    context.contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        HISTORY_PROJECTION,
                        "${Telephony.Sms._ID} > ?",
                        arrayOf(since.toString()),
                        "${Telephony.Sms._ID} ASC",
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(Telephony.Sms._ID)
                        val addressCol = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyCol = cursor.getColumnIndex(Telephony.Sms.BODY)
                        val dateCol = cursor.getColumnIndex(Telephony.Sms.DATE)
                        val typeCol = cursor.getColumnIndex(Telephony.Sms.TYPE)
                        while (cursor.moveToNext()) {
                            val rowId = if (idCol >= 0) cursor.getLong(idCol) else since
                            if (rowId > maxRowId) maxRowId = rowId
                            cursor.toMessageEntity(idCol, addressCol, bodyCol, dateCol, typeCol)?.let { messageDao.upsertFromSystemProvider(rowId, it) }
                        }
                    }
                    if (maxRowId != since) configStore.setLastSyncedSmsRowId(maxRowId)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsHistoryImporter", "content://sms incremental sync failed", e)
        }
    }
}
