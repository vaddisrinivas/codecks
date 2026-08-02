package io.codecks.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardNavigationPolicyTest {
    @Test
    fun tabAndShiftTabHaveDeterministicDirection() {
        assertEquals(
            ShellKeyAction.FocusNext,
            shellKeyAction(ShellKey.Tab, shiftPressed = false, fullscreen = false, canNavigateBack = true),
        )
        assertEquals(
            ShellKeyAction.FocusPrevious,
            shellKeyAction(ShellKey.Tab, shiftPressed = true, fullscreen = false, canNavigateBack = true),
        )
    }

    @Test
    fun enterSpaceBackAndEscapeHaveExplicitBehavior() {
        listOf(ShellKey.Enter, ShellKey.Space).forEach { key ->
            assertEquals(
                ShellKeyAction.ActivateFocused,
                shellKeyAction(key, shiftPressed = false, fullscreen = false, canNavigateBack = true),
            )
        }
        assertEquals(
            ShellKeyAction.NavigateBack,
            shellKeyAction(ShellKey.Back, false, fullscreen = false, canNavigateBack = true),
        )
        assertEquals(
            ShellKeyAction.ExitFullscreen,
            shellKeyAction(ShellKey.Escape, false, fullscreen = true, canNavigateBack = false),
        )
        assertEquals(
            ShellKeyAction.PassThrough,
            shellKeyAction(ShellKey.Escape, false, fullscreen = false, canNavigateBack = false),
        )
    }

    @Test
    fun highFontScaleRetainsNavigationOrEmergencyStopAcrossRequiredSizes() {
        val sizes = listOf(
            360 to 640,
            600 to 960,
            1280 to 720,
            1920 to 1080,
        )
        sizes.forEach { (width, height) ->
            val normal = shellAccessibilityLayout(width, height, fontScale = 2f, fullscreen = false)
            val fullscreen = shellAccessibilityLayout(width, height, fontScale = 2f, fullscreen = true)
            assertTrue(normal.navigationVisible)
            assertFalse(normal.stopInputVisible)
            assertFalse(fullscreen.navigationVisible)
            assertTrue(fullscreen.stopInputVisible)
        }
        assertEquals(
            ShellNavigationMode.BottomBar,
            shellAccessibilityLayout(1280, 720, 2f, fullscreen = false).navigationMode,
        )
    }

}
