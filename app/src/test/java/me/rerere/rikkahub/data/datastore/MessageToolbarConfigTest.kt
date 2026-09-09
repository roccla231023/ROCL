package me.rerere.rikkahub.data.datastore

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageToolbarConfigTest {
    @Test
    fun toggleMovesBetweenToolbarAndMore() {
        val start = MessageToolbarConfig.DEFAULT_USER
        assertTrue(start.isOnToolbar(MessageToolbarButton.COPY))
        assertFalse(start.isOnToolbar(MessageToolbarButton.FORK))

        val forked = start.toggle(MessageToolbarButton.FORK)
        assertTrue(forked.isOnToolbar(MessageToolbarButton.FORK))
        assertTrue(forked.isOnToolbar(MessageToolbarButton.COPY))

        val copyInMore = forked.toggle(MessageToolbarButton.COPY)
        assertFalse(copyInMore.isOnToolbar(MessageToolbarButton.COPY))
        assertTrue(copyInMore.isOnToolbar(MessageToolbarButton.FORK))
    }

    @Test
    fun continueAndTtsAreAssistantOnly() {
        assertFalse(MessageToolbarButton.CONTINUE.isAvailableFor(MessageRole.USER))
        assertFalse(MessageToolbarButton.TTS.isAvailableFor(MessageRole.USER))
        assertTrue(MessageToolbarButton.CONTINUE.isAvailableFor(MessageRole.ASSISTANT))
        assertTrue(MessageToolbarButton.COPY.isAvailableFor(MessageRole.USER))
    }

    @Test
    fun officialDefaultsKeepForkInMore() {
        assertFalse(MessageToolbarConfig.DEFAULT_USER.isOnToolbar(MessageToolbarButton.FORK))
        assertFalse(MessageToolbarConfig.DEFAULT_ASSISTANT.isOnToolbar(MessageToolbarButton.FORK))
        assertFalse(MessageToolbarConfig.DEFAULT_ASSISTANT.isOnToolbar(MessageToolbarButton.CONTINUE))
        assertTrue(MessageToolbarConfig.DEFAULT_ASSISTANT.isOnToolbar(MessageToolbarButton.TTS))
    }

    @Test
    fun legacyContinueSwitchPinsContinueOnAssistantBar() {
        val display = DisplaySetting(
            showContinueOnAssistantToolbar = true,
            assistantMessageToolbar = MessageToolbarConfig.DEFAULT_ASSISTANT,
        )
        val resolved = display.resolvedToolbar(MessageRole.ASSISTANT)
        assertTrue(resolved.isOnToolbar(MessageToolbarButton.CONTINUE))
        assertTrue(resolved.isOnToolbar(MessageToolbarButton.COPY))

        val off = display.copy(showContinueOnAssistantToolbar = false)
            .resolvedToolbar(MessageRole.ASSISTANT)
        assertFalse(off.isOnToolbar(MessageToolbarButton.CONTINUE))
    }
}
