package io.codecks.core.trackpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockscreenTrackpadPolicyTest {
    private fun baseState(
        entryOrigin: TrackpadEntryOrigin = TrackpadEntryOrigin.ExactPublicUri,
    ) = LockscreenControlState(
        keyguardShowing = true,
        deviceLocked = true,
        userUnlockedSinceBoot = true,
        hidConnected = true,
        selectedHostPresent = true,
        bluetoothPermissionGranted = true,
        featureEnabled = true,
        entryOrigin = entryOrigin,
    )

    @Test
    fun unlockedDeviceForwardsToNormalTrackpad() {
        assertEquals(
            LockscreenDecision.ForwardToUnlockedTrackpad,
            LockscreenTrackpadPolicy.decision(
                baseState().copy(keyguardShowing = false, deviceLocked = false),
            ),
        )
    }

    @Test
    fun disconnectedPublicUriIsIgnored() {
        assertEquals(
            LockscreenDecision.IgnoreAutomaticEntry,
            LockscreenTrackpadPolicy.decision(baseState().copy(hidConnected = false)),
        )
    }

    @Test
    fun disconnectedInternalEntryRequiresUnlock() {
        assertEquals(
            LockscreenDecision.RequireUnlock,
            LockscreenTrackpadPolicy.decision(
                baseState(entryOrigin = TrackpadEntryOrigin.InternalWidget).copy(hidConnected = false),
            ),
        )
    }

    @Test
    fun unknownOriginFailsClosed() {
        assertEquals(
            LockscreenDecision.RequireUnlock,
            LockscreenTrackpadPolicy.decision(baseState(entryOrigin = TrackpadEntryOrigin.Unknown)),
        )
    }

    @Test
    fun onlyPointerCapabilitiesRemainAllowedWhileLocked() {
        val state = baseState()

        assertTrue(LockscreenTrackpadPolicy.allows(LockscreenCapability.PointerMove, state))
        assertTrue(LockscreenTrackpadPolicy.allows(LockscreenCapability.PointerScroll, state))
        assertTrue(LockscreenTrackpadPolicy.allows(LockscreenCapability.MouseButton, state))
        LockscreenCapability.entries
            .filterNot {
                it in setOf(
                    LockscreenCapability.PointerMove,
                    LockscreenCapability.PointerScroll,
                    LockscreenCapability.MouseButton,
                )
            }
            .forEach { capability ->
                assertFalse("capability should stay blocked on lockscreen: $capability", LockscreenTrackpadPolicy.allows(capability, state))
            }
    }

    @Test
    fun missingOptInFailsClosed() {
        val state = baseState().copy(featureEnabled = false)
        assertEquals(LockscreenDecision.RequireUnlock, LockscreenTrackpadPolicy.decision(state))
        assertFalse(LockscreenTrackpadPolicy.allows(LockscreenCapability.PointerMove, state))
    }

    @Test
    fun missingSelectedHostFailsClosed() {
        val state = baseState().copy(selectedHostPresent = false)
        assertEquals(LockscreenDecision.RequireUnlock, LockscreenTrackpadPolicy.decision(state))
        assertFalse(LockscreenTrackpadPolicy.allows(LockscreenCapability.PointerMove, state))
    }

    @Test
    fun missingBluetoothPermissionFailsClosed() {
        val state = baseState().copy(bluetoothPermissionGranted = false)
        assertEquals(LockscreenDecision.RequireUnlock, LockscreenTrackpadPolicy.decision(state))
        assertFalse(LockscreenTrackpadPolicy.allows(LockscreenCapability.MouseButton, state))
    }

    @Test
    fun preFirstUnlockFailsClosed() {
        val state = baseState().copy(userUnlockedSinceBoot = false)
        assertEquals(LockscreenDecision.RequireUnlock, LockscreenTrackpadPolicy.decision(state))
        assertFalse(LockscreenTrackpadPolicy.allows(LockscreenCapability.PointerScroll, state))
    }
}
