package me.rerere.rikkahub.ui.components.message.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class InlineWorkspaceReadTest {
    private val allowed = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val other = Uuid.parse("22222222-2222-2222-2222-222222222222")

    @Test
    fun offMeansNeverInline() {
        assertFalse(shouldInlineWorkspaceRead(false, setOf(allowed), allowed, "chapter.md"))
    }

    @Test
    fun emptyAllowlistNeverInlines() {
        assertFalse(shouldInlineWorkspaceRead(true, emptySet(), allowed, "chapter.md"))
    }

    @Test
    fun otherAssistantKeepsCodePreview() {
        assertFalse(shouldInlineWorkspaceRead(true, setOf(allowed), other, "chapter.md"))
        assertFalse(shouldInlineWorkspaceRead(true, setOf(allowed), null, "chapter.md"))
    }

    @Test
    fun markdownAndTxtOnAllowlistInline() {
        assertTrue(shouldInlineWorkspaceRead(true, setOf(allowed), allowed, "novel/ch1.md"))
        assertTrue(shouldInlineWorkspaceRead(true, setOf(allowed), allowed, "notes.markdown"))
        assertTrue(shouldInlineWorkspaceRead(true, setOf(allowed), allowed, "memo.TXT"))
        assertTrue(shouldInlineWorkspaceRead(true, setOf(allowed), allowed, "README.MD"))
    }

    @Test
    fun codeAndUnknownStayPreview() {
        assertFalse(shouldInlineWorkspaceRead(true, setOf(allowed), allowed, "Main.kt"))
        assertFalse(shouldInlineWorkspaceRead(true, setOf(allowed), allowed, "data.json"))
        assertFalse(shouldInlineWorkspaceRead(true, setOf(allowed), allowed, "run.log"))
        assertFalse(shouldInlineWorkspaceRead(true, setOf(allowed), allowed, "README"))
        assertFalse(shouldInlineWorkspaceRead(true, setOf(allowed), allowed, null))
        assertFalse(shouldInlineWorkspaceRead(true, setOf(allowed), allowed, "notes.txt.bak"))
    }
}
