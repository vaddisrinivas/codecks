package io.codecks.ui.app

import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
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

    @Test
    fun quickDeckActions_areActiveVisibleActionsCappedAtEight() {
        val actions = listOf(
            DeckAction("blank", "Blank", ActionKind.Local, ActionIcon.Empty),
            DeckAction("a", "A", ActionKind.Local, ActionIcon.Play),
            DeckAction("a", "A duplicate", ActionKind.Local, ActionIcon.Play),
        ) + (1..9).map { index ->
            DeckAction("action-$index", "Action $index", ActionKind.Local, ActionIcon.Play)
        }

        assertEquals(8, quickDeckActions(actions).size)
        assertEquals("a", quickDeckActions(actions).first().id)
        assertFalse(quickDeckActions(actions).any { it.id == "blank" })
    }

    @Test
    fun drawerIsPermanentOnlyForExpandedNormalText() {
        assertEquals(ShellDrawerMode.Modal, shellDrawerMode(839, 1f, fullscreen = false))
        assertEquals(ShellDrawerMode.Modal, shellDrawerMode(1199, 1f, fullscreen = false))
        assertEquals(ShellDrawerMode.Permanent, shellDrawerMode(1200, 1f, fullscreen = false))
        assertEquals(ShellDrawerMode.Modal, shellDrawerMode(1200, 2f, fullscreen = false))
        assertEquals(ShellDrawerMode.Modal, shellDrawerMode(1200, 1f, fullscreen = true))
    }

}
