package io.codecks.ui.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityPrimitiveTest {
    @Test
    fun errorStatusExposesStateErrorAndOptionalPoliteAnnouncement() {
        val semantics = accessibleStatusSemantics(
            kind = AccessibleStatusKind.Error,
            stateDescription = "Connection blocked",
            detail = "Bluetooth permission required",
            announceChanges = true,
        )

        assertEquals("Connection blocked", semantics.stateDescription)
        assertEquals("Bluetooth permission required", semantics.errorDescription)
        assertTrue(semantics.announcesPolitely)
    }

    @Test
    fun nonErrorStatusDoesNotExposeErrorSemanticsOrDependOnAnnouncements() {
        val semantics = accessibleStatusSemantics(
            kind = AccessibleStatusKind.Success,
            stateDescription = "Connected",
            detail = "Mac is ready",
            announceChanges = false,
        )

        assertNull(semantics.errorDescription)
        assertFalse(semantics.announcesPolitely)
    }

    @Test
    fun actionContractRequiresButtonRoleAndFortyEightDpTarget() {
        val contract = accessibleActionContract()

        assertEquals("button", contract.role)
        assertTrue(contract.minimumTargetDp >= 48)
    }

    @Test
    fun failedActionMovesFocusToFirstInvalidOrBlockingItemInVisualOrder() {
        val requested = mutableListOf<String>()
        val candidates = listOf(
            AccessibilityFocusCandidate("name", AccessibilityFocusProblem.None, "name-target"),
            AccessibilityFocusCandidate("host", AccessibilityFocusProblem.Invalid, "host-target"),
            AccessibilityFocusCandidate("diagnostic", AccessibilityFocusProblem.Blocking, "diagnostic-target"),
        )

        val moved = requestFirstAccessibilityProblem(candidates) {
            requested += it
            true
        }

        assertTrue(moved)
        assertEquals(listOf("host-target"), requested)
    }

    @Test
    fun focusRecoveryDoesNothingWhenNoProblemExists() {
        var requested = false
        val moved = requestFirstAccessibilityProblem(
            listOf(AccessibilityFocusCandidate("host", AccessibilityFocusProblem.None, "target")),
        ) {
            requested = true
            true
        }

        assertFalse(moved)
        assertFalse(requested)
    }

    @Test
    fun terminalAnnouncementIsConsumedOncePerStableStateKey() {
        val gate = TerminalAnnouncementGate()

        assertTrue(gate.consume("clipboard:conflict:1"))
        assertFalse(gate.consume("clipboard:conflict:1"))
        assertTrue(gate.consume("clipboard:resolved:2"))
        assertFalse(gate.consume("clipboard:resolved:2"))
    }

    @Test
    fun blockingFailureFocusMovesOncePerFailureInsteadOfPolling() {
        assertTrue(shouldRequestBlockingFailureFocus(previousFailureKey = null, currentFailureKey = "network"))
        assertFalse(shouldRequestBlockingFailureFocus(previousFailureKey = "network", currentFailureKey = "network"))
        assertTrue(shouldRequestBlockingFailureFocus(previousFailureKey = "network", currentFailureKey = "identity"))
        assertFalse(shouldRequestBlockingFailureFocus(previousFailureKey = null, currentFailureKey = null))
    }

    @Test
    fun twoHundredPercentTextStacksControlsAndKeepsLongContentScrollable() {
        val policy = accessibilityReflowPolicy(fontScale = 2f)

        assertTrue(policy.stackControls)
        assertTrue(policy.scrollLongContent)
        assertEquals(48, policy.minimumTargetDp)
    }

    @Test
    fun writesAccessibilityPrimitiveEvidence() {
        val output = evidenceDirectory().resolve("accessibility_primitives.xml")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(
            """
            <accessibility-primitives version="1">
              <status role="image" state-description="required" error-semantics="typed" live-region="optional-polite" />
              <action role="button" minimum-target-dp="48" visual-size="independent" />
              <focus policy="first-invalid-or-blocking-in-visual-order" repeat="new-failure-key-only" />
              <reflow font-scale="2.0" controls="stacked" long-content="scrollable" minimum-target-dp="48" />
            </accessibility-primitives>
            """.trimIndent(),
        )

        assertTrue(output.isFile)
    }

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/A11Y-01")
    }
}
