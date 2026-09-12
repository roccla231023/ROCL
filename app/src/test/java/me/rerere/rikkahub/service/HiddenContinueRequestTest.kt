package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class HiddenContinueRequestTest {
    @Test
    fun `dedupeContinuationText trims repeated prefix when overlap is large enough`() {
        val original = "0123456789".repeat(8)
        val overlap = original.takeLast(24)
        val full = original + overlap + "NEW_CONTENT"

        val deduped = dedupeContinuationText(
            originalText = original,
            generatedFullText = full,
            minOverlap = 20,
            maxOverlap = 64,
        )

        assertEquals(original + "NEW_CONTENT", deduped)
    }

    @Test
    fun `dedupeContinuationText keeps text when overlap is below threshold`() {
        val original = "abcdefghij".repeat(8)
        val overlap = original.takeLast(10)
        val full = original + overlap + "TAIL"

        val deduped = dedupeContinuationText(
            originalText = original,
            generatedFullText = full,
            minOverlap = 20,
            maxOverlap = 64,
        )

        assertEquals(full, deduped)
    }

    @Test
    fun `applyContinuationDedupe only modifies text parts and keeps other parts`() {
        val messageId = Uuid.random()
        val original = "0123456789".repeat(8)
        val overlap = original.takeLast(24)
        val full = original + overlap + "NEW"

        val message = UIMessage(
            id = messageId,
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Reasoning(reasoning = "thinking..."),
                UIMessagePart.Text(full),
                UIMessagePart.Image(url = "file://image.png"),
            ),
        )
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(message.toMessageNode()),
        )

        val updated = applyContinuationDedupe(
            conversation = conversation,
            config = ContinuationDedupeConfig(
                targetMessageId = messageId,
                originalText = original,
                minOverlap = 20,
                maxOverlap = 64,
            )
        )

        val updatedMessage = updated.currentMessages.first()
        val textParts = updatedMessage.parts.filterIsInstance<UIMessagePart.Text>()
        assertEquals(1, textParts.size)
        assertEquals(original + "NEW", textParts.first().text)
        assertTrue(updatedMessage.parts.any { it is UIMessagePart.Reasoning })
        assertTrue(updatedMessage.parts.any { it is UIMessagePart.Image })
    }
}
