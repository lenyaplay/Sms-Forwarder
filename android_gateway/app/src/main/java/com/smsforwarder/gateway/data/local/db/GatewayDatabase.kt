package com.smsforwarder.gateway.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, ConversationMetaEntity::class, FilterRuleEntity::class, DeliveryLogEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class GatewayDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun deliveryLogDao(): DeliveryLogDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS filter_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        stage TEXT NOT NULL,
                        senderPattern TEXT,
                        senderIsRegex INTEGER NOT NULL,
                        subscriptionId INTEGER,
                        contentPattern TEXT,
                        contentIsRegex INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS delivery_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sender TEXT NOT NULL,
                        attemptNumber INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        success INTEGER NOT NULL,
                        errorMessage TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_sender_createdAt ON messages(sender, createdAt)")
            }
        }
    }
}
