package me.rerere.rikkahub.data.repository

internal data class UsageLedgerSnapshot(
    val totalConversations: Long = 0L,
    val totalMessages: Long = 0L,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cachedTokens: Long = 0L,
)

internal fun mergeUsageStats(
    current: UsageLedgerSnapshot,
    scanned: UsageLedgerSnapshot,
): UsageLedgerSnapshot {
    return UsageLedgerSnapshot(
        totalConversations = maxOf(current.totalConversations, scanned.totalConversations),
        totalMessages = maxOf(current.totalMessages, scanned.totalMessages),
        inputTokens = maxOf(current.inputTokens, scanned.inputTokens),
        outputTokens = maxOf(current.outputTokens, scanned.outputTokens),
        cachedTokens = maxOf(current.cachedTokens, scanned.cachedTokens),
    )
}
