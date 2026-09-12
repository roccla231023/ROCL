package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.TransformerContext
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

internal const val CONTINUE_TAIL_CHARS_DEFAULT = 320
internal const val CONTINUE_MIN_OVERLAP_DEFAULT = 20
internal const val CONTINUE_MAX_OVERLAP_DEFAULT = 320

internal data class ContinuationDedupeConfig(
    val targetMessageId: Uuid,
    val originalText: String,
    val minOverlap: Int = CONTINUE_MIN_OVERLAP_DEFAULT,
    val maxOverlap: Int = CONTINUE_MAX_OVERLAP_DEFAULT,
)

internal fun buildHiddenContinuePrompt(
    previousAssistantText: String,
    tailChars: Int = CONTINUE_TAIL_CHARS_DEFAULT,
): String {
    val safeTailChars = tailChars.coerceAtLeast(0)
    val normalizedTail = previousAssistantText
        .replace("\r", "")
        .trim()
        .takeLast(safeTailChars)

    return buildString {
        appendLine("[CONTINUE_REQUEST_BEGIN]")
        appendLine("Continue directly from the previous assistant message.")
        appendLine()
        appendLine("Hard requirements:")
        appendLine("- Output only the new continuation.")
        appendLine("- Do not repeat, paraphrase, summarize, or rewrite any existing text.")
        appendLine("- Do not explain that you are continuing, and do not add any preface.")
        appendLine("- Keep exactly the same language, tone, and formatting as the previous assistant message.")
        appendLine("- Preserve structural continuity (headings, lists, code blocks, punctuation style).")
        if (normalizedTail.isNotBlank()) {
            appendLine()
            appendLine("Reference tail from the previous assistant message (for continuity only; do not repeat):")
            appendLine("<<<")
            appendLine(normalizedTail)
            appendLine(">>>")
        }
        appendLine()
        appendLine("If the previous assistant message is incomplete, continue from the first unfinished thought.")
        appendLine("[CONTINUE_REQUEST_END]")
    }.trim()
}

internal class HiddenContinueRequestTransformer(
    private val prompt: String,
) : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val normalized = prompt.replace("\r", "").trim()
        if (normalized.isBlank()) return messages
        return messages + UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text(normalized)),
            isSynthetic = true,
        )
    }
}

internal fun dedupeContinuationText(
    originalText: String,
    generatedFullText: String,
    minOverlap: Int = CONTINUE_MIN_OVERLAP_DEFAULT,
    maxOverlap: Int = CONTINUE_MAX_OVERLAP_DEFAULT,
): String {
    if (originalText.isBlank()) return generatedFullText
    if (generatedFullText.isBlank()) return generatedFullText
    if (!generatedFullText.startsWith(originalText)) return generatedFullText

    val appended = generatedFullText.substring(originalText.length)
    if (appended.isEmpty()) return generatedFullText

    val safeMin = minOverlap.coerceAtLeast(1)
    val safeMax = maxOverlap.coerceAtLeast(safeMin)
    val maxCandidate = minOf(originalText.length, appended.length, safeMax)
    if (maxCandidate < safeMin) return generatedFullText

    for (overlap in maxCandidate downTo safeMin) {
        val prefix = appended.take(overlap)
        if (originalText.endsWith(prefix)) {
            return originalText + appended.drop(overlap)
        }
    }

    return generatedFullText
}

internal fun applyContinuationDedupe(
    conversation: Conversation,
    config: ContinuationDedupeConfig,
): Conversation {
    var changed = false
    val updatedNodes = conversation.messageNodes.map { node ->
        if (node.messages.none { it.id == config.targetMessageId }) return@map node

        val updatedMessages = node.messages.map { message ->
            if (message.id != config.targetMessageId) return@map message
            val updated = applyContinuationDedupeToMessage(message, config)
            if (updated != message) changed = true
            updated
        }

        if (updatedMessages != node.messages) {
            node.copy(messages = updatedMessages)
        } else {
            node
        }
    }

    if (!changed) return conversation
    return conversation.copy(messageNodes = updatedNodes)
}

private fun applyContinuationDedupeToMessage(
    message: UIMessage,
    config: ContinuationDedupeConfig,
): UIMessage {
    val textIndices = message.parts.mapIndexedNotNull { index, part ->
        if (part is UIMessagePart.Text) index else null
    }
    if (textIndices.isEmpty()) return message

    val fullText = textIndices.joinToString(separator = "\n") { index ->
        (message.parts[index] as UIMessagePart.Text).text
    }
    val deduped = dedupeContinuationText(
        originalText = config.originalText,
        generatedFullText = fullText,
        minOverlap = config.minOverlap,
        maxOverlap = config.maxOverlap,
    )
    if (deduped == fullText) return message

    var written = false
    val extraTextIndices = textIndices.drop(1).toSet()
    val rebuiltParts = buildList {
        message.parts.forEachIndexed { index, part ->
            when {
                part is UIMessagePart.Text && index in extraTextIndices -> Unit
                part is UIMessagePart.Text && !written -> {
                    add(part.copy(text = deduped))
                    written = true
                }
                else -> add(part)
            }
        }
    }

    return message.copy(parts = rebuiltParts)
}
