package io.codecks.domain.reactive.deskdock

import io.codecks.domain.reactive.CapabilityState
import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacApplication
import io.codecks.domain.reactive.MacClipboardMetadata
import io.codecks.domain.reactive.MacId
import io.codecks.domain.reactive.MacMeetingState
import io.codecks.domain.reactive.MacMediaState
import io.codecks.domain.reactive.MacScreenshotState
import io.codecks.domain.reactive.MacSelection
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.MacSystemState
import io.codecks.domain.reactive.MacWindow
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.Observed
import io.codecks.domain.reactive.StateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeskDockScoringEngineTest {
    private val engine = DeskDockScoringEngine(
        DeskDockConfig(
            enterThreshold = 75,
            exitThreshold = 45,
            minDwellMillis = 10_000L,
            cooldownMillis = 20_000L,
        ),
    )

    @Test
    fun singleDriftingSensorCannotLaunch() {
        val decision = engine.evaluate(
            signals = DeskDockSignals(
                capturedAtMillis = 1_000L,
                ambientLightStable = DeskDockSignalState.Positive,
            ),
        )

        assertEquals(DeskDockState.Away, decision.state)
        assertFalse(decision.eligibleForLaunch)
        assertEquals(10, decision.score.confidence)
    }

    @Test
    fun highConfidenceRequiresDwellBeforeDocked() {
        val first = engine.evaluate(confidentSignals(nowMillis = 10_000L))
        val second = engine.evaluate(confidentSignals(nowMillis = 15_000L), first.session)
        val third = engine.evaluate(confidentSignals(nowMillis = 20_001L), second.session)

        assertEquals(DeskDockState.Candidate, first.state)
        assertEquals(DeskDockState.Candidate, second.state)
        assertEquals(DeskDockState.Docked, third.state)
        assertTrue(third.eligibleForLaunch)
    }

    @Test
    fun dockedStateUsesExitHysteresis() {
        val docked = DeskDockSession(state = DeskDockState.Docked, dockedSinceMillis = 10_000L)

        val stillDocked = engine.evaluate(
            signals = DeskDockSignals(
                capturedAtMillis = 11_000L,
                bluetoothNear = DeskDockSignalState.Positive,
                charging = DeskDockSignalState.Positive,
            ),
            previous = docked,
        )
        val exited = engine.evaluate(
            signals = DeskDockSignals(capturedAtMillis = 12_000L),
            previous = stillDocked.session,
        )

        assertEquals(DeskDockState.Docked, stillDocked.state)
        assertEquals(DeskDockState.Cooldown, exited.state)
        assertFalse(exited.eligibleForLaunch)
    }

    @Test
    fun cooldownBlocksImmediateRelaunch() {
        val cooldown = DeskDockSession(
            state = DeskDockState.Cooldown,
            cooldownUntilMillis = 30_000L,
        )

        val blocked = engine.evaluate(confidentSignals(nowMillis = 20_000L), cooldown)
        val allowedCandidate = engine.evaluate(confidentSignals(nowMillis = 31_000L), blocked.session)

        assertEquals(DeskDockState.Cooldown, blocked.state)
        assertTrue("cooldown_active" in blocked.score.reasons)
        assertEquals(DeskDockState.Candidate, allowedCandidate.state)
    }

    @Test
    fun manualOverrideWinsAndCanBeRevokedByNewSignals() {
        val forced = engine.evaluate(
            signals = DeskDockSignals(
                capturedAtMillis = 10_000L,
                manualOverride = DeskDockManualOverride.ForceDocked,
            ),
        )
        val revoked = engine.evaluate(
            signals = DeskDockSignals(
                capturedAtMillis = 11_000L,
                manualOverride = DeskDockManualOverride.None,
            ),
            previous = forced.session.copy(manualOverride = DeskDockManualOverride.None),
        )

        assertEquals(DeskDockState.Docked, forced.state)
        assertEquals(100, forced.score.confidence)
        assertEquals(DeskDockState.Cooldown, revoked.state)
    }

    @Test
    fun recordedTraceIsDeterministic() {
        val trace = listOf(
            confidentSignals(nowMillis = 10_000L),
            confidentSignals(nowMillis = 20_001L),
            DeskDockSignals(capturedAtMillis = 25_000L, bluetoothNear = DeskDockSignalState.Positive),
        )

        val first = replay(trace)
        val second = replay(trace)

        assertEquals(first.map { it.state }, second.map { it.state })
        assertEquals(first.map { it.score }, second.map { it.score })
    }

    @Test
    fun freshMacStateAddsBoundedConfidence() {
        val score = engine.score(
            DeskDockSignals(
                capturedAtMillis = 10_000L,
                macState = macState(capturedAtMillis = 9_000L),
            ),
        )

        assertEquals(10, score.confidence)
        assertTrue("mac_state_fresh" in score.reasons)
    }

    private fun replay(trace: List<DeskDockSignals>): List<DeskDockDecision> {
        var session = DeskDockSession()
        return trace.map { signals ->
            engine.evaluate(signals, session).also { session = it.session }
        }
    }

    private fun confidentSignals(nowMillis: Long): DeskDockSignals = DeskDockSignals(
        capturedAtMillis = nowMillis,
        bluetoothNear = DeskDockSignalState.Positive,
        charging = DeskDockSignalState.Positive,
        faceUp = DeskDockSignalState.Positive,
        stationary = DeskDockSignalState.Positive,
        ambientLightStable = DeskDockSignalState.Positive,
    )

    private fun macState(capturedAtMillis: Long): MacStateSnapshot {
        fun <T> observed(value: T?): Observed<T> = Observed(
            value = value,
            status = if (value == null) ObservationStatus.Unavailable else ObservationStatus.Fresh,
            observedAtMillis = capturedAtMillis,
            source = StateSource.Helper,
        )
        return MacStateSnapshot(
            macId = MacId("mac"),
            snapshotRevision = 1L,
            capturedAtMillis = capturedAtMillis,
            frontApp = observed(MacApplication("com.apple.finder", "Finder", MacAppKind.Finder)),
            activeWindow = observed<MacWindow>(null),
            displays = observed(emptyList()),
            cursor = observed(null),
            selection = observed(MacSelection.None),
            clipboard = observed<MacClipboardMetadata>(null),
            media = observed<MacMediaState>(null),
            system = observed<MacSystemState>(null),
            meeting = observed<MacMeetingState>(null),
            latestScreenshot = observed<MacScreenshotState>(null),
            capabilities = emptySet<CapabilityState>(),
        )
    }
}
