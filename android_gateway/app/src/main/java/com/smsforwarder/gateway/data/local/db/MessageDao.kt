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

    @Query("SELECT * FROM messages WHERE deliveryStatus != 'SENT'")
    suspend fun getUndelivered(): List<MessageEntity>

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
