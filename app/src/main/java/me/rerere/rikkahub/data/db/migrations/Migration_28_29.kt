package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(28, 29)
        try {
            db.execSQL(
                "ALTER TABLE ConversationEntity ADD COLUMN session_memories TEXT NOT NULL DEFAULT '[]'"
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
