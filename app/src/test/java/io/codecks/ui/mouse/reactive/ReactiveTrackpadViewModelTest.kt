package io.codecks.ui.mouse.reactive

import androidx.lifecycle.viewModelScope
import io.codecks.domain.reactive.CapabilityState
import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacApplication
import io.codecks.domain.reactive.MacId
import io.codecks.domain.reactive.MacSelection
import io.codecks.domain.reactive.MacStateConnectionState
import io.codecks.domain.reactive.MacStateRefreshResult
import io.codecks.domain.reactive.MacStateRepository
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.Observed
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveActionExecutor
import io.codecks.domain.reactive.ReactiveActionInvocation
import io.codecks.domain.reactive.ReactiveActionResult
import io.codecks.domain.reactive.ReactiveAuthorization
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveDecision
import io.codecks.domain.reactive.ReactiveEngine
import io.codecks.domain.reactive.ReactiveExecutionOutcome
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.ReactiveTrackpadMode
import io.codecks.domain.reactive.ReactiveUndoOutcome
import io.codecks.domain.reactive.ReceiptId
import io.codecks.domain.reactive.StateSource
import io.codecks.domain.reactive.TrackpadVisibility
import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.ControlId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReactiveTrackpadViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun visibleStartsRepositoryAndHiddenStopsIt() = runTest(dispatcher) {
        val repo = FakeMacStateRepository()
        val viewModel = ReactiveTrackpadViewModel(
            macStateRepository = repo,
            engine = FakeReactiveEngine(),
            executor = FakeReactiveExecutor(),
            nowMillis = { 9_000L },
        )

        viewModel.setVisible(true)
        viewModel.setVisible(false)

        assertEquals(listOf(TrackpadVisibility.Visible), repo.startCalls)
        assertEquals(1, repo.stopCalls)
    }

    @Test
    fun uiStateReflectsControlsAndConnectionLabel() = runTest(dispatcher) {
        val repo = FakeMacStateRepository()
        val engine = FakeReactiveEngine(
            decision = ReactiveDecision(
                controls = listOf(sampleControl()),
            ),
        )
        val viewModel = ReactiveTrackpadViewModel(
            macStateRepository = repo,
            engine = engine,
            executor = FakeReactiveExecutor(),
            nowMillis = { 9_000L },
        )

        repo.connectionFlow.value = MacStateConnectionState.Connected(StateSource.Helper)
        repo.stateFlow.value = sampleState()
        advanceUntilIdle()

        assertEquals("Google Chrome ready", viewModel.uiState.value.connectionLabel)
        assertEquals(listOf("Reload"), viewModel.uiState.value.controls.map { it.title })
    }

    @Test
    fun successUpdatesLastResult() = runTest(dispatcher) {
        val repo = FakeMacStateRepository(initialState = sampleState())
        val executor = FakeReactiveExecutor(
            next = ReactiveExecutionOutcome(ReactiveActionResult.Succeeded("catalog_action_succeeded")),
        )
        val viewModel = ReactiveTrackpadViewModel(
            macStateRepository = repo,
            engine = FakeReactiveEngine(ReactiveDecision(controls = listOf(sampleControl()))),
            executor = executor,
            nowMillis = { 9_000L },
        )
        advanceUntilIdle()

        viewModel.runControl(sampleControl().id)
        advanceUntilIdle()

        assertEquals(ReactiveActionResult.Succeeded("catalog_action_succeeded"), viewModel.uiState.value.lastResult)
        assertNull(viewModel.uiState.value.pendingConfirmation)
        assertEquals(sampleControl().id, executor.lastControlId)
    }

    @Test
    fun successRefreshesControlsForReceiptBackedUndoProvider() = runTest(dispatcher) {
        val repo = FakeMacStateRepository(initialState = sampleState())
        val engine = SequencedReactiveEngine(
            ArrayDeque(
                listOf(
                    ReactiveDecision(controls = listOf(sampleControl())),
                    ReactiveDecision(controls = listOf(sampleControl(), sampleControl(id = "reactive_undo", title = "Undo Reload"))),
                ),
            ),
        )
        val viewModel = ReactiveTrackpadViewModel(
            macStateRepository = repo,
            engine = engine,
            executor = FakeReactiveExecutor(
                next = ReactiveExecutionOutcome(ReactiveActionResult.Succeeded("catalog_action_succeeded")),
            ),
            nowMillis = { 9_000L },
        )
        advanceUntilIdle()

        viewModel.runControl(sampleControl().id)
        advanceUntilIdle()

        assertEquals(listOf("Reload", "Undo Reload"), viewModel.uiState.value.controls.map { it.title })
        assertEquals(2, engine.calls)
    }


    @Test
    fun confirmationFlowsThroughPendingAndConfirm() = runTest(dispatcher) {
        val repo = FakeMacStateRepository(initialState = sampleState())
        val executor = FakeReactiveExecutor(
            nextSequence = ArrayDeque(
                listOf(
                    ReactiveExecutionOutcome(
                        ReactiveActionResult.RequiresConfirmation(
                            actionRevision = sampleControl().actionRevision,
                            title = "Confirm Reload",
                            body = "Needs confirmation",
                        ),
                    ),
                    ReactiveExecutionOutcome(ReactiveActionResult.Succeeded("catalog_action_succeeded")),
                ),
            ),
        )
        val viewModel = ReactiveTrackpadViewModel(
            macStateRepository = repo,
            engine = FakeReactiveEngine(ReactiveDecision(controls = listOf(sampleControl()))),
            executor = executor,
            nowMillis = { 9_000L },
        )
        advanceUntilIdle()

        viewModel.runControl(sampleControl().id)
        advanceUntilIdle()
        assertEquals("Confirm Reload", viewModel.uiState.value.pendingConfirmation?.title)

        viewModel.confirmPending()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingConfirmation)
        assertEquals(ReactiveActionResult.Succeeded("catalog_action_succeeded"), viewModel.uiState.value.lastResult)
        assertEquals(sampleControl().actionRevision, executor.lastAuthorization?.confirmedActionRevision)
    }

    @Test
    fun reviewedRunPassesReviewedRevision() = runTest(dispatcher) {
        val repo = FakeMacStateRepository(initialState = sampleState())
        val executor = FakeReactiveExecutor(
            next = ReactiveExecutionOutcome(ReactiveActionResult.Succeeded("catalog_action_succeeded")),
        )
        val viewModel = ReactiveTrackpadViewModel(
            macStateRepository = repo,
            engine = FakeReactiveEngine(ReactiveDecision(controls = listOf(sampleControl()))),
            executor = executor,
            nowMillis = { 9_000L },
        )
        advanceUntilIdle()

        viewModel.runReviewed(sampleControl().id)
        advanceUntilIdle()

        assertEquals(sampleControl().actionRevision, executor.lastAuthorization?.reviewedActionRevision)
    }

    private fun sampleControl(
        id: String = "reactive_reload",
        title: String = "Reload",
    ): ReactiveControl = ReactiveControl(
        id = ControlId(id),
        title = title,
        subtitle = "Refresh tab",
        icon = ReactiveIcon.Reload,
        action = ReactiveAction.ExistingCatalog("reload"),
        source = io.codecks.domain.reactive.ReactiveControlSource.FrontApp,
        basePriority = 10,
        reason = "Useful while browser is active",
        requiredCapabilities = emptySet(),
        risk = ReactiveRisk.Safe,
        reversible = false,
        stateRevision = 1L,
        actionRevision = ActionRevision("rev_reload"),
        expiresAtMillis = Long.MAX_VALUE,
    )

    private fun sampleState(): MacStateSnapshot = MacStateSnapshot(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 1L,
        capturedAtMillis = 9_000L,
        frontApp = observed(
            MacApplication(
                bundleId = "com.google.Chrome",
                displayName = "Google Chrome",
                kind = MacAppKind.Browser,
            ),
        ),
        activeWindow = observed<io.codecks.domain.reactive.MacWindow>(null),
        displays = observed(emptyList()),
        cursor = observed<io.codecks.domain.reactive.MacCursorState>(null),
        selection = observed(MacSelection.None),
        clipboard = observed<io.codecks.domain.reactive.MacClipboardMetadata>(null),
        media = observed<io.codecks.domain.reactive.MacMediaState>(null),
        system = observed<io.codecks.domain.reactive.MacSystemState>(null),
        meeting = observed<io.codecks.domain.reactive.MacMeetingState>(null),
        latestScreenshot = observed<io.codecks.domain.reactive.MacScreenshotState>(null),
        capabilities = emptySet<CapabilityState>(),
    )

    private fun <T> observed(value: T?): Observed<T> = Observed(
        value = value,
        status = if (value == null) ObservationStatus.Unavailable else ObservationStatus.Fresh,
        observedAtMillis = 9_000L,
        source = StateSource.Helper,
    )
}

private class FakeMacStateRepository(
    initialState: MacStateSnapshot? = null,
) : MacStateRepository {
    val stateFlow = MutableStateFlow(initialState)
    val connectionFlow = MutableStateFlow<MacStateConnectionState>(MacStateConnectionState.Idle)
    val startCalls = mutableListOf<TrackpadVisibility>()
    var stopCalls = 0

    override val state: StateFlow<MacStateSnapshot?> = stateFlow
    override val connection: StateFlow<MacStateConnectionState> = connectionFlow

    override suspend fun refreshBasic(): MacStateRefreshResult =
        MacStateRefreshResult.Skipped("not_needed")

    override suspend fun refreshDisplays(): MacStateRefreshResult =
        MacStateRefreshResult.Skipped("not_needed")

    override suspend fun refreshClipboardMetadata(): MacStateRefreshResult =
        MacStateRefreshResult.Skipped("not_needed")

    override suspend fun refreshMedia(): MacStateRefreshResult =
        MacStateRefreshResult.Skipped("not_needed")

    override suspend fun inspectSelection(): MacStateRefreshResult =
        MacStateRefreshResult.Skipped("not_needed")

    override fun start(visibility: TrackpadVisibility) {
        startCalls += visibility
    }

    override fun stop() {
        stopCalls += 1
    }
}

private class FakeReactiveEngine(
    private val decision: ReactiveDecision = ReactiveDecision(emptyList()),
) : ReactiveEngine {
    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): ReactiveDecision = decision
}

private class SequencedReactiveEngine(
    private val decisions: ArrayDeque<ReactiveDecision>,
) : ReactiveEngine {
    var calls = 0

    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): ReactiveDecision {
        calls += 1
        return decisions.removeFirstOrNull() ?: ReactiveDecision(emptyList())
    }
}

private class FakeReactiveExecutor(
    private val next: ReactiveExecutionOutcome = ReactiveExecutionOutcome(ReactiveActionResult.Succeeded("ok")),
    private val nextSequence: ArrayDeque<ReactiveExecutionOutcome> = ArrayDeque(),
) : ReactiveActionExecutor {
    var lastControlId: ControlId? = null
    var lastAuthorization: ReactiveAuthorization? = null

    override suspend fun execute(
        control: ReactiveControl,
        authorization: ReactiveAuthorization,
        nowMillis: Long,
        currentState: MacStateSnapshot?,
        invocation: ReactiveActionInvocation,
    ): ReactiveExecutionOutcome {
        lastControlId = control.id
        lastAuthorization = authorization
        return nextSequence.removeFirstOrNull() ?: next
    }

    override suspend fun undo(
        receiptId: ReceiptId,
        nowMillis: Long,
        invocation: ReactiveActionInvocation?,
    ): ReactiveUndoOutcome = ReactiveUndoOutcome.Unsupported("test_executor_no_undo")
}
