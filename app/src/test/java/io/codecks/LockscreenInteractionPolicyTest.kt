package io.codecks

import io.codecks.core.trackpad.LockscreenCapability
import io.codecks.core.trackpad.LockscreenControlState
import io.codecks.core.trackpad.LockscreenDecision
import io.codecks.core.trackpad.LockscreenTrackpadPolicy
import io.codecks.core.trackpad.TrackpadEntryOrigin
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockscreenInteractionPolicyTest {
    @Test
    fun lockTriggerRestrictsRepositoryStateToPointerOnly() {
        val locked = reduceHidSystemEvent(
            HidState(userLockState = HidUserLockState.Unlocked, inputAccess = HidInputAccess.Full),
            HidSystemEvent.UserLocked,
        ).state

        assertEquals(HidUserLockState.Locked, locked.userLockState)
        assertEquals(HidInputAccess.PointerOnly, locked.inputAccess)
        val source = File("src/main/java/io/codecks/HidRepository.kt").readText()
        assertTrue(source.contains("if (_state.value.inputAccess == HidInputAccess.Full) controller.typeText(text)"))
        assertTrue(source.contains("if (_state.value.inputAccess != HidInputAccess.Full) return"))
    }

    @Test
    fun restrictedLockscreenAllowsOnlyPointerCapabilities() {
        val state = LockscreenControlState(
            keyguardShowing = true,
            deviceLocked = true,
            userUnlockedSinceBoot = true,
            hidConnected = true,
            selectedHostPresent = true,
            bluetoothPermissionGranted = true,
            featureEnabled = true,
            entryOrigin = TrackpadEntryOrigin.InternalWidget,
        )

        assertEquals(LockscreenDecision.AllowRestrictedPointer, LockscreenTrackpadPolicy.decision(state))
        assertTrue(LockscreenTrackpadPolicy.allows(LockscreenCapability.PointerMove, state))
        assertTrue(LockscreenTrackpadPolicy.allows(LockscreenCapability.PointerScroll, state))
        assertTrue(LockscreenTrackpadPolicy.allows(LockscreenCapability.MouseButton, state))
        listOf(
            LockscreenCapability.Keyboard,
            LockscreenCapability.HidShortcut,
            LockscreenCapability.DeckAction,
            LockscreenCapability.ReactiveAction,
            LockscreenCapability.Clipboard,
            LockscreenCapability.Settings,
        ).forEach { capability ->
            assertFalse("$capability must remain guarded", LockscreenTrackpadPolicy.allows(capability, state))
        }
    }
}
