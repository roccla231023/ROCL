package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.uuid.Uuid

class AutoContinuePolicyTest {
    @Test
    fun truncationReasonsTriggerContinue() {
        assertTrue(shouldAutoContinueForFinishReasons(setOf("length")))
        assertTrue(shouldAutoContinueForFinishReasons(setOf("LENGTH")))
        assertTrue(shouldAutoContinueForFinishReasons(setOf("max_tokens")))
        assertTrue(shouldAutoContinueForFinishReasons(setOf("MAX_TOKENS")))
        assertTrue(shouldAutoContinueForFinishReasons(setOf("max_output_tokens")))
        assertTrue(shouldAutoContinueForFinishReasons(setOf("incomplete:max_output_tokens")))
        assertTrue(shouldAutoContinueForFinishReasons(setOf("max_tokens_exceeded")))
        assertTrue(shouldAutoContinueForFinishReasons(setOf("token_limit_reached")))
        assertTrue(shouldAutoContinueForFinishReasons(setOf("stop", "length")))
    }

    @Test
    fun normalAndEmptyReasonsDoNotTrigger() {
        assertFalse(shouldAutoContinueForFinishReasons(emptySet()))
        assertFalse(shouldAutoContinueForFinishReasons(setOf("stop")))
        assertFalse(shouldAutoContinueForFinishReasons(setOf("end_turn")))
        assertFalse(shouldAutoContinueForFinishReasons(setOf("tool_calls")))
        assertFalse(shouldAutoContinueForFinishReasons(setOf("pause_turn")))
    }

    @Test
    fun networkIoTriggersContinue() {
        assertTrue(shouldAutoContinueOnNetworkError(IOException("socket closed")))
        assertTrue(shouldAutoContinueOnNetworkError(SocketTimeoutException("timeout")))
        assertTrue(shouldAutoContinueOnNetworkError(UnknownHostException("dns")))
        assertTrue(shouldAutoContinueOnNetworkError(RuntimeException("wrap", IOException("reset"))))
    }

    @Test
    fun userCancelAndGenericErrorsDoNotTrigger() {
        assertFalse(shouldAutoContinueOnNetworkError(CancellationException("user stopped")))
        assertFalse(shouldAutoContinueOnNetworkError(IOException("canceled")))
        assertFalse(shouldAutoContinueOnNetworkError(IOException("cancelled")))
        assertFalse(shouldAutoContinueOnNetworkError(RuntimeException("canceled", CancellationException())))
        assertFalse(shouldAutoContinueOnNetworkError(IllegalStateException("bad state")))
    }

    @Test
    fun lastAssistantMessageIsTheCandidate() {
        val assistant = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("  half of a chapter  ")),
        )
        val conversation = Conversation(
            assistantId = Uuid.parse("11111111-1111-1111-1111-111111111111"),
            messageNodes = listOf(
                MessageNode.of(UIMessage.user("write the next chapter")),
                MessageNode.of(assistant),
            ),
        )
        val candidate = resolveAutoContinueCandidate(conversation)
        assertNotNull(candidate)
        assertEquals(1, candidate!!.nodeIndex)
        assertEquals("half of a chapter", candidate.originalText)
        assertEquals(assistant.id, candidate.message.id)
    }

    @Test
    fun userLastMessageAndUnfinishedToolsAreRejected() {
        val assistantId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val userOnly = Conversation(
            assistantId = assistantId,
            messageNodes = listOf(MessageNode.of(UIMessage.user("hello"))),
        )
        assertNull(resolveAutoContinueCandidate(userOnly))

        val pendingTool = Conversation(
            assistantId = assistantId,
            messageNodes = listOf(
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.Text("I'll look it up"),
                            UIMessagePart.Tool(toolCallId = "call_1", toolName = "search_web", input = "{}"),
                        ),
                    )
                )
            ),
        )
        assertNull(resolveAutoContinueCandidate(pendingTool))

        val blankAssistant = Conversation(
            assistantId = assistantId,
            messageNodes = listOf(
                MessageNode.of(
                    UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("   ")))
                )
            ),
        )
        assertNull(resolveAutoContinueCandidate(blankAssistant))
    }
}
