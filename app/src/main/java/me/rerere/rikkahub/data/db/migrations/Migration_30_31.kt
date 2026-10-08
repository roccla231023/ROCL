package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(30, 31)
        try {
            addColumnIfNotExists(db, "rp_session", "story_memory_json", "TEXT NOT NULL DEFAULT '{}'")
            addColumnIfNotExists(db, "rp_turn", "state_before_json", "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, "rp_turn", "event_json", "TEXT NOT NULL DEFAULT ''")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

private fun addColumnIfNotExists(
    db: SupportSQLiteDatabase,
    table: String,
    column: String,
    columnDef: String,
) {
    db.query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIndex != -1 && cursor.getString(nameIndex) == column) return
        }
    }
    db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $columnDef")
}
