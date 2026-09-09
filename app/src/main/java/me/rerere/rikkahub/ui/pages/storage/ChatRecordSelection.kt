package me.rerere.rikkahub.ui.pages.storage

import me.rerere.rikkahub.data.repository.ChatRecordsMonthEntry

sealed interface ChatRecordMonthSelection {
    val selectedCount: Int

    data class All(override val selectedCount: Int) : ChatRecordMonthSelection

    data class Some(val conversationIds: Set<String>) : ChatRecordMonthSelection {
        override val selectedCount: Int
            get() = conversationIds.size
    }
}

data class ChatRecordSelectionSummary(
    val selectedMonthCount: Int,
    val selectedConversationCount: Int,
    val totalConversationCount: Int,
)

internal fun buildChatRecordSelectionSummary(
    monthEntries: List<ChatRecordsMonthEntry>,
    selections: Map<String, ChatRecordMonthSelection>,
): ChatRecordSelectionSummary {
    val selectedMonthCount = selections.size
    val selectedConversationCount = selections.values.sumOf { it.selectedCount }
    val totalConversationCount = monthEntries.sumOf { it.conversationCount }
    return ChatRecordSelectionSummary(
        selectedMonthCount = selectedMonthCount,
        selectedConversationCount = selectedConversationCount,
        totalConversationCount = totalConversationCount,
    )
}

internal fun filterChatRecordSelectionsByValidMonths(
    selections: Map<String, ChatRecordMonthSelection>,
    validYearMonths: Set<String>,
): Map<String, ChatRecordMonthSelection> {
    if (selections.isEmpty()) return emptyMap()
    if (validYearMonths.isEmpty()) return emptyMap()
    return selections.filterKeys { it in validYearMonths }
}

data class ChatRecordClearTargets(
    val yearMonths: Set<String>,
    val conversationIds: Set<String>,
)

internal fun buildChatRecordClearTargets(
    selections: Map<String, ChatRecordMonthSelection>,
): ChatRecordClearTargets {
    if (selections.isEmpty()) {
        return ChatRecordClearTargets(
            yearMonths = emptySet(),
            conversationIds = emptySet(),
        )
    }

    val yearMonths = LinkedHashSet<String>(selections.size)
    val conversationIds = LinkedHashSet<String>(selections.size * 16)

    selections.forEach { (yearMonth, selection) ->
        when (selection) {
            is ChatRecordMonthSelection.All -> yearMonths += yearMonth
            is ChatRecordMonthSelection.Some -> conversationIds.addAll(selection.conversationIds)
        }
    }

    return ChatRecordClearTargets(
        yearMonths = yearMonths,
        conversationIds = conversationIds,
    )
}

