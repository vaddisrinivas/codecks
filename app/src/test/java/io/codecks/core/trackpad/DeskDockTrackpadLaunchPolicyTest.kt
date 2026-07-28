package io.codecks.core.trackpad

import io.codecks.domain.reactive.deskdock.DeskDockConfig
import io.codecks.domain.reactive.deskdock.DeskDockManualOverride
import io.codecks.domain.reactive.deskdock.DeskDockScoringEngine
import io.codecks.domain.reactive.deskdock.DeskDockSignalState
import io.codecks.domain.reactive.deskdock.DeskDockSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeskDockTrackpadLaunchPolicyTest {
    private val policy = DeskDockTrackpadLaunchPolicy(
        DeskDockScoringEngine(
            DeskDockConfig(
                enterThreshold = 75,
                exitThreshold = 45,
                minDwellMillis = 10_000L,
                cooldownMillis = 20_000L,
            ),
        ),
    )

    @Test
    fun highConfidenceOnlyLaunchesAfterDwell() {
        val first = policy.evaluate(input(signals = confidentSignals(10_000L)))
        val second = policy.evaluate(input(signals = confidentSignals(15_000L), previous = first.deskDock.session))
        val third = policy.evaluate(input(signals = confidentSignals(20_001L), previous = second.deskDock.session))

        assertEquals(DeskDockTrackpadRecommendation.None, first.recommendation)
        assertEquals(DeskDockTrackpadRecommendation.None, second.recommendation)
        assertEquals(DeskDockTrackpadRecommendation.LaunchUnlockedTrackpad, third.recommendation)
    }

    @Test
    fun backgroundSignalCanSuggestButCannotLaunch() {
        val candidate = policy.evaluate(
            input(
                signals = confidentSignals(20_001L),
                previous = input(signals = confidentSignals(10_000L)).let { policy.evaluate(it).deskDock.session },
                trigger = DeskDockLaunchTrigger.BackgroundSignal,
            ),
        )

        assertEquals(DeskDockTrackpadRecommendation.SuggestTrackpad, candidate.recommendation)
        assertTrue("background_launch_blocked" in candidate.reasons)
    }

    @Test
    fun lockedEntryLaunchesRestrictedTrackpadOnly() {
        val candidate = policy.evaluate(
            input(
                signals = confidentSignals(20_001L),
                previous = policy.evaluate(input(signals = confidentSignals(10_000L))).deskDock.session,
                lockscreenState = lockedState(),
                trigger = DeskDockLaunchTrigger.ExternalAutomation,
            ),
        )

        assertEquals(LockscreenDecision.AllowRestrictedPointer, candidate.lockscreenDecision)
        assertEquals(DeskDockTrackpadRecommendation.LaunchRestrictedTrackpad, candidate.recommendation)
    }

    @Test
    fun lockedDisconnectedEntryCannotLaunch() {
        val candidate = policy.evaluate(
            input(
                signals = confidentSignals(20_001L),
                previous = policy.evaluate(input(signals = confidentSignals(10_000L))).deskDock.session,
                lockscreenState = lockedState(hidConnected = false),
                hidConnected = false,
                trigger = DeskDockLaunchTrigger.ExternalAutomation,
            ),
        )

        assertEquals(DeskDockTrackpadRecommendation.None, candidate.recommendation)
        assertTrue("hid_disconnected" in candidate.reasons)
    }

    @Test
    fun disabledFeatureCannotLaunchEvenWhenDocked() {
        val candidate = policy.evaluate(
            input(
                signals = confidentSignals(20_001L),
                previous = policy.evaluate(input(signals = confidentSignals(10_000L))).deskDock.session,
                featureEnabled = false,
            ),
        )

        assertEquals(DeskDockTrackpadRecommendation.None, candidate.recommendation)
        assertTrue("deskdock_disabled" in candidate.reasons)
    }

    @Test
    fun automaticLaunchOptInCanBeSeparatedFromDeskStatus() {
        val candidate = policy.evaluate(
            input(
                signals = confidentSignals(20_001L),
                previous = policy.evaluate(input(signals = confidentSignals(10_000L))).deskDock.session,
                automaticLaunchEnabled = false,
            ),
        )

        assertEquals(DeskDockTrackpadRecommendation.SuggestTrackpad, candidate.recommendation)
        assertTrue("automatic_launch_disabled" in candidate.reasons)
    }

    @Test
    fun suspendedManualOverrideBlocksLaunch() {
        val candidate = policy.evaluate(
            input(
                signals = DeskDockSignals(
                    capturedAtMillis = 20_001L,
                    manualOverride = DeskDockManualOverride.Suspended,
                ),
            ),
        )

        assertEquals(DeskDockTrackpadRecommendation.None, candidate.recommendation)
        assertTrue("manual_suspended" in candidate.reasons)
    }

    private fun input(
        signals: DeskDockSignals,
        previous: io.codecks.domain.reactive.deskdock.DeskDockSession =
            io.codecks.domain.reactive.deskdock.DeskDockSession(),
        lockscreenState: LockscreenControlState = unlockedState(),
        featureEnabled: Boolean = true,
        automaticLaunchEnabled: Boolean = true,
        hidConnected: Boolean = true,
        trigger: DeskDockLaunchTrigger = DeskDockLaunchTrigger.ExternalAutomation,
    ) = DeskDockTrackpadLaunchInput(
        signals = signals,
        previousSession = previous,
        lockscreenState = lockscreenState,
        featureEnabled = featureEnabled,
        automaticLaunchEnabled = automaticLaunchEnabled,
        hidConnected = hidConnected,
        trigger = trigger,
    )

    private fun unlockedState() = lockedState().copy(
        keyguardShowing = false,
        deviceLocked = false,
    )

    private fun lockedState(
        hidConnected: Boolean = true,
    ) = LockscreenControlState(
        keyguardShowing = true,
        deviceLocked = true,
        userUnlockedSinceBoot = true,
        hidConnected = hidConnected,
        selectedHostPresent = true,
        bluetoothPermissionGranted = true,
        featureEnabled = true,
        entryOrigin = TrackpadEntryOrigin.ExactPublicUri,
    )

    private fun confidentSignals(nowMillis: Long): DeskDockSignals = DeskDockSignals(
        capturedAtMillis = nowMillis,
        bluetoothNear = DeskDockSignalState.Positive,
        charging = DeskDockSignalState.Positive,
        faceUp = DeskDockSignalState.Positive,
        stationary = DeskDockSignalState.Positive,
        ambientLightStable = DeskDockSignalState.Positive,
    )
}
