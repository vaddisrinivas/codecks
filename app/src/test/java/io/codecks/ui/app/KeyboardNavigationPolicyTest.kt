package io.codecks.ui.app

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
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
            ShellNavigationMode.Rail,
            shellAccessibilityLayout(1280, 720, 2f, fullscreen = false).navigationMode,
        )
    }

    @Test
    fun sourceLeavesFocusAtBoundaryAndAnnouncesDisabledControls() {
        val shell = File("src/main/java/io/codecks/ui/app/CodecksAppShell.kt").readText()
        val deckComponents = File("src/main/java/io/codecks/ui/designsystem/DeckComponents.kt").readText()
        val accessibleAction = File("src/main/java/io/codecks/ui/app/AccessibleAction.kt").readText()

        assertTrue(shell.contains("ShellKeyAction.FocusNext -> focusManager.moveFocus"))
        assertTrue(shell.contains("ShellKeyAction.ActivateFocused"))
        assertTrue(shell.contains("-> false"))
        assertTrue(shell.contains("contentDescription = \"Stop input\""))
        assertTrue(deckComponents.contains("disabled()"))
        assertTrue(deckComponents.contains("stateDescription = \"Disabled\""))
        assertTrue(accessibleAction.contains("disabled()"))
    }

    @Test
    fun inputDispatchArchitectureHasNoControlPlaneIo() {
        val hid = File("src/main/java/io/codecks/HidRepository.kt").readText()
        val hidInputDispatch = hid.substring(
            hid.indexOf("override fun move"),
            hid.indexOf("private fun key"),
        )
        val keyboard = File("src/main/java/io/codecks/ui/keyboard/KeyboardViewModel.kt").readText()
        val keyboardCommandDispatch = keyboard.substring(
            keyboard.indexOf("class KeyboardViewModel"),
            keyboard.indexOf("internal fun KeyboardUiState"),
        )
        forbiddenDependencies.forEach { dependency ->
            assertFalse(hidInputDispatch.contains(dependency))
            assertFalse(keyboardCommandDispatch.contains(dependency))
        }
    }

    @Test
    fun writesKeyboardNavigationEvidence() {
        val rows = JSONArray()
        listOf(
            Triple(360, 640, "phone"),
            Triple(600, 960, "tablet"),
            Triple(1280, 720, "desktop_720"),
            Triple(1920, 1080, "desktop_1080"),
        ).forEach { (width, height, label) ->
            val normal = shellAccessibilityLayout(width, height, 2f, fullscreen = false)
            val fullscreen = shellAccessibilityLayout(width, height, 2f, fullscreen = true)
            rows.put(
                JSONObject()
                    .put("surface", label)
                    .put("widthDp", width)
                    .put("heightDp", height)
                    .put("fontScale", 2.0)
                    .put("navigation", normal.navigationVisible)
                    .put("mode", normal.navigationMode.name)
                    .put("fullscreenStop", fullscreen.stopInputVisible),
            )
        }
        val output = evidenceDirectory().resolve("keyboard_navigation_matrix.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(rows.toString(2))

        assertEquals(4, rows.length())
        assertTrue(output.isFile)
    }

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/A11Y-04")
    }

    private companion object {
        val forbiddenDependencies = listOf(
            "ConnectionRepository",
            "ReactiveHelper",
            "Update",
            "Backup",
            "DiagnosticEventStore",
            "ConnectionRepository",
            "runCommand(",
            "writeMacClipboard(",
        )
    }
}
