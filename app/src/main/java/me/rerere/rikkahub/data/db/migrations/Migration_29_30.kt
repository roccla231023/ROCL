package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(29, 30)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `rp_session` (
                    `id` TEXT NOT NULL,
                    `card_id` TEXT NOT NULL,
                    `conversation_id` TEXT NOT NULL,
                    `card_json` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `active_branch_id` TEXT NOT NULL,
                    `revision` INTEGER NOT NULL,
                    `state_json` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_rp_session_conversation_id` ON `rp_session` (`conversation_id`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `rp_turn` (
                    `id` TEXT NOT NULL,
                    `session_id` TEXT NOT NULL,
                    `branch_id` TEXT NOT NULL,
                    `input` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `outcome_json` TEXT NOT NULL,
                    `review_json` TEXT NOT NULL,
                    `narrative` TEXT NOT NULL,
                    `state_after_json` TEXT NOT NULL,
                    `error` TEXT,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_rp_turn_session_id` ON `rp_turn` (`session_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_rp_turn_branch_id` ON `rp_turn` (`branch_id`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `rp_card` (
                    `id` TEXT NOT NULL,
                    `version` INTEGER NOT NULL,
                    `card_json` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            addColumnIfNotExists(db, "ConversationEntity", "rp_session_id", "TEXT NOT NULL DEFAULT ''")
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
            if (nameIndex != -1 && cursor.getString(nameIndex) == column) {
                return
            }
        }
    }
    db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $columnDef")
}
