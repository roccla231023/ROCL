package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import java.io.IOException
import java.util.Locale

private val TRUNCATION_FINISH_REASONS = setOf(
    "length",
    "max_tokens",
    "max_output_tokens",
    "max_tokens_exceeded",
    "token_limit_reached",
)

fun shouldAutoContinueForFinishReasons(finishReasons: Set<String>): Boolean {
    if (finishReasons.isEmpty()) return false
    return finishReasons.any { reason ->
        val normalized = reason.trim().lowercase(Locale.US)
        if (normalized in TRUNCATION_FINISH_REASONS) return@any true
        val suffix = normalized.substringAfterLast(':', missingDelimiterValue = "")
        suffix.isNotEmpty() && suffix in TRUNCATION_FINISH_REASONS
    }
}

fun shouldAutoContinueOnNetworkError(throwable: Throwable): Boolean {
    val visited = HashSet<Throwable>()
    var current: Throwable? = throwable
    var sawNetworkIo = false
    while (current != null && visited.add(current)) {
        if (current is CancellationException) return false
        if (current is IOException && !current.isCanceledIo()) {
            sawNetworkIo = true
        }
        current = current.cause
    }
    return sawNetworkIo
}

private fun IOException.isCanceledIo(): Boolean {
    val message = message?.lowercase(Locale.US).orEmpty()
    return message == "canceled" || message == "cancelled"
}

data class AutoContinueCandidate(
    val message: UIMessage,
    val nodeIndex: Int,
    val originalText: String,
)

fun resolveAutoContinueCandidate(conversation: Conversation): AutoContinueCandidate? {
    val nodeIndex = conversation.messageNodes.lastIndex
    if (nodeIndex < 0) return null
    val message = conversation.messageNodes[nodeIndex].currentMessage
    if (message.role != MessageRole.ASSISTANT) return null
    if (message.getTools().any { !it.isExecuted }) return null
    val originalText = message.toText().trim()
    if (originalText.isBlank()) return null
    return AutoContinueCandidate(
        message = message,
        nodeIndex = nodeIndex,
        originalText = originalText,
    )
}
