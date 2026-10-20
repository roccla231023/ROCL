package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.model.MemoryFile

val Migration_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(31, 32)
        try {
            // Older ROCL migrations made embedding_model_id NOT NULL despite the nullable Room entity.
            db.execSQL("CREATE TABLE `memory_records_upgrade` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `assistant_id` TEXT NOT NULL, `content` TEXT NOT NULL, `embedding` TEXT DEFAULT NULL, `embedding_model_id` TEXT DEFAULT '', `type` INTEGER NOT NULL DEFAULT 0, `pinned` INTEGER NOT NULL DEFAULT 0, `last_accessed_at` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER DEFAULT NULL)")
            db.execSQL("INSERT INTO memory_records_upgrade (id, assistant_id, content, embedding, embedding_model_id, type, pinned, last_accessed_at, created_at, updated_at) SELECT id, assistant_id, content, embedding, embedding_model_id, type, pinned, last_accessed_at, created_at, updated_at FROM MemoryEntity")
            db.execSQL("DROP TABLE MemoryEntity")
            db.execSQL("ALTER TABLE memory_records_upgrade RENAME TO MemoryEntity")
            db.execSQL("ALTER TABLE ConversationEntity ADD COLUMN config TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE TABLE IF NOT EXISTS `memory_file` (`assistant_id` TEXT NOT NULL, `path` TEXT NOT NULL, `content` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`assistant_id`, `path`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `memory_consolidation` (`conversation_id` TEXT NOT NULL, `consolidated_at` INTEGER NOT NULL, PRIMARY KEY(`conversation_id`), FOREIGN KEY(`conversation_id`) REFERENCES `ConversationEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE TABLE IF NOT EXISTS `media_creation_session` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `draft` TEXT NOT NULL, `create_at` INTEGER NOT NULL, `update_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `media_creation_node` (`id` TEXT NOT NULL, `session_id` TEXT NOT NULL, `selected_record_id` TEXT NOT NULL, `create_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`session_id`) REFERENCES `media_creation_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_creation_node_session_id` ON `media_creation_node` (`session_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `media_creation_record` (`id` TEXT NOT NULL, `session_id` TEXT NOT NULL, `node_id` TEXT NOT NULL, `provider_id` TEXT NOT NULL, `provider_name` TEXT NOT NULL, `model_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `prompt` TEXT NOT NULL, `params` TEXT NOT NULL, `inputs` TEXT NOT NULL, `status` TEXT NOT NULL, `task_id` TEXT, `error` TEXT, `outputs` TEXT NOT NULL, `create_at` INTEGER NOT NULL, `update_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`session_id`) REFERENCES `media_creation_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`node_id`) REFERENCES `media_creation_node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_creation_record_session_id` ON `media_creation_record` (`session_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_creation_record_node_id` ON `media_creation_record` (`node_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `translation_history` (`id` TEXT NOT NULL, `source_text` TEXT NOT NULL, `translated_text` TEXT NOT NULL, `target_language` TEXT NOT NULL, `model_id` TEXT NOT NULL, `create_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_translation_history_create_at` ON `translation_history` (`create_at`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `usage_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `created_at` INTEGER NOT NULL, `source` TEXT NOT NULL, `provider_id` TEXT, `model_id` TEXT, `model_name` TEXT, `assistant_id` TEXT, `conversation_id` TEXT, `prompt_tokens` INTEGER NOT NULL, `completion_tokens` INTEGER NOT NULL, `cached_tokens` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_record_created_at` ON `usage_record` (`created_at`)")
            val memories = linkedMapOf<String, MutableList<String>>()
            db.query("SELECT assistant_id, content FROM MemoryEntity ORDER BY id").use { cursor ->
                while (cursor.moveToNext()) {
                    memories.getOrPut(cursor.getString(0)) { mutableListOf() }.add(cursor.getString(1))
                }
            }
            memories.forEach { (assistantId, contents) ->
                val profile = legacyMemoriesToProfile(contents) ?: return@forEach
                db.execSQL(
                    "INSERT INTO memory_file (assistant_id, path, content, updated_at) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(assistantId, MemoryFile.PROFILE_PATH, profile, System.currentTimeMillis()),
                )
            }
            db.execSQL(
                """
                INSERT INTO usage_record (
                    created_at, source, provider_id, model_id, model_name, assistant_id, conversation_id,
                    prompt_tokens, completion_tokens, cached_tokens
                )
                SELECT
                    COALESCE(
                        CAST(strftime('%s', substr(json_extract(j.value, '$.createdAt'), 1, 19), 'utc') AS INTEGER) * 1000,
                        c.create_at
                    ),
                    'CHAT',
                    NULL,
                    json_extract(j.value, '$.modelId'),
                    json_extract(j.value, '$.modelSnapshot.modelId'),
                    c.assistant_id,
                    mn.conversation_id,
                    COALESCE(json_extract(j.value, '$.usage.promptTokens'), 0),
                    COALESCE(json_extract(j.value, '$.usage.completionTokens'), 0),
                    COALESCE(json_extract(j.value, '$.usage.cachedTokens'), 0)
                FROM message_node mn
                JOIN ConversationEntity c ON c.id = mn.conversation_id,
                    json_each(CASE WHEN json_valid(mn.messages) THEN mn.messages ELSE '[]' END) j
                WHERE json_extract(j.value, '$.role') = 'assistant'
                    AND json_type(j.value, '$.usage') = 'object'
                    AND (
                        COALESCE(json_extract(j.value, '$.usage.promptTokens'), 0) > 0
                        OR COALESCE(json_extract(j.value, '$.usage.completionTokens'), 0) > 0
                    )
                                """.trimIndent()
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
