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

    @Query(
        """
        SELECT m.sender, m.text, m.createdAt, m.deliveryStatus, m.direction FROM messages AS m
        LEFT JOIN conversation_meta AS cm ON cm.sender = m.sender
        WHERE m.createdAt = (SELECT MAX(createdAt) FROM messages WHERE sender = m.sender)
        AND COALESCE(cm.isArchived, 0) = :archived
        ORDER BY m.createdAt DESC
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
