package io.codecks.ui.connection

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedConnectionUiSourceTest {
    @Test
    fun `production HID surfaces render unified redacted presentation only`() {
        val root = sourceRoot()
        val keyboard = File(root, "app/src/main/java/io/codecks/ui/keyboard/HidHostHeader.kt").readText()
        val settings = File(root, "app/src/main/java/io/codecks/ui/settings/SettingsScreen.kt").readText()
        val checklist = File(root, "app/src/main/java/io/codecks/ui/settings/SettingsThemeSections.kt").readText()
        val connection = File(root, "app/src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt").readText()

        listOf(keyboard, settings, checklist, connection).forEach {
            assertFalse(it.contains("hidHealth.detail"))
        }
        assertFalse(checklist.contains("state.hidHealth(permissionGranted).detail"))
        assertTrue(keyboard.contains("presentation.detail"))
        assertTrue(keyboard.contains("presentation.supportCode"))
    }

    @Test
    fun `all production channels consume typed repairs`() {
        val root = sourceRoot()
        val keyboard = File(root, "app/src/main/java/io/codecks/ui/keyboard/HidHostHeader.kt").readText()
        val ssh = File(root, "app/src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt").readText()
        val helper = File(root, "app/src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt").readText()
        val clipboard = File(root, "app/src/main/java/io/codecks/ui/clipboard/ClipboardScreen.kt").readText()

        assertTrue(keyboard.contains("presentation.repairs.firstOrNull()"))
        assertTrue(ssh.contains("diagnostic.repairActions.firstOrNull()"))
        assertTrue(helper.contains("state.repairs.firstOrNull"))
        assertTrue(clipboard.contains("presentation.repairs.firstOrNull"))
    }

    @Test
    fun `parallel SSH repair enum is removed`() {
        val source = File(sourceRoot(), "app/src/main/java/io/codecks/ui/connection/ConnectionDiagnosticPresenter.kt").readText()
        assertFalse(source.contains("ConnectionRepairAction"))
        assertTrue(source.contains("List<ConnectionRepair>"))
        assertFalse(source.contains("ConnectionDiagnosticState"))
    }

    @Test
    fun `settings repair panel exposes typed next action and focus`() {
        val source = File(sourceRoot(), "app/src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt").readText()
        assertTrue(source.contains("diagnostic.repairActions.firstOrNull()"))
        assertTrue(source.contains("focusRequester(failureFocusRequester)"))
        assertTrue(source.contains("state.connectionDiagnostic()"))
    }

    private fun sourceRoot(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt").isFile }
}
