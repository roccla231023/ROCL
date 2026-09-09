package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage

internal data class QuotaTokenUsageDelta(
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cachedTokens: Long = 0L,
) {
    val isEmpty: Boolean
        get() = inputTokens <= 0L && outputTokens <= 0L && cachedTokens <= 0L

    operator fun plus(usage: TokenUsage): QuotaTokenUsageDelta {
        return copy(
            inputTokens = inputTokens + usage.promptTokens.toLong(),
            outputTokens = outputTokens + usage.completionTokens.toLong(),
            cachedTokens = cachedTokens + usage.cachedTokens.toLong(),
        )
    }
}

internal data class GenerationUsageDelta(
    val tokens: QuotaTokenUsageDelta,
    val newAssistantMessageCount: Int,
)

internal fun calculateGenerationUsageDelta(
    baselineMessages: List<UIMessage>,
    finalMessages: List<UIMessage>,
): GenerationUsageDelta {
    val baselineIds = baselineMessages.mapTo(mutableSetOf()) { it.id }
    val baselineUsageById = baselineMessages.associate { message -> message.id to message.usage }
    var newAssistantMessageCount = 0
    val tokens = finalMessages
        .asSequence()
        .filter { message -> message.role == MessageRole.ASSISTANT }
        .mapNotNull { message ->
            val isNewMessage = message.id !in baselineIds
            if (isNewMessage) newAssistantMessageCount += 1
            val usage = message.usage ?: return@mapNotNull null
            val usageChanged = !isNewMessage && baselineUsageById[message.id] != usage
            if (isNewMessage || usageChanged) usage else null
        }
        .fold(QuotaTokenUsageDelta()) { acc, usage -> acc + usage }
    return GenerationUsageDelta(tokens = tokens, newAssistantMessageCount = newAssistantMessageCount)
}
