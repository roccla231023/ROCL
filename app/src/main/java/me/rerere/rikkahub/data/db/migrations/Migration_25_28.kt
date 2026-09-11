package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_25_28 = object : Migration(25, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(25, 28)
        try {
            db.execSQL(
                "ALTER TABLE ConversationEntity ADD COLUMN sticky_speaker_seat_id TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN embedding TEXT DEFAULT NULL")
            db.execSQL(
                "ALTER TABLE MemoryEntity ADD COLUMN embedding_model_id TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN type INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "ALTER TABLE MemoryEntity ADD COLUMN last_accessed_at INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN updated_at INTEGER DEFAULT NULL")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `usage_stats` (
                    `id` INTEGER NOT NULL,
                    `total_conversations` INTEGER NOT NULL,
                    `total_messages` INTEGER NOT NULL,
                    `input_tokens` INTEGER NOT NULL,
                    `output_tokens` INTEGER NOT NULL,
                    `cached_tokens` INTEGER NOT NULL,
                    `app_launches` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `daily_activity` (
                    `date` TEXT NOT NULL,
                    `message_count` INTEGER NOT NULL,
                    `last_message_time` INTEGER NOT NULL,
                    PRIMARY KEY(`date`)
                )
                """.trimIndent()
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
