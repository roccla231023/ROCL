package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.SessionMemory
import me.rerere.rikkahub.data.model.SessionMemoryPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAssemblerTest {
    @Test
    fun sessionAndPinnedGoToPrefixDynamicGoesBeforeLatestUser() {
        val history = listOf(
            UIMessage.user("earlier"),
            UIMessage.assistant("ok"),
            UIMessage.user("latest"),
        )
        val assembled = MemoryAssembler.assemble(
            history = history,
            systemPrompt = "You are Bob.",
            enableMemory = true,
            enableSessionMemory = true,
            memories = listOf(
                AssistantMemory(id = 1, content = "pinned fact", pinned = true),
                AssistantMemory(id = 2, content = "retrieved fact"),
            ),
            sessionMemories = listOf(
                SessionMemory(id = 1, content = "stable outline", placement = SessionMemoryPlacement.SYSTEM_PROMPT_AFTER),
                SessionMemory(id = 2, content = "today's act", placement = SessionMemoryPlacement.BEFORE_LATEST_MESSAGE),
            ),
            toolPrompts = emptyList(),
        )
        val roles = assembled.map { it.role }
        assertEquals(MessageRole.SYSTEM, roles.first())
        assertTrue(assembled.first().toText().contains("You are Bob."))
        assertTrue(assembled[1].toText().contains("stable outline"))
        assertTrue(assembled[1].toText().contains("pinned fact"))
        assertFalse(assembled[1].toText().contains("today's act"))
        val beforeLatest = assembled[assembled.lastIndex - 1]
        assertEquals(MessageRole.USER, beforeLatest.role)
        assertTrue(beforeLatest.toText().contains("today's act"))
        assertTrue(beforeLatest.toText().contains("retrieved fact"))
        assertEquals("latest", assembled.last().toText())
    }

    @Test
    fun memoryOffKeepsSessionInjection() {
        val assembled = MemoryAssembler.assemble(
            history = listOf(UIMessage.user("hi")),
            systemPrompt = "sys",
            enableMemory = false,
            enableSessionMemory = true,
            memories = listOf(AssistantMemory(id = 1, content = "long term")),
            sessionMemories = listOf(
                SessionMemory(id = 1, content = "session only", placement = SessionMemoryPlacement.SYSTEM_PROMPT_AFTER),
            ),
            toolPrompts = emptyList(),
        )
        val joined = assembled.joinToString("\n") { it.toText() }
        assertTrue(joined.contains("session only"))
        assertFalse(joined.contains("long term"))
    }

    @Test
    fun bothOffOnlyKeepsSystemAndHistory() {
        val assembled = MemoryAssembler.assemble(
            history = listOf(UIMessage.user("hi")),
            systemPrompt = "sys",
            enableMemory = false,
            enableSessionMemory = false,
            memories = listOf(AssistantMemory(id = 1, content = "long term")),
            sessionMemories = listOf(
                SessionMemory(id = 1, content = "session only", placement = SessionMemoryPlacement.SYSTEM_PROMPT_AFTER),
            ),
            toolPrompts = listOf("tool docs"),
        )
        assertEquals("sys\ntool docs", assembled.first().toText().trim())
        assertEquals(2, assembled.size)
    }

    @Test
    fun injectionMessagesAreSynthetic() {
        val assembled = MemoryAssembler.assemble(
            history = listOf(UIMessage.user("latest")),
            systemPrompt = "sys",
            enableMemory = true,
            enableSessionMemory = true,
            memories = listOf(AssistantMemory(id = 1, content = "pinned", pinned = true)),
            sessionMemories = listOf(
                SessionMemory(id = 1, content = "stable", placement = SessionMemoryPlacement.SYSTEM_PROMPT_AFTER),
                SessionMemory(id = 2, content = "dynamic", placement = SessionMemoryPlacement.BEFORE_LATEST_MESSAGE),
            ),
            toolPrompts = emptyList(),
        )
        assertTrue(assembled[0].isSynthetic)
        assertTrue(assembled[1].isSynthetic)
        assertTrue(assembled[2].isSynthetic)
        assertFalse(assembled.last().isSynthetic)
        assertEquals("latest", assembled.last().toText())
    }
}
