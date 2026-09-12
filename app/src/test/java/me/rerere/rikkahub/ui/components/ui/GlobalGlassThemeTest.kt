package me.rerere.rikkahub.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GlobalGlassThemeTest {
    @Test
    fun inactiveKeepsOpaqueSchemeWithoutSwap() {
        assertNull(resolveGlobalGlassAlphas(active = false, surfaceOpacity = 0.68f))
        assertNull(resolveGlobalGlassAlphas(active = false, surfaceOpacity = 1f))
    }

    @Test
    fun activeUsesGlassSurfaceOpacity() {
        val alphas = resolveGlobalGlassAlphas(active = true, surfaceOpacity = 0.68f)
        assertNotNull(alphas)
        assertEquals(0.06f, alphas!!.background, 0f)
        assertEquals(0.68f, alphas.surface, 0f)
        assertEquals(0.74f, alphas.surfaceDim, 0.0001f)
        assertEquals(0.48f, alphas.surfaceContainerLowest, 0.0001f)
        assertEquals(1f, alphas.surfaceContainerLow, 0f)
        assertEquals(0.72f, alphas.surfaceContainerHigh, 0.0001f)
        assertEquals(0.76f, alphas.surfaceContainerHighest, 0.0001f)
        assertEquals(0.46f, alphas.outline, 0f)
        assertEquals(0.30f, alphas.outlineVariant, 0f)
    }

    @Test
    fun surfaceOpacityIsClamped() {
        val low = resolveGlobalGlassAlphas(active = true, surfaceOpacity = 0.1f)!!
        val high = resolveGlobalGlassAlphas(active = true, surfaceOpacity = 1.5f)!!
        assertEquals(0.35f, low.surface, 0f)
        assertEquals(1f, high.surface, 0f)
        assertEquals(1f, high.surfaceDim, 0f)
        assertEquals(0.24f, low.surfaceContainerLowest, 0.0001f)
    }

    @Test
    fun leavingChatKeepsBackgroundMountedAndTurnsGlassOn() {
        val layer = resolveGlobalBackgroundLayer(
            hasGlobalBackground = true,
            isChatScreen = false,
            backgroundOpacity = 0.8f,
        )
        assertEquals(true, layer.keepBackgroundMounted)
        assertEquals(true, layer.glassActive)
        assertEquals(0.8f, layer.targetBackgroundOpacity, 0f)
    }

    @Test
    fun returningToChatKeepsBackgroundMountedAndTurnsGlassOff() {
        val layer = resolveGlobalBackgroundLayer(
            hasGlobalBackground = true,
            isChatScreen = true,
            backgroundOpacity = 0.8f,
        )
        assertEquals(true, layer.keepBackgroundMounted)
        assertEquals(false, layer.glassActive)
        assertEquals(0f, layer.targetBackgroundOpacity, 0f)
    }

    @Test
    fun disabledBackgroundDoesNotMountLayer() {
        val layer = resolveGlobalBackgroundLayer(
            hasGlobalBackground = false,
            isChatScreen = false,
            backgroundOpacity = 1f,
        )
        assertEquals(false, layer.keepBackgroundMounted)
        assertEquals(false, layer.glassActive)
        assertEquals(0f, layer.targetBackgroundOpacity, 0f)
    }
}
