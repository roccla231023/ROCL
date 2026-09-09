package me.rerere.rikkahub.ui.pages.storage

import me.rerere.rikkahub.data.repository.ChatRecordMonthRange
import me.rerere.rikkahub.data.repository.ChatRecordsMonthEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ChatRecordSelectionTest {
    @Test
    fun `buildChatRecordClearTargets splits whole months and individual ids`() {
        val targets = buildChatRecordClearTargets(
            mapOf(
                "2024-01" to ChatRecordMonthSelection.All(3),
                "2024-02" to ChatRecordMonthSelection.Some(setOf("a", "b")),
            )
        )
        assertEquals(setOf("2024-01"), targets.yearMonths)
        assertEquals(setOf("a", "b"), targets.conversationIds)
    }

    @Test
    fun `filterChatRecordSelectionsByValidMonths drops stale months`() {
        val filtered = filterChatRecordSelectionsByValidMonths(
            selections = mapOf(
                "2024-01" to ChatRecordMonthSelection.All(2),
                "2024-02" to ChatRecordMonthSelection.Some(setOf("x")),
            ),
            validYearMonths = setOf("2024-02"),
        )
        assertEquals(setOf("2024-02"), filtered.keys)
    }

    @Test
    fun `selection summary uses month counts not unique ids`() {
        val summary = buildChatRecordSelectionSummary(
            monthEntries = listOf(
                ChatRecordsMonthEntry("2024-01", 4),
                ChatRecordsMonthEntry("2024-02", 2),
            ),
            selections = mapOf(
                "2024-01" to ChatRecordMonthSelection.All(4),
                "2024-02" to ChatRecordMonthSelection.Some(setOf("a")),
            ),
        )
        assertEquals(2, summary.selectedMonthCount)
        assertEquals(5, summary.selectedConversationCount)
        assertEquals(6, summary.totalConversationCount)
    }

    @Test
    fun `millisRange is half-open local month window`() {
        val range = ChatRecordMonthRange.millisRange("2024-01", ZoneOffset.UTC)!!
        val next = ChatRecordMonthRange.millisRange("2024-02", ZoneOffset.UTC)!!
        assertEquals(range.second, next.first)
        assertTrue(range.second > range.first)
        assertNull(ChatRecordMonthRange.millisRange("not-a-month"))
    }
}
