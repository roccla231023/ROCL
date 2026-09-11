package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownHeaderStyleTest {
    @Test
    fun defaultHeadingsStayChatSized() {
        assertEquals(24f, HeaderStyle.fromLevel(1, 1f).fontSize.value)
        assertEquals(22f, HeaderStyle.fromLevel(2, 1f).fontSize.value)
        assertEquals(16f, HeaderStyle.verticalPadding(1).value)
    }

    @Test
    fun compactHeadingsShrinkOnlyWhenAsked() {
        assertEquals(16f, HeaderStyle.fromLevel(1, 1f, compact = true).fontSize.value)
        assertEquals(15f, HeaderStyle.fromLevel(2, 1f, compact = true).fontSize.value)
        assertEquals(13f, HeaderStyle.fromLevel(6, 1f, compact = true).fontSize.value)
        assertEquals(6f, HeaderStyle.verticalPadding(1, compact = true).value)
        assertEquals(24f, HeaderStyle.fromLevel(1, 1f, compact = false).fontSize.value)
    }
}
