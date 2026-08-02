package io.codecks.ui.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityCriticalFlowPolicyTest {
    @Test
    fun clipboardConflictHasTypedTerminalStatusAndFontTwoStackedChoices() {
        val source = source("ui/clipboard/ClipboardScreen.kt")

        assertTrue(source.contains("""stateDescription = "Clipboard conflict""""))
        assertTrue(source.contains("announcementKey = \"clipboard-conflict:"))
        assertTrue(source.contains("if (state.hasConflict)"))
        assertTrue(source.contains("actions(Modifier.fillMaxWidth())"))
        assertTrue(source.contains("accessibilityTraversalOrder(3f)"))
    }

    @Test
    fun automationCleanupAndRecoveryAreNamedAndOptionsScrollAtLargeFont() {
        val source = source("ui/automations/AutomationsScreen.kt")
        val model = source("ui/automations/AutomationModels.kt")

        assertTrue(model.contains("val cleanupPassed: Boolean?"))
        assertTrue(model.contains("val recoveryRequired: Boolean"))
        assertTrue(source.contains("""AutomationStatus("Recovery required""""))
        assertTrue(source.contains("""AutomationStatus("Cleanup failed""""))
        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
        assertTrue(source.contains("heightIn(max = 520.dp)"))
    }

    @Test
    fun backupDialogAnnouncesEachPlanOnceAndKeepsDestructiveControlsOutsideScroll() {
        val source = source("ui/settings/BackupRestoreDialog.kt")

        assertTrue(source.contains("""announcementKey = "restore-blocked:"""))
        assertTrue(source.contains("""announcementKey = "restore-ready:"""))
        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
        assertTrue(source.indexOf("confirmButton =") > source.indexOf(".verticalScroll("))
        assertTrue(source.contains("accessibilityTraversalOrder(2f)"))
        assertTrue(source.contains("accessibilityTraversalOrder(1f)"))
    }

    private fun source(relative: String): String =
        File("src/main/java/io/codecks/$relative").readText()
}
