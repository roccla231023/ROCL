package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AIIconMatcherTest {
    @Test
    fun antigravityIsNotGoogle() {
        assertEquals("antigravity.png", computeAIIconByName("Google.Antigravity"))
        assertEquals("antigravity.png", computeAIIconByName("antigravity"))
    }

    @Test
    fun googleStillMapsToGoogle() {
        assertEquals("google-color.svg", computeAIIconByName("Google.Gcli"))
        assertEquals("google-color.svg", computeAIIconByName("Google"))
    }
}
