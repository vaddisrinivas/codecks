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
        val source = listOf("AutomationsScreen.kt", "AutomationSections.kt")
            .joinToString("\n") { source("ui/automations/$it") }
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

    @Test
    fun keyboardComposerHasPersistentLabelLiveDeliveryStatusAndTwoHundredPercentReflow() {
        val source = source("ui/keyboard/KeyboardScreen.kt")

        assertTrue(source.contains("label = { Text(\"Text to type on Mac\") }"))
        assertTrue(source.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(source.contains("LocalDensity.current.fontScale >= 2f"))
        assertTrue(source.contains("if (largeText)"))
        assertTrue(source.contains("heightIn(min = CodecksDesignTokens.Size.actionMinHeight)"))
    }

    @Test
    fun trackpadExposesTalkBackEquivalentsWithoutLeakingRestrictedLockscreenActions() {
        val adapter = source("ui/mouse/RawTrackpadAdapter.kt")
        val lockscreen = source("ui/mouse/lockscreen/LockscreenTrackpadScreen.kt")

        assertTrue(adapter.contains("AccessibilityAction(ACTION_OPEN_DECK, \"Open Deck\")"))
        assertTrue(adapter.contains("AccessibilityAction(ACTION_OPEN_CONTROLS, \"Open Trackpad controls\")"))
        assertTrue(adapter.contains("ACTION_SCROLL_FORWARD"))
        assertTrue(adapter.contains("ViewCompat.setStateDescription"))
        assertTrue(adapter.contains("AccessibilityNodeInfoCompat.wrap(info).stateDescription"))
        assertTrue(lockscreen.contains("exposeDeckAccessibilityAction = false"))
        assertTrue(lockscreen.contains("exposeControlsAccessibilityAction = false"))
    }

    @Test
    fun lockscreenHelperOverlayAndWidgetUseNonInteractiveStatusAndNamedSurfaces() {
        val lockscreen = source("ui/mouse/lockscreen/LockscreenTrackpadScreen.kt")
        val firstRun = source("ui/mouse/TrackpadHostScreen.kt")
        val helper = source("ui/settings/SettingsConnectionSections.kt")
        val overlay = source("AppDestinationSupport.kt")
        val widget = File("src/main/res/layout/trackpad_widget.xml").readText()
        val widgetInfo = File("src/main/res/xml/trackpad_widget_info.xml").readText()

        assertTrue(lockscreen.contains("verticalScroll(rememberScrollState())"))
        assertTrue(lockscreen.contains("accessibilityTraversalOrder(6f)"))
        assertTrue(firstRun.contains("LocalDensity.current.fontScale >= 2f"))
        assertTrue(firstRun.contains("Modifier.verticalScroll(rememberScrollState())"))
        assertTrue(helper.contains("private fun HelperStatusBadge"))
        assertTrue(helper.contains("stateDescription = label"))
        assertTrue(overlay.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(widget.contains("android:contentDescription=\"@string/widget_trackpad_action\""))
        assertTrue(widget.contains("android:importantForAccessibility=\"noHideDescendants\""))
        assertTrue(widgetInfo.contains("android:minWidth=\"180dp\""))
        assertTrue(widgetInfo.contains("android:minHeight=\"96dp\""))
    }

    private fun source(relative: String): String =
        File("src/main/java/io/codecks/$relative").readText()
}
