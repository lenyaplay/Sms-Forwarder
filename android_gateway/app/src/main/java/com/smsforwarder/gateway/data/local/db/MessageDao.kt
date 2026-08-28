package com.smsforwarder.gateway.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
        SELECT sender, text, createdAt, deliveryStatus, direction FROM messages AS m
        WHERE createdAt = (SELECT MAX(createdAt) FROM messages WHERE sender = m.sender)
        ORDER BY createdAt DESC
        """
    )
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE sender = :sender ORDER BY createdAt ASC")
    fun observeThread(sender: String): Flow<List<MessageEntity>>
}
