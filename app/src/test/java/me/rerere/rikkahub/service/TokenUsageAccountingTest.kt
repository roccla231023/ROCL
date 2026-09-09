package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.mergeUsageStats
import me.rerere.rikkahub.data.repository.UsageLedgerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenUsageAccountingTest {
    @Test
    fun countsEveryNewAssistantUsageFromToolLoop() {
        val user = UIMessage.user("Find the latest release notes")
        val toolCall = assistantMessage(
            text = "",
            usage = TokenUsage(promptTokens = 12_000, completionTokens = 120),
            parts = listOf(UIMessagePart.Tool(toolCallId = "call_1", toolName = "search_web", input = "{}")),
        )
        val finalAnswer = assistantMessage(
            text = "Here is the summary",
            usage = TokenUsage(promptTokens = 23_032, completionTokens = 756, cachedTokens = 32),
        )

        val delta = calculateGenerationUsageDelta(
            baselineMessages = listOf(user),
            finalMessages = listOf(user, toolCall, finalAnswer),
        )

        assertEquals(35_032L, delta.tokens.inputTokens)
        assertEquals(876L, delta.tokens.outputTokens)
        assertEquals(32L, delta.tokens.cachedTokens)
        assertEquals(2, delta.newAssistantMessageCount)
    }

    @Test
    fun doesNotCountUnchangedHistoricalAssistantUsage() {
        val user = UIMessage.user("Hello")
        val oldAssistant = assistantMessage(
            text = "Old answer",
            usage = TokenUsage(promptTokens = 1_000, completionTokens = 100),
        )

        val delta = calculateGenerationUsageDelta(
            baselineMessages = listOf(user, oldAssistant),
            finalMessages = listOf(user, oldAssistant),
        )

        assertTrue(delta.tokens.isEmpty)
        assertEquals(0, delta.newAssistantMessageCount)
    }

    @Test
    fun countsChangedUsageOnContinuedAssistantMessageWithoutNewMessage() {
        val original = assistantMessage(
            text = "Partial answer",
            usage = TokenUsage(promptTokens = 1_000, completionTokens = 100),
        )
        val continued = original.copy(
            parts = listOf(UIMessagePart.Text("Partial answer continued")),
            usage = TokenUsage(promptTokens = 4_000, completionTokens = 250, cachedTokens = 10),
        )

        val delta = calculateGenerationUsageDelta(
            baselineMessages = listOf(original),
            finalMessages = listOf(continued),
        )

        assertEquals(4_000L, delta.tokens.inputTokens)
        assertEquals(250L, delta.tokens.outputTokens)
        assertEquals(10L, delta.tokens.cachedTokens)
        assertEquals(0, delta.newAssistantMessageCount)
    }

    @Test
    fun mergeUsageStatsNeverDecreases() {
        val merged = mergeUsageStats(
            current = UsageLedgerSnapshot(
                totalConversations = 10,
                totalMessages = 100,
                inputTokens = 50,
                outputTokens = 20,
                cachedTokens = 5,
            ),
            scanned = UsageLedgerSnapshot(
                totalConversations = 3,
                totalMessages = 40,
                inputTokens = 80,
                outputTokens = 10,
                cachedTokens = 0,
            ),
        )
        assertEquals(10, merged.totalConversations)
        assertEquals(100, merged.totalMessages)
        assertEquals(80, merged.inputTokens)
        assertEquals(20, merged.outputTokens)
        assertEquals(5, merged.cachedTokens)
    }

    private fun assistantMessage(
        text: String,
        usage: TokenUsage,
        parts: List<UIMessagePart> = listOf(UIMessagePart.Text(text)),
    ): UIMessage {
        return UIMessage(
            role = MessageRole.ASSISTANT,
            parts = parts,
            usage = usage,
        )
    }
}
