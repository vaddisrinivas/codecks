package io.codecks.ui.app

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
        assertNull(semantics.detailDescription)
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
        assertEquals("Mac is ready", semantics.detailDescription)
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
    fun liveRegionRemainsEnabledSoCommittedTextChangesCanAnnounce() {
        assertTrue(
            accessibleStatusSemantics(
                kind = AccessibleStatusKind.Error,
                stateDescription = "Conflict",
                detail = null,
                announceChanges = true,
            ).announcesPolitely,
        )
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

}
