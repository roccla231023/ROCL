package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsRpFilterTest {
    @Test
    fun skipsWrappedActionTextAndKeepsDialogue() {
        val rules = listOf(RpStyleRule(pattern = "*", colorHex = "#888888"))
        val spoken = filterTtsWithRpRules(
            text = "*walks to the door* Hello there.",
            rules = rules,
        )
        assertEquals("Hello there.", spoken)
    }

    @Test
    fun disabledRulesAreIgnored() {
        val rules = listOf(RpStyleRule(pattern = "%", enabled = false))
        assertEquals("%keep%", filterTtsWithRpRules("%keep%", rules))
    }
}
