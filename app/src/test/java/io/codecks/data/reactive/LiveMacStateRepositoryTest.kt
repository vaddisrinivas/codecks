package io.codecks.data.reactive

import io.codecks.data.reactive.state.HelperMacStateSource
import io.codecks.data.reactive.state.SshMacStateSource
import io.codecks.domain.reactive.CapabilityAvailability
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacStateConnectionState
import io.codecks.domain.reactive.MacStateRefreshResult
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.StateSource
import io.codecks.domain.reactive.TrackpadVisibility
import io.codecks.shared.protocol.ReactiveCapabilityId
import io.codecks.shared.protocol.ReactiveHelperBasicState
import io.codecks.shared.protocol.StateProvenance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test
    fun helperSourceWinsWhenConnected() = runTest {
        val helper = FakeHelperMacStateSource(
            connected = true,
            state = helperState(
                revision = 9L,
                name = "Finder",
                provenance = StateProvenance.Helper,
            ),
        )
        var sshCalls = 0
        val repository = LiveMacStateRepository(
            helperSource = helper,
            sshSource = SshMacStateSource {
                sshCalls += 1
                helperState(revision = 10L, name = "Terminal", provenance = StateProvenance.Ssh)
            },
            nowMillis = { 10_100L },
        )
        repository.update(connectedInputs())

        val result = repository.refreshBasic()

        assertEquals(0, sshCalls)
        assertTrue(result is MacStateRefreshResult.Succeeded)
        assertEquals(StateSource.Helper, (result as MacStateRefreshResult.Succeeded).source)
        assertEquals("Finder", repository.state.value?.frontApp?.value?.displayName)
        assertEquals(MacStateConnectionState.Connected(StateSource.Helper), repository.connection.value)
    }

    @Test
    fun sshFallbackUsedWhenHelperDisconnected() = runTest {
        val helper = FakeHelperMacStateSource(
            connected = false,
            state = helperState(revision = 9L, name = "Finder", provenance = StateProvenance.Helper),
        )
        val repository = LiveMacStateRepository(
            helperSource = helper,
            sshSource = SshMacStateSource {
                helperState(revision = 3L, name = "Terminal", provenance = StateProvenance.Ssh)
            },
            nowMillis = { 10_100L },
        )
        repository.update(connectedInputs())

        val result = repository.refreshBasic()

        assertTrue(result is MacStateRefreshResult.Succeeded)
        assertEquals(StateSource.SshProbe, (result as MacStateRefreshResult.Succeeded).source)
        assertEquals("Terminal", repository.state.value?.frontApp?.value?.displayName)
        assertEquals(MacStateConnectionState.Connected(StateSource.SshProbe), repository.connection.value)
    }

    @Test
    fun sourceFailureMarksExistingStateStale() = runTest {
        var now = 10_100L
        val helper = FakeHelperMacStateSource(
            connected = true,
            state = helperState(revision = 4L, name = "Finder", provenance = StateProvenance.Helper),
        )
        val repository = LiveMacStateRepository(
            helperSource = helper,
            nowMillis = { now },
        )
        repository.update(connectedInputs())
        repository.refreshBasic()
        val freshRevision = repository.state.value!!.snapshotRevision
        now = 11_000L
        helper.failWith(IllegalStateException("helper down"))

        val result = repository.refreshBasic()

        assertTrue(result is MacStateRefreshResult.Failed)
        assertEquals(ObservationStatus.Stale, repository.state.value?.frontApp?.status)
        assertTrue(repository.state.value!!.snapshotRevision > freshRevision)
        assertTrue(repository.connection.value is MacStateConnectionState.Degraded)
    }

    @Test
    fun revisionsStayMonotonicAcrossLowerSourceRevision() = runTest {
        var sourceRevision = 1L
        val repository = LiveMacStateRepository(
            sshSource = SshMacStateSource {
                helperState(revision = sourceRevision, name = "Finder", provenance = StateProvenance.Ssh)
            },
            nowMillis = { 10_100L },
        )
        repository.update(connectedInputs())
        val first = (repository.refreshBasic() as MacStateRefreshResult.Succeeded).snapshotRevision!!
        sourceRevision = 1L

        val second = (repository.refreshBasic() as MacStateRefreshResult.Succeeded).snapshotRevision!!

        assertTrue(second > first)
    }

    @Test
    fun localCacheStateDegradesWhenAllSourcesFail() = runTest {
        val repository = LiveMacStateRepository(
            helperSource = FakeHelperMacStateSource(
                connected = true,
                failure = IllegalStateException("helper down"),
            ),
            sshSource = SshMacStateSource { error("ssh down") },
            nowMillis = { 10_100L },
        )
        repository.update(
            LiveMacStateInputs(
                selectedMacId = "mac_123",
                macCommandsReady = false,
                macInputConnected = false,
                activeMacApp = null,
            ),
        )

        val result = repository.refreshBasic()

        assertTrue(result is MacStateRefreshResult.Failed)
        assertEquals(ObservationStatus.Unavailable, repository.state.value?.frontApp?.status)
        assertEquals(true, repository.state.value?.stale)
        assertTrue(repository.connection.value is MacStateConnectionState.Degraded)
    }

    @Test
    fun helperTimeoutFallsBackToSshWithoutWaitingUnbounded() = runTest {
        val repository = LiveMacStateRepository(
            helperSource = FakeHelperMacStateSource(
                connected = true,
                state = helperState(revision = 1L, name = "Finder", provenance = StateProvenance.Helper),
                delayMillis = 1_000L,
            ),
            sshSource = SshMacStateSource {
                helperState(revision = 2L, name = "Terminal", provenance = StateProvenance.Ssh)
            },
            refreshTimeoutMillis = 10L,
            nowMillis = { 10_100L },
        )
        repository.update(connectedInputs())

        val result = repository.refreshBasic()

        assertTrue(result is MacStateRefreshResult.Succeeded)
        assertEquals(StateSource.SshProbe, (result as MacStateRefreshResult.Succeeded).source)
        assertEquals("Terminal", repository.state.value?.frontApp?.value?.displayName)
    }

    private fun connectedInputs() = LiveMacStateInputs(
        selectedMacId = "mac_123",
        macCommandsReady = true,
        macInputConnected = true,
        activeMacApp = null,
    )

    private fun helperState(
        revision: Long,
        name: String,
        provenance: StateProvenance,
    ) = ReactiveHelperBasicState(
        macId = "mac_123",
        snapshotRevision = revision,
        capturedAtMillis = 10_000L,
        freshnessMillis = 1_000L,
        provenance = provenance,
        frontAppBundleId = "com.apple.${name.lowercase()}",
        frontAppName = name,
        capabilities = setOf(ReactiveCapabilityId.FrontAppState, ReactiveCapabilityId.ActionExecute),
    )
}

private class FakeHelperMacStateSource(
    connected: Boolean,
    private var state: ReactiveHelperBasicState? = null,
    private var failure: Throwable? = null,
    private val delayMillis: Long = 0L,
) : HelperMacStateSource {
    private val connectedFlow = MutableStateFlow(connected)
    override val connected: Boolean
        get() = connectedFlow.value

    override suspend fun refreshBasicState(deadlineMillis: Long): ReactiveHelperBasicState {
        if (delayMillis > 0L) delay(delayMillis)
        failure?.let { throw it }
        return requireNotNull(state) { "missing_state" }
    }

    fun failWith(error: Throwable) {
        failure = error
        state = null
    }
}
