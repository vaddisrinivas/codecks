package io.codecks.data.reactive

import io.codecks.domain.reactive.CapabilityAvailability
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacStateConnectionState
import io.codecks.domain.reactive.MacStateRefreshResult
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.TrackpadVisibility
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMacStateRepositoryTest {
    @Test
    fun noSelectedMacStaysIdle() {
        val repository = LiveMacStateRepository(nowMillis = { 1_000L })

        repository.update(
            LiveMacStateInputs(
                selectedMacId = null,
                macCommandsReady = false,
                macInputConnected = false,
                activeMacApp = null,
            ),
        )

        assertNull(repository.state.value)
        assertEquals(MacStateConnectionState.Idle, repository.connection.value)
    }

    @Test
    fun connectedInputsProduceFreshFrontAppAndCapabilities() {
        val repository = LiveMacStateRepository(nowMillis = { 2_000L })

        repository.update(
            LiveMacStateInputs(
                selectedMacId = "mac_123",
                macCommandsReady = true,
                macInputConnected = true,
                activeMacApp = "Google Chrome",
            ),
        )

        val snapshot = repository.state.value
        assertNotNull(snapshot)
        snapshot!!
        assertEquals("Google Chrome", snapshot.frontApp.value?.displayName)
        assertEquals(MacAppKind.Browser, snapshot.frontApp.value?.kind)
        assertEquals(ObservationStatus.Fresh, snapshot.frontApp.status)
        assertEquals(
            CapabilityAvailability.Available,
            snapshot.capabilities.first { it.capability == CodecksCapability.MacCommand }.availability,
        )
        assertEquals(
            CapabilityAvailability.Available,
            snapshot.capabilities.first { it.capability == CodecksCapability.PointerInput }.availability,
        )
        assertTrue(repository.connection.value is MacStateConnectionState.Connected)
    }

    @Test
    fun partialConnectivityIsDegradedAndKeepsStaleFrontApp() {
        var now = 3_000L
        val repository = LiveMacStateRepository(nowMillis = { now })

        repository.update(
            LiveMacStateInputs(
                selectedMacId = "mac_123",
                macCommandsReady = true,
                macInputConnected = true,
                activeMacApp = "Finder",
            ),
        )
        now = 4_000L

        repository.update(
            LiveMacStateInputs(
                selectedMacId = "mac_123",
                macCommandsReady = false,
                macInputConnected = true,
                activeMacApp = null,
            ),
        )

        val snapshot = repository.state.value
        assertNotNull(snapshot)
        snapshot!!
        assertEquals("Finder", snapshot.frontApp.value?.displayName)
        assertEquals(ObservationStatus.Stale, snapshot.frontApp.status)
        assertTrue(repository.connection.value is MacStateConnectionState.Degraded)
    }

    @Test
    fun refreshBasicRebuildsSnapshotWhenMacExists() = runTest {
        var now = 5_000L
        val repository = LiveMacStateRepository(nowMillis = { now })
        repository.update(
            LiveMacStateInputs(
                selectedMacId = "mac_123",
                macCommandsReady = true,
                macInputConnected = false,
                activeMacApp = "Terminal",
            ),
        )
        val firstSnapshot = repository.state.value
        assertNotNull(firstSnapshot)
        val firstRevision = firstSnapshot!!.snapshotRevision
        now = 6_000L
        repository.start(TrackpadVisibility.Visible)

        val result = repository.refreshBasic()

        val secondSnapshot = repository.state.value
        assertNotNull(secondSnapshot)
        val secondRevision = secondSnapshot!!.snapshotRevision
        assertTrue(result is MacStateRefreshResult.Succeeded)
        assertTrue(secondRevision > firstRevision)
    }
}
