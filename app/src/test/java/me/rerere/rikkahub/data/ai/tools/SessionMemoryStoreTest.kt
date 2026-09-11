package me.rerere.rikkahub.data.ai.tools

import me.rerere.rikkahub.data.model.SessionMemoryPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMemoryStoreTest {
    @Test
    fun createAssignsIncrementalIdsAndPlacement() {
        val first = SessionMemoryStore.create(
            current = emptyList(),
            content = "User wants act 2 outline",
            placement = SessionMemoryPlacement.BEFORE_LATEST_MESSAGE,
        )
        val second = SessionMemoryStore.create(
            current = listOf(first),
            content = "Stable world rules",
            placement = SessionMemoryPlacement.SYSTEM_PROMPT_AFTER,
        )
        assertEquals(1, first.id)
        assertEquals(2, second.id)
        assertEquals(SessionMemoryPlacement.SYSTEM_PROMPT_AFTER, second.placement)
    }

    @Test
    fun createSameContentUpdatesPlacementInsteadOfDuplicating() {
        val existing = SessionMemoryStore.create(
            current = emptyList(),
            content = "Keep outline",
            placement = SessionMemoryPlacement.BEFORE_LATEST_MESSAGE,
        )
        val result = SessionMemoryStore.create(
            current = listOf(existing),
            content = "keep outline",
            placement = SessionMemoryPlacement.SYSTEM_PROMPT_AFTER,
        )
        assertEquals(existing.id, result.id)
        assertEquals(SessionMemoryPlacement.SYSTEM_PROMPT_AFTER, result.placement)
    }

    @Test
    fun createRejectsBlankAndOverLimit() {
        runCatching {
            SessionMemoryStore.create(emptyList(), "  ", SessionMemoryPlacement.BEFORE_LATEST_MESSAGE)
        }.onFailure { error ->
            assertTrue(error.message.orEmpty().contains("empty"))
        }.onSuccess { error("expected failure") }

        val filled = (1..SessionMemoryStore.MAX_COUNT).map { id ->
            SessionMemoryStore.create(
                current = emptyList(),
                content = "item $id",
                placement = SessionMemoryPlacement.BEFORE_LATEST_MESSAGE,
            ).copy(id = id)
        }
        runCatching {
            SessionMemoryStore.create(filled, "one more", SessionMemoryPlacement.BEFORE_LATEST_MESSAGE)
        }.onFailure { error ->
            assertTrue(error.message.orEmpty().contains("limit"))
        }.onSuccess { error("expected failure") }
    }

    @Test
    fun editAndDelete() {
        val created = SessionMemoryStore.create(
            current = emptyList(),
            content = "draft",
            placement = SessionMemoryPlacement.BEFORE_LATEST_MESSAGE,
        )
        val edited = SessionMemoryStore.edit(
            current = listOf(created),
            id = created.id,
            content = "final draft",
            placement = SessionMemoryPlacement.SYSTEM_PROMPT_AFTER,
        )
        assertEquals("final draft", edited.content)
        val remaining = SessionMemoryStore.delete(listOf(edited), edited.id)
        assertTrue(remaining.isEmpty())
    }
}
