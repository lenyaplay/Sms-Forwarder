package com.smsforwarder.gateway.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MessageEntity::class, ConversationMetaEntity::class], version = 3, exportSchema = true)
abstract class GatewayDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN direction TEXT NOT NULL DEFAULT 'IN'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS conversation_meta (sender TEXT NOT NULL, isArchived INTEGER NOT NULL, PRIMARY KEY(sender))"
                )
            }
        }
    }
}
