package io.codecks.ui.home.smart

import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.ActionStatus
import io.codecks.domain.DeckAction
import io.codecks.domain.LocalActionResult
import io.codecks.domain.smart.SmartActionRef
import io.codecks.domain.smart.SmartAppKey
import io.codecks.domain.smart.SmartCandidate
import io.codecks.domain.smart.SmartConfidenceLabel
import io.codecks.domain.smart.SmartContext
import io.codecks.domain.smart.SmartDecision
import io.codecks.domain.smart.SmartEngine
import io.codecks.domain.smart.SmartFeedback
import io.codecks.domain.smart.SmartFeedbackSummary
import io.codecks.domain.smart.SmartFeedbackType
import io.codecks.domain.smart.SmartPhoneContext
import io.codecks.domain.smart.SmartRisk
import io.codecks.domain.smart.SmartSurface
import io.codecks.domain.smart.smartCandidateId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmartDeckViewModelBehaviorTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun disabledDeckBuildsNoContextAndWritesNoLearningState() = runTest(dispatcher) {
        val fixture = fixture()

        fixture.viewModel.updateInputs(
            enabledInputs().copy(
                smartDeckEnabled = false,
                onHomeRoute = false,
            ),
        )
        runCurrent()

        assertTrue(fixture.viewModel.suggestions.value.isEmpty())
        assertNull(fixture.viewModel.smartContext.value)
        assertEquals(0, fixture.contextSource.callCount)
        assertTrue(fixture.learningStore.records.isEmpty())
        assertEquals(0, fixture.learningStore.clearCount)
        assertEquals(0, fixture.engine.callCount)
    }

    @Test
    fun enabledDeckRefreshesDeterministicContextAndSuggestions() = runTest(dispatcher) {
        val fixture = fixture(nowMillis = 42_000L)

        fixture.viewModel.updateInputs(enabledInputs())
        runCurrent()

        assertEquals(1, fixture.contextSource.callCount)
        assertEquals(42_000L, fixture.contextSource.requestedTimes.single())
        assertEquals(SmartSurface.Deck, fixture.viewModel.smartContext.value?.currentSurface)
        assertEquals(listOf(ACTION_ID), fixture.viewModel.suggestions.value.map { it.action.id })
        assertEquals(1, fixture.engine.callCount)
        fixture.stop()
    }

    @Test
    fun temporaryHideResetsWhenContextChangesAndNeverExecutesAction() = runTest(dispatcher) {
        val fixture = fixture()
        val effects = mutableListOf<SmartDeckEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.viewModel.effects.toList(effects)
        }
        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_A))
        runCurrent()
        val suggestion = fixture.singleSuggestion()

        fixture.viewModel.hide(suggestion)
        runCurrent()

        assertTrue(fixture.viewModel.suggestions.value.isEmpty())
        assertTrue(effects.isEmpty())
        assertTrue(fixture.learningStore.records.isEmpty())

        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_B))
        runCurrent()

        assertEquals(listOf(ACTION_ID), fixture.viewModel.suggestions.value.map { it.action.id })
        assertTrue(effects.isEmpty())
        fixture.stop()
    }

    @Test
    fun suppressHerePersistsOnlyForMatchingContext() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_A))
        runCurrent()

        fixture.viewModel.suppressHere(fixture.singleSuggestion())
        runCurrent()

        assertTrue(fixture.viewModel.suggestions.value.isEmpty())
        assertEquals(SmartFeedbackType.SuppressHere, fixture.learningStore.records.single().type)

        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_B))
        runCurrent()
        assertEquals(listOf(ACTION_ID), fixture.viewModel.suggestions.value.map { it.action.id })

        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_A))
        runCurrent()
        assertTrue(fixture.viewModel.suggestions.value.isEmpty())
        fixture.stop()
    }

    @Test
    fun neverPersistsAcrossContexts() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_A))
        runCurrent()

        fixture.viewModel.never(fixture.singleSuggestion())
        runCurrent()

        assertEquals(SmartFeedbackType.NeverGlobal, fixture.learningStore.records.single().type)
        assertTrue(fixture.viewModel.suggestions.value.isEmpty())

        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_B))
        runCurrent()
        assertTrue(fixture.viewModel.suggestions.value.isEmpty())
        fixture.stop()
    }

    @Test
    fun runOnlyEmitsExecutionRequest() = runTest(dispatcher) {
        val fixture = fixture()
        val effects = mutableListOf<SmartDeckEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.viewModel.effects.toList(effects)
        }
        fixture.viewModel.updateInputs(enabledInputs())
        runCurrent()
        val suggestion = fixture.singleSuggestion()

        fixture.viewModel.run(suggestion)
        runCurrent()

        val request = (effects.single() as SmartDeckEffect.Execute).request
        assertEquals(suggestion, request.suggestion)
        assertEquals(APP_A, request.context.activeMacApp)
        assertEquals(false, request.allowDangerous)
        assertTrue(fixture.viewModel.runPending.value)
        assertTrue(fixture.learningStore.records.isEmpty())
        fixture.stop()
    }

    @Test
    fun pinRecordsFeedbackAndEmitsPinRequest() = runTest(dispatcher) {
        val fixture = fixture()
        val effects = mutableListOf<SmartDeckEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.viewModel.effects.toList(effects)
        }
        fixture.viewModel.updateInputs(enabledInputs())
        runCurrent()
        val suggestion = fixture.singleSuggestion()

        fixture.viewModel.pin(suggestion)
        runCurrent()

        assertEquals(listOf(SmartDeckEffect.Pin(suggestion)), effects)
        assertEquals(SmartFeedbackType.Pin, fixture.learningStore.records.single().type)
        fixture.stop()
    }

    @Test
    fun sshCompletionRecordsSuccessAndFailureFeedback() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.updateInputs(enabledInputs())
        runCurrent()
        val suggestion = fixture.singleSuggestion()

        fixture.viewModel.run(suggestion)
        runCurrent()
        val firstRequest = fixture.singleExecutionRequest()
        fixture.viewModel.onExecutionAccepted(firstRequest.id)
        fixture.viewModel.onExecutionCompleted(firstRequest.id, succeeded = true)

        fixture.viewModel.run(suggestion)
        runCurrent()
        val secondRequest = fixture.executionRequests().last()
        fixture.viewModel.onExecutionAccepted(secondRequest.id)
        fixture.viewModel.onExecutionCompleted(secondRequest.id, succeeded = false)

        assertEquals(
            listOf(SmartFeedbackType.Success, SmartFeedbackType.Failure),
            fixture.learningStore.records.map { it.type },
        )
        assertEquals(listOf(true, false), fixture.learningStore.records.map { it.success })
        fixture.stop()
    }

    @Test
    fun localCompletionRecordsSuccessAndFailureFeedback() = runTest(dispatcher) {
        val fixture = fixture(action = LOCAL_ACTION)
        fixture.viewModel.updateInputs(enabledInputs(action = LOCAL_ACTION))
        runCurrent()
        val suggestion = fixture.singleSuggestion()

        fixture.viewModel.run(suggestion)
        runCurrent()
        val firstRequest = fixture.singleExecutionRequest()
        fixture.viewModel.onExecutionAccepted(firstRequest.id)
        fixture.viewModel.onLocalSuggestionResult(firstRequest.id, LocalActionResult.Succeeded)

        fixture.viewModel.run(suggestion)
        runCurrent()
        val secondRequest = fixture.executionRequests().last()
        fixture.viewModel.onExecutionAccepted(secondRequest.id)
        fixture.viewModel.onLocalSuggestionResult(secondRequest.id, LocalActionResult.Failed("failed"))

        assertEquals(
            listOf(SmartFeedbackType.Success, SmartFeedbackType.Failure),
            fixture.learningStore.records.map { it.type },
        )
        assertEquals(listOf(true, false), fixture.learningStore.records.map { it.success })
        fixture.stop()
    }

    @Test
    fun busyRejectionClearsRequestWithoutPendingOrFeedback() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.updateInputs(enabledInputs())
        runCurrent()

        fixture.viewModel.run(fixture.singleSuggestion())
        runCurrent()
        val request = fixture.singleExecutionRequest()

        fixture.viewModel.onExecutionRejected(request.id)
        fixture.viewModel.onExecutionCompleted(request.id, succeeded = true)

        assertEquals(false, fixture.viewModel.runPending.value)
        assertTrue(fixture.learningStore.records.isEmpty())
        fixture.stop()
    }

    @Test
    fun doubleClickSameOrDifferentSuggestionEmitsOnlyOneRequest() = runTest(dispatcher) {
        val secondAction = ACTION.copy(id = "second", label = "Second")
        val fixture = fixture()
        fixture.viewModel.updateInputs(
            enabledInputs().copy(allActions = listOf(ACTION, secondAction)),
        )
        runCurrent()
        val first = fixture.viewModel.suggestions.value.first()
        val second = fixture.viewModel.suggestions.value.last()

        fixture.viewModel.run(first)
        fixture.viewModel.run(first)
        fixture.viewModel.run(second)
        runCurrent()

        assertEquals(listOf(first), fixture.executionRequests().map(SmartRunRequest::suggestion))
        assertTrue(fixture.viewModel.runPending.value)
        fixture.stop()
    }

    @Test
    fun sshCompletionUsesRunTimeContextAfterContextSwitch() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_A))
        runCurrent()
        fixture.viewModel.run(fixture.singleSuggestion())
        runCurrent()
        val request = fixture.singleExecutionRequest()
        fixture.viewModel.onExecutionAccepted(request.id)

        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_B))
        runCurrent()
        fixture.viewModel.onExecutionCompleted(request.id, succeeded = true)

        assertEquals(APP_A, fixture.learningStore.records.single().appKey)
        fixture.stop()
    }

    @Test
    fun localCompletionUsesRunTimeContextAfterContextSwitch() = runTest(dispatcher) {
        val fixture = fixture(action = LOCAL_ACTION)
        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_A, action = LOCAL_ACTION))
        runCurrent()
        fixture.viewModel.run(fixture.singleSuggestion())
        runCurrent()
        val request = fixture.singleExecutionRequest()
        fixture.viewModel.onExecutionAccepted(request.id)

        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_B, action = LOCAL_ACTION))
        runCurrent()
        fixture.viewModel.onLocalSuggestionResult(request.id, LocalActionResult.Navigated)

        assertEquals(APP_A, fixture.learningStore.records.single().appKey)
        fixture.stop()
    }

    @Test
    fun completionClearsOnlyExactAcceptedRun() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.updateInputs(enabledInputs())
        runCurrent()
        fixture.viewModel.run(fixture.singleSuggestion())
        runCurrent()
        val request = fixture.singleExecutionRequest()
        fixture.viewModel.onExecutionAccepted(request.id)

        fixture.viewModel.onExecutionCompleted(SmartRunId(request.id.value + 1), succeeded = true)
        assertTrue(fixture.viewModel.runPending.value)
        assertTrue(fixture.learningStore.records.isEmpty())

        fixture.viewModel.onExecutionCompleted(request.id, succeeded = true)
        assertEquals(false, fixture.viewModel.runPending.value)
        assertEquals(1, fixture.learningStore.records.size)
        fixture.stop()
    }

    @Test
    fun clearHistoryClearsPersistedAndSessionSuppression() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.updateInputs(enabledInputs(activeApp = APP_A))
        runCurrent()
        val suggestion = fixture.singleSuggestion()
        fixture.viewModel.hide(suggestion)
        fixture.viewModel.never(suggestion)
        runCurrent()
        assertTrue(fixture.viewModel.suggestions.value.isEmpty())

        fixture.viewModel.clearHistory()
        runCurrent()

        assertEquals(1, fixture.learningStore.clearCount)
        assertTrue(fixture.learningStore.records.isEmpty())
        assertEquals(listOf(ACTION_ID), fixture.viewModel.suggestions.value.map { it.action.id })
        fixture.stop()
    }

    private fun TestScope.fixture(
        nowMillis: Long = 1_000L,
        action: DeckAction = ACTION,
    ): Fixture {
        val contextSource = FakeContextSource()
        val learningStore = FakeLearningStore()
        val engine = FakeSmartEngine()
        val effects = mutableListOf<SmartDeckEffect>()
        val viewModel = SmartDeckViewModel(
            SmartDeckDependencies(
                contextSource = contextSource,
                learningStore = learningStore,
                engine = engine,
                nowMillis = { nowMillis },
            ),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.toList(effects)
        }
        return Fixture(viewModel, contextSource, learningStore, engine, action.id, effects)
    }

    private fun enabledInputs(
        activeApp: SmartAppKey? = APP_A,
        action: DeckAction = ACTION,
    ) = SmartDeckInputs(
        smartDeckEnabled = true,
        onHomeRoute = true,
        currentSurface = SmartSurface.Deck,
        activeMacApp = activeApp,
        allActions = listOf(action),
    )

    private data class Fixture(
        val viewModel: SmartDeckViewModel,
        val contextSource: FakeContextSource,
        val learningStore: FakeLearningStore,
        val engine: FakeSmartEngine,
        val actionId: String,
        val effects: MutableList<SmartDeckEffect>,
    ) {
        fun singleSuggestion(): SmartDeckSuggestionUi = viewModel.suggestions.value.single()
        fun executionRequests(): List<SmartRunRequest> =
            effects.filterIsInstance<SmartDeckEffect.Execute>().map(SmartDeckEffect.Execute::request)
        fun singleExecutionRequest(): SmartRunRequest = executionRequests().single()

        fun stop() {
            viewModel.updateInputs(SmartDeckInputs())
        }
    }

    private class FakeContextSource : SmartDeckContextSource {
        var callCount = 0
        val requestedTimes = mutableListOf<Long>()

        override fun current(inputs: SmartDeckInputs, nowMillis: Long): SmartContext {
            callCount += 1
            requestedTimes += nowMillis
            return SmartContext(
                currentSurface = inputs.currentSurface,
                selectedMacId = inputs.selectedMacId,
                macConnected = inputs.connectionReady,
                macInputConnected = inputs.macInputConnected,
                activeMacApp = inputs.activeMacApp,
                recentActionIds = inputs.recentActionIds,
                supportedCapabilities = emptySet(),
                hourBucket = 0,
                createdAtMillis = nowMillis,
                expiresAtMillis = nowMillis + 300_000L,
                phoneContext = SmartPhoneContext.Phone,
            )
        }
    }

    private class FakeLearningStore : SmartDeckLearningStore {
        val records = mutableListOf<SmartFeedback>()
        var clearCount = 0

        override fun record(feedback: SmartFeedback) {
            records += feedback
        }

        override fun summary(nowMillis: Long): SmartFeedbackSummary = SmartFeedbackSummary(
            suppressedContextActionKeys = records
                .filter { it.type == SmartFeedbackType.SuppressHere }
                .mapNotNull { feedback ->
                    feedback.actionId?.let { actionId ->
                        smartCandidateId(feedback.surface, feedback.appKey, actionId)
                    }
                }
                .toSet(),
            globallySuppressedActionIds = records
                .filter { it.type == SmartFeedbackType.NeverGlobal }
                .mapNotNull(SmartFeedback::actionId)
                .toSet(),
        )

        override fun clear() {
            clearCount += 1
            records.clear()
        }
    }

    private class FakeSmartEngine : SmartEngine {
        var callCount = 0

        override fun suggest(
            context: SmartContext,
            actions: List<SmartActionRef>,
            feedback: SmartFeedbackSummary,
            nowMillis: Long,
        ): SmartDecision {
            callCount += 1
            val candidates = actions.mapNotNull { action ->
                val candidateId = smartCandidateId(context.currentSurface, context.activeMacApp, action.id)
                if (
                    candidateId in feedback.suppressedContextActionKeys ||
                    action.id in feedback.globallySuppressedActionIds
                ) {
                    return@mapNotNull null
                }
                SmartCandidate(
                    id = candidateId,
                    actionId = action.id,
                    title = action.title,
                    summary = action.description,
                    reason = "Deterministic test reason",
                    confidenceLabel = SmartConfidenceLabel.Likely,
                    capabilities = emptySet(),
                    risks = setOf(SmartRisk.Normal),
                    contextKeys = context.sanitizedKeys(),
                    expiresAtMillis = nowMillis + 300_000L,
                )
            }
            return SmartDecision(candidates)
        }
    }

    private companion object {
        val APP_A = SmartAppKey("app-a")
        val APP_B = SmartAppKey("app-b")
        const val ACTION_ID = "open_finder"
        val ACTION = DeckAction(
            id = ACTION_ID,
            label = "Open Finder",
            kind = ActionKind.Ssh,
            icon = ActionIcon.Finder,
            command = "open -a Finder",
        )
        val LOCAL_ACTION = DeckAction(
            id = "open_settings",
            label = "Open Settings",
            kind = ActionKind.Local,
            icon = ActionIcon.Control,
            route = "settings",
        )
    }
}
