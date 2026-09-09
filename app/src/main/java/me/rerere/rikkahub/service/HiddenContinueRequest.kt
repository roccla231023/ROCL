package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.TransformerContext

internal const val CONTINUE_TAIL_CHARS_DEFAULT = 320

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
