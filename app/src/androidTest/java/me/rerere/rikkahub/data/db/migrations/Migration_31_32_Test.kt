package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_31_32_Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun preservesChatAndArchivedRoleplayData() {
        helper.createDatabase("rocl-upgrade", 31).apply {
            execSQL("INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at) VALUES ('chat', 'assistant', 'Keep chat', '[]', 1, 2)")
            execSQL("INSERT INTO MemoryEntity (assistant_id, content) VALUES ('assistant', 'Keep memory')")
            execSQL("INSERT INTO rp_card (id, version, card_json, created_at, updated_at) VALUES ('card', 1, '{}', 1, 2)")
            execSQL("INSERT INTO rp_session (id, card_id, conversation_id, card_json, title, active_branch_id, revision, state_json, status, created_at, updated_at) VALUES ('session', 'card', 'legacy-chat', '{}', 'Keep story', 'branch', 3, '{}', 'ACTIVE', 1, 2)")
            execSQL("INSERT INTO rp_turn (id, session_id, branch_id, input, status, outcome_json, review_json, narrative, state_after_json, created_at) VALUES ('turn', 'session', 'branch', 'Keep input', 'COMPLETED', '{}', '{}', 'Keep narrative', '{}', 1)")
            execSQL("INSERT INTO usage_stats (id, total_conversations, total_messages, input_tokens, output_tokens, cached_tokens, app_launches) VALUES (1, 7, 19, 100, 40, 20, 4)")
            execSQL("INSERT INTO daily_activity (date, message_count, last_message_time) VALUES ('2026-10-01', 19, 1)")
            close()
        }
        helper.runMigrationsAndValidate("rocl-upgrade", 32, true, Migration_31_32).use { db ->
            db.query("SELECT title FROM ConversationEntity WHERE id = 'chat'").use {
                it.moveToFirst()
                assertEquals("Keep chat", it.getString(0))
            }
            db.query("SELECT card_json FROM rp_card WHERE id = 'card'").use {
                it.moveToFirst()
                assertEquals("{}", it.getString(0))
            }
            db.query("SELECT title, revision FROM rp_session WHERE id = 'session'").use {
                it.moveToFirst()
                assertEquals("Keep story", it.getString(0))
                assertEquals(3L, it.getLong(1))
            }
            db.query("SELECT input, narrative FROM rp_turn WHERE id = 'turn'").use {
                it.moveToFirst()
                assertEquals("Keep input", it.getString(0))
                assertEquals("Keep narrative", it.getString(1))
            }
            db.query("SELECT total_messages, input_tokens FROM usage_stats WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(19L, it.getLong(0))
                assertEquals(100L, it.getLong(1))
            }
            db.query("SELECT content FROM MemoryEntity").use {
                it.moveToFirst()
                assertEquals("Keep memory", it.getString(0))
            }
            db.query("SELECT content FROM memory_file WHERE assistant_id = 'assistant'").use {
                it.moveToFirst()
                assertEquals("---\nname: profile\ndescription: Who the user is\n---\n\n- [stated] Keep memory\n", it.getString(0))
            }
        }
    }
}
