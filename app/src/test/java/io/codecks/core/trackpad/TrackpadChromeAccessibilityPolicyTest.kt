package io.codecks.core.trackpad

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadChromeAccessibilityPolicyTest {
    @Test
    fun backClosesOpenControlsAcrossTrackpadModes() {
        val source = File("src/main/java/io/codecks/ui/mouse/MouseScreen.kt").readText()

        assertTrue(source.contains("BackHandler(enabled = inputMode == MouseInputMode.Trackpad || controlsOpen)"))
        assertTrue(source.contains("} else if (controlsOpen) {"))
        assertTrue(source.contains("controlsOpen = false"))
    }

    @Test
    fun trayIconsExposeButtonRoleAndSelectionState() {
        val source = File("src/main/java/io/codecks/ui/mouse/MouseScreen.kt").readText()

        assertTrue(source.contains("semantics(mergeDescendants = true)"))
        assertTrue(source.contains("role = Role.Button"))
        assertTrue(source.contains("""stateDescription = if (selected) "Selected" else "Not selected""""))
        assertTrue(source.contains(".clickable("))
    }
}
