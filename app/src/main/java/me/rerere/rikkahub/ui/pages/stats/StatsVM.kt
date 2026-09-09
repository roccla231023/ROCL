package me.rerere.rikkahub.ui.pages.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.db.entity.UsageStatsEntity
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.time.LocalDate

data class AppStats(
    val isLoading: Boolean = true,
    val totalConversations: Int = 0,
    val totalMessages: Int = 0,
    val totalPromptTokens: Long = 0L,
    val totalCompletionTokens: Long = 0L,
    val totalCachedTokens: Long = 0L,
    val conversationsPerDay: Map<LocalDate, Int> = emptyMap(),
    val launchCount: Int = 0,
)

class StatsVM(
    conversationRepo: ConversationRepository,
) : ViewModel() {

    val stats = combine(
        conversationRepo.countConversationsFlow(),
        conversationRepo.getUsageStatsFlow(),
        conversationRepo.getAllDailyActivityFlow(),
    ) { conversationCount, usage, activity ->
        val ledger = usage ?: UsageStatsEntity()
        AppStats(
            isLoading = false,
            totalConversations = conversationCount,
            totalMessages = ledger.totalMessages.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            totalPromptTokens = ledger.inputTokens,
            totalCompletionTokens = ledger.outputTokens,
            totalCachedTokens = ledger.cachedTokens,
            conversationsPerDay = activity.mapNotNull { entry ->
                runCatching { LocalDate.parse(entry.date) to entry.messageCount }.getOrNull()
            }.toMap(),
            launchCount = ledger.appLaunches.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppStats())
}
