package com.smsforwarder.gateway.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ConversationEntity(
    val sender: String,
    val text: String,
    val createdAt: Long,
    val deliveryStatus: DeliveryStatus,
    val direction: MessageDirection,
)

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    // NOT_FORWARDED excluded deliberately - a forwarding-stage filter block is a
    // decision, not a delivery failure, so it must not get swept up automatically.
    @Query("SELECT * FROM messages WHERE deliveryStatus NOT IN ('SENT', 'NOT_FORWARDED')")
    suspend fun getUndelivered(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE deliveryStatus = 'FAILED'")
    suspend fun getFailed(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE deliveryStatus = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT * FROM messages ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    // Two real messages from the same sender can share the exact same createdAt
    // (batch-delivered content://sms rows after the device was offline) - a plain
    // `createdAt = MAX(createdAt)` WHERE clause then matches BOTH rows instead of
    // picking one, producing two conversation rows for one sender (crashes the
    // LazyColumn's `key = { it.sender }` in ConversationsScreen). GROUP BY sender
    // guarantees exactly one row per sender; the correlated subqueries pick that
    // row's own text/deliveryStatus/direction, using `id DESC` as a tiebreaker for
    // the (rare) case of two messages tied on createdAt too. (Room's compile-time
    // SQL validator doesn't parse window functions/subqueries-in-FROM, so ROW_NUMBER
    // isn't an option here.)
    @Query(
        """
        SELECT m.sender,
               (SELECT text FROM messages WHERE sender = m.sender ORDER BY createdAt DESC, id DESC LIMIT 1) AS text,
               MAX(m.createdAt) AS createdAt,
               (SELECT deliveryStatus FROM messages WHERE sender = m.sender ORDER BY createdAt DESC, id DESC LIMIT 1) AS deliveryStatus,
               (SELECT direction FROM messages WHERE sender = m.sender ORDER BY createdAt DESC, id DESC LIMIT 1) AS direction
        FROM messages AS m
        LEFT JOIN conversation_meta AS cm ON cm.sender = m.sender
        WHERE COALESCE(cm.isArchived, 0) = :archived
        GROUP BY m.sender
        ORDER BY createdAt DESC
        """
    )
    fun observeConversations(archived: Boolean): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE sender = :sender ORDER BY createdAt ASC")
    fun observeThread(sender: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE text LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Query("SELECT systemSmsId FROM messages WHERE id = :id")
    suspend fun getSystemSmsId(id: Long): Long?

    @Query("SELECT systemSmsId FROM messages WHERE sender = :sender AND systemSmsId IS NOT NULL")
    suspend fun getSystemSmsIdsForSender(sender: String): List<Long>

    /**
     * Backfill target for a content://sms row this app already stored (via
     * SMS_DELIVER or sendMessage) but hasn't yet linked to its system-provider
     * id. Matched by (sender, approximate timestamp) within a small window -
     * content://sms's DATE/DATE_SENT columns don't reliably equal our own
     * sentStamp/receivedStamp millisecond-for-millisecond (carrier PDU time
     * vs. local receipt/send time, OEM-dependent), so an exact-equality match
     * would rarely hit. Not guaranteed unique for two messages from the same
     * sender within the window - a known/accepted limitation at this
     * project's personal-use scale (see spec 0018).
     */
    @Query(
        """
        SELECT * FROM messages WHERE sender = :sender AND systemSmsId IS NULL
        AND (receivedStamp BETWEEN :timestamp - 2000 AND :timestamp + 2000
             OR sentStamp BETWEEN :timestamp - 2000 AND :timestamp + 2000)
        ORDER BY MIN(ABS(receivedStamp - :timestamp), ABS(IFNULL(sentStamp, :timestamp) - :timestamp)) ASC LIMIT 1
        """
    )
    suspend fun findUnmatchedForBackfill(sender: String, timestamp: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE sender = :sender")
    suspend fun deleteBySender(sender: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversation_meta WHERE sender = :sender")
    suspend fun deleteConversationMeta(sender: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConversationMeta(meta: ConversationMetaEntity)

    /** Atomic so a process death mid-delete can't leave an orphaned conversation_meta row. */
    @Transaction
    suspend fun deleteConversationAndMeta(sender: String) {
        deleteBySender(sender)
        deleteConversationMeta(sender)
    }
}
