package io.codecks.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeUiPolicyTest {
    @Test
    fun `two hundred percent text uses one editor column`() {
        assertEquals(1, adaptation(width = 1080, height = 720, fontScale = 2f).editorColumns)
    }

    @Test
    fun `phone portrait and landscape remain usable`() {
        assertEquals(1, adaptation(width = 360, height = 800).editorColumns)
        assertEquals(2, adaptation(width = 800, height = 360).editorColumns)
    }

    @Test
    fun `dex sized window uses wide policy while narrow resized window does not`() {
        val dex = adaptation(width = 1280, height = 720)
        val narrow = adaptation(width = 420, height = 720)
        assertTrue(dex.wideLayout)
        assertFalse(narrow.wideLayout)
        assertEquals(.72f, themePreviewWidthFraction(dex))
        assertEquals(1f, themePreviewWidthFraction(narrow))
    }

    @Test
    fun `reduced motion disables animation policy`() {
        val reduced = adaptation(width = 600, height = 800, reducedMotion = true)
        val normal = adaptation(width = 600, height = 800)
        assertFalse(reduced.animationsEnabled)
        assertEquals(0, themeAnimationDurationMillis(reduced))
        assertEquals(180, themeAnimationDurationMillis(normal))
    }

    @Test
    fun `overlay and lockscreen are preview only`() {
        assertFalse(adaptation(width = 600, height = 800, overlay = true).editingAllowed)
        assertFalse(adaptation(width = 600, height = 800, locked = true).editingAllowed)
        assertTrue(adaptation(width = 600, height = 800).editingAllowed)
    }

    private fun adaptation(
        width: Int,
        height: Int,
        fontScale: Float = 1f,
        reducedMotion: Boolean = false,
        overlay: Boolean = false,
        locked: Boolean = false,
    ) = ThemeUiPolicy.resolve(ThemeUiEnvironment(width, height, fontScale, reducedMotion, overlay, locked))
}
