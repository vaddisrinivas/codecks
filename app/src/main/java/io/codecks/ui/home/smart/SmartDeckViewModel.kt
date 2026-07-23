package io.codecks.ui.home.smart

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.domain.ActionStatus
import io.codecks.domain.DeckAction
import io.codecks.domain.isRunnableFromSmartSuggestion
import io.codecks.domain.LocalActionResult
import io.codecks.domain.smart.DeterministicSmartEngine
import io.codecks.domain.smart.SmartCandidate
import io.codecks.domain.smart.SmartContext
import io.codecks.domain.smart.SmartFeedback
import io.codecks.domain.smart.SmartFeedbackSummary
import io.codecks.domain.smart.SmartFeedbackType
import io.codecks.domain.smart.SmartEngine
import io.codecks.domain.smart.smartCandidateId
import io.codecks.domain.smart.providers.ActiveAppCandidateProvider
import io.codecks.domain.smart.providers.ConnectionRepairCandidateProvider
import io.codecks.domain.smart.providers.RecentActionCandidateProvider
import io.codecks.domain.smart.providers.TransitionCandidateProvider
import io.codecks.data.smart.SmartContextRepository
import io.codecks.data.smart.SmartLearningStore
import io.codecks.data.smart.SmartAppActionMappings
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

internal fun interface SmartDeckContextSource {
    fun current(inputs: SmartDeckInputs, nowMillis: Long): SmartContext
}

internal interface SmartDeckLearningStore {
    fun record(feedback: SmartFeedback)
    fun summary(nowMillis: Long): SmartFeedbackSummary
    fun clear()
}

class SmartDeckDependencies internal constructor(
    internal val contextSource: SmartDeckContextSource,
    internal val learningStore: SmartDeckLearningStore,
    internal val engine: SmartEngine,
    internal val nowMillis: () -> Long,
) {
    @Inject
    constructor(
        @ApplicationContext appContext: Context,
    ) : this(
        contextSource = RepositorySmartDeckContextSource(SmartContextRepository(appContext)),
        learningStore = PersistedSmartDeckLearningStore(SmartLearningStore(appContext)),
        engine = DeterministicSmartEngine(
            providers = listOf(
                RecentActionCandidateProvider(),
                TransitionCandidateProvider(),
                ActiveAppCandidateProvider(
                    mappings = SmartAppActionMappings.load(appContext),
                ),
                ConnectionRepairCandidateProvider(),
            ),
        ),
        nowMillis = System::currentTimeMillis,
    )
}

private class RepositorySmartDeckContextSource(
    private val repository: SmartContextRepository,
) : SmartDeckContextSource {
    override fun current(inputs: SmartDeckInputs, nowMillis: Long): SmartContext =
        repository.current(
            currentSurface = inputs.currentSurface,
            selectedMacId = inputs.selectedMacId,
            macConnected = inputs.connectionReady,
            macInputConnected = inputs.macInputConnected,
            activeMacApp = inputs.activeMacApp,
            recentActionIds = inputs.recentActionIds,
            nowMillis = nowMillis,
        )
}

private class PersistedSmartDeckLearningStore(
    private val store: SmartLearningStore,
) : SmartDeckLearningStore {
    override fun record(feedback: SmartFeedback) = store.record(feedback)
    override fun summary(nowMillis: Long): SmartFeedbackSummary = store.summary(nowMillis)
    override fun clear() = store.clear()
}

@HiltViewModel
class SmartDeckViewModel @Inject constructor(
    dependencies: SmartDeckDependencies,
) : ViewModel() {

    private val contextSource = dependencies.contextSource
    private val learningStore = dependencies.learningStore
    private val engine = dependencies.engine
    private val nowMillis = dependencies.nowMillis

    private val _inputs = MutableStateFlow(SmartDeckInputs())
    private val _suggestions = MutableStateFlow<List<SmartDeckSuggestionUi>>(emptyList())
    private val _smartContext = MutableStateFlow<SmartContext?>(null)
    private val _effects = MutableSharedFlow<SmartDeckEffect>(extraBufferCapacity = 16)
    private val _pendingDangerousSuggestion = MutableStateFlow<SmartDeckSuggestionUi?>(null)

    private val hiddenCandidateIds = MutableStateFlow<Set<String>>(emptySet())
    private val suppressedContextSmartActionIds = MutableStateFlow<Set<String>>(emptySet())
    private val globallySuppressedActionIds = MutableStateFlow<Set<String>>(emptySet())
    private val _runPending = MutableStateFlow(false)
    private var requestedSmartRun: SmartRunRequest? = null
    private var acceptedSmartRun: SmartRunRequest? = null
    private var nextSmartRunId = 1L

    private val refreshTick = MutableStateFlow(0)
    private var refreshLoop: Job? = null
    private var lastContextSignature: String? = null

    val suggestions: StateFlow<List<SmartDeckSuggestionUi>> = _suggestions.asStateFlow()
    val smartContext: StateFlow<SmartContext?> = _smartContext.asStateFlow()
    val effects: SharedFlow<SmartDeckEffect> = _effects.asSharedFlow()
    val pendingDangerousSuggestion: StateFlow<SmartDeckSuggestionUi?> = _pendingDangerousSuggestion.asStateFlow()
    val runPending: StateFlow<Boolean> = _runPending.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                _inputs,
                refreshTick,
                hiddenCandidateIds,
                suppressedContextSmartActionIds,
                globallySuppressedActionIds,
            ) { inputs, _, hidden, suppressedForContext, suppressedGlobal ->
                buildSmartSuggestions(
                    inputs = inputs,
                    hiddenCandidateIds = hidden,
                    suppressedContextActionIds = suppressedForContext,
                    suppressedGlobalActionIds = suppressedGlobal,
                )
            }.collect { candidates ->
                _suggestions.value = candidates
            }
        }
    }

    fun updateInputs(inputs: SmartDeckInputs) {
        if (!inputs.isEnabledAndVisible) {
            hiddenCandidateIds.value = emptySet()
            suppressedContextSmartActionIds.value = emptySet()
            if (!inputs.smartDeckEnabled) {
                lastContextSignature = null
            }
            _smartContext.value = null
            _pendingDangerousSuggestion.value = null
            stopRefreshLoop()
        } else {
            ensureRefreshLoop()
        }
        _inputs.value = inputs
    }

    private fun ensureRefreshLoop() {
        if (refreshLoop?.isActive == true) return
        refreshLoop = viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                if (!_inputs.value.isEnabledAndVisible) break
                refreshTick.update { it + 1 }
            }
        }
    }

    private fun stopRefreshLoop() {
        refreshLoop?.cancel()
        refreshLoop = null
    }

    fun onRunSuggestion(suggestion: SmartDeckSuggestionUi) {
        if (suggestion.action.dangerous) {
            _pendingDangerousSuggestion.value = suggestion
            emitEffect(SmartDeckEffect.ConfirmDangerousSuggestion(suggestion))
            return
        }
        emitRunEffect(suggestion, allowDangerous = false)
    }

    fun run(suggestion: SmartDeckSuggestionUi) {
        onRunSuggestion(suggestion)
    }

    fun confirmDangerousSuggestion() {
        val suggestion = _pendingDangerousSuggestion.value ?: return
        _pendingDangerousSuggestion.value = null
        emitRunEffect(suggestion, allowDangerous = true)
    }

    fun cancelDangerousSuggestion() {
        _pendingDangerousSuggestion.value = null
    }

    fun onPinSuggestion(suggestion: SmartDeckSuggestionUi) {
        recordSmartFeedback(suggestion, SmartFeedbackType.Pin)
        emitEffect(SmartDeckEffect.Pin(suggestion))
    }

    fun pin(suggestion: SmartDeckSuggestionUi) {
        onPinSuggestion(suggestion)
    }

    fun onHideSuggestion(suggestion: SmartDeckSuggestionUi) {
        hiddenCandidateIds.update { it + suggestion.candidateId }
    }

    fun hide(suggestion: SmartDeckSuggestionUi) {
        onHideSuggestion(suggestion)
    }

    fun onExplainSuggestion(suggestion: SmartDeckSuggestionUi) {
        recordSmartFeedback(suggestion, SmartFeedbackType.Why)
        emitEffect(SmartDeckEffect.ShowExplanation(suggestion.confidence, suggestion.reason))
    }

    fun explain(suggestion: SmartDeckSuggestionUi) {
        onExplainSuggestion(suggestion)
    }

    fun onSuppressSuggestionForContext(suggestion: SmartDeckSuggestionUi) {
        val context = _smartContext.value ?: return
        val candidateId = smartCandidateId(context.currentSurface, context.activeMacApp, suggestion.action.id)
        suppressedContextSmartActionIds.update { it + candidateId }
        recordSmartFeedback(suggestion, SmartFeedbackType.SuppressHere)
    }

    fun suppressHere(suggestion: SmartDeckSuggestionUi) {
        onSuppressSuggestionForContext(suggestion)
    }

    fun onNeverSuggestion(suggestion: SmartDeckSuggestionUi) {
        globallySuppressedActionIds.update { it + suggestion.action.id }
        recordSmartFeedback(suggestion, SmartFeedbackType.NeverGlobal)
    }

    fun never(suggestion: SmartDeckSuggestionUi) {
        onNeverSuggestion(suggestion)
    }

    fun onExecutionAccepted(runId: SmartRunId) {
        val request = requestedSmartRun?.takeIf { it.id == runId } ?: return
        requestedSmartRun = null
        acceptedSmartRun = request
    }

    fun onExecutionRejected(runId: SmartRunId) {
        if (requestedSmartRun?.id != runId) return
        requestedSmartRun = null
        _runPending.value = false
    }

    fun onExecutionCompleted(runId: SmartRunId, succeeded: Boolean) {
        val request = acceptedSmartRun?.takeIf { it.id == runId } ?: return
        acceptedSmartRun = null
        _runPending.value = false
        recordSmartFeedback(
            suggestion = request.suggestion,
            type = if (succeeded) SmartFeedbackType.Success else SmartFeedbackType.Failure,
            success = succeeded,
            context = request.context,
        )
    }

    fun onLocalSuggestionResult(runId: SmartRunId, result: LocalActionResult) {
        onExecutionCompleted(
            runId = runId,
            succeeded = result == LocalActionResult.Succeeded || result == LocalActionResult.Navigated,
        )
    }

    fun onHomeActionStatusChanged(status: ActionStatus) {
        when (status) {
            is ActionStatus.Succeeded -> acceptedSmartRun
                ?.takeIf { it.suggestion.action.id == status.actionId }
                ?.let { onExecutionCompleted(it.id, succeeded = true) }
            is ActionStatus.Failed -> acceptedSmartRun
                ?.takeIf { it.suggestion.action.id == status.actionId }
                ?.let { onExecutionCompleted(it.id, succeeded = false) }
            else -> Unit
        }
    }

    fun clearSmartHistory() {
        learningStore.clear()
        hiddenCandidateIds.value = emptySet()
        suppressedContextSmartActionIds.value = emptySet()
        globallySuppressedActionIds.value = emptySet()
        refreshTick.update { it + 1 }
    }

    fun clearHistory() {
        clearSmartHistory()
    }

    private fun emitRunEffect(suggestion: SmartDeckSuggestionUi, allowDangerous: Boolean) {
        if (_runPending.value) return
        val context = _smartContext.value?.immutableSnapshot() ?: return
        val request = SmartRunRequest(
            id = SmartRunId(nextSmartRunId++),
            suggestion = suggestion,
            context = context,
            allowDangerous = allowDangerous,
        )
        requestedSmartRun = request
        _runPending.value = true
        emitEffect(SmartDeckEffect.Execute(request))
    }

    private fun buildSmartSuggestions(
        inputs: SmartDeckInputs,
        hiddenCandidateIds: Set<String>,
        suppressedContextActionIds: Set<String>,
        suppressedGlobalActionIds: Set<String>,
    ): List<SmartDeckSuggestionUi> {
        if (!inputs.isEnabledAndVisible) {
            _smartContext.value = null
            return emptyList()
        }
        val context = runCatching {
            contextSource.current(inputs, nowMillis())
        }.getOrNull() ?: return emptyList()

        val signature = smartContextSignature(context)
        if (signature != lastContextSignature) {
            this.hiddenCandidateIds.value = emptySet()
            suppressedContextSmartActionIds.value = emptySet()
            lastContextSignature = signature
        }
        _smartContext.value = context

        val visibleActionIds = inputs.visibleDeckActions.map(DeckAction::id).toSet()
        val suggestionEligibleActions = inputs.allActions.filter(DeckAction::isRunnableFromSmartSuggestion)
        val actionById = suggestionEligibleActions.associateBy(DeckAction::id)
        val contextFeedback = learningStore.summary(context.createdAtMillis)
        val filteredFeedback = contextFeedback.copy(
            suppressedContextActionKeys = contextFeedback.suppressedContextActionKeys +
                hiddenCandidateIds +
                suppressedContextActionIds,
            globallySuppressedActionIds = contextFeedback.globallySuppressedActionIds + suppressedGlobalActionIds,
        )
        return engine.suggest(
            context = context,
            actions = suggestionEligibleActions.map(DeckAction::toSmartActionRef),
            feedback = filteredFeedback,
            nowMillis = context.createdAtMillis,
        ).candidates.mapNotNull { candidate ->
            val action = candidate.actionId?.let(actionById::get) ?: return@mapNotNull null
            if (action.id in visibleActionIds) return@mapNotNull null
            candidate.toSuggestionUi(action)
        }
    }

    private fun SmartCandidate.toSuggestionUi(action: DeckAction) = SmartDeckSuggestionUi(
        candidateId = id,
        action = action,
        reason = reason,
        confidence = confidenceLabel.productLabel(),
    )

    private fun recordSmartFeedback(
        suggestion: SmartDeckSuggestionUi,
        type: SmartFeedbackType,
        success: Boolean? = null,
        context: SmartContext? = _smartContext.value,
    ) {
        val feedbackContext = context ?: return
        learningStore.record(
            SmartFeedback(
                candidateId = suggestion.candidateId,
                actionId = suggestion.action.id,
                appKey = feedbackContext.activeMacApp,
                surface = feedbackContext.currentSurface,
                macId = feedbackContext.selectedMacId,
                type = type,
                success = success,
                coarseHourBucket = feedbackContext.hourBucket,
                contextKeys = feedbackContext.sanitizedKeys(),
                atMillis = nowMillis(),
            ),
        )
        refreshTick.update { it + 1 }
    }

    private fun emitEffect(effect: SmartDeckEffect) {
        viewModelScope.launch {
            _effects.emit(effect)
        }
    }

    private fun smartContextSignature(context: SmartContext): String = buildString {
        append(context.currentSurface)
        append(":")
        append(context.selectedMacId?.value.orEmpty())
        append(":")
        append(context.activeMacApp?.value.orEmpty())
    }

    private fun SmartContext.immutableSnapshot(): SmartContext = copy(
        recentActionIds = recentActionIds.toList(),
        supportedCapabilities = supportedCapabilities.toSet(),
    )
}
