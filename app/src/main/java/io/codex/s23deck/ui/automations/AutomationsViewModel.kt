package io.codex.s23deck.ui.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codex.s23deck.core.actions.AiGeneratedContentPlanner
import io.codex.s23deck.core.actions.ActionResult
import io.codex.s23deck.core.actions.ActionResultStatus
import io.codex.s23deck.core.actions.ActionRunner
import io.codex.s23deck.core.actions.ActionSpec
import io.codex.s23deck.core.actions.ShellTrustLevel
import io.codex.s23deck.data.ConnectionRepository
import io.codex.s23deck.core.actions.RawCommandPolicy
import io.codex.s23deck.data.automation.AutomationRepository
import io.codex.s23deck.data.automation.AutomationScheduler
import io.codex.s23deck.domain.ai.AiArtifact
import io.codex.s23deck.domain.ai.GeneratedDraft
import io.codex.s23deck.domain.automation.AutomationCatalog
import io.codex.s23deck.domain.automation.AutomationGroup
import io.codex.s23deck.domain.automation.AutomationRecipe
import io.codex.s23deck.domain.automation.AutomationRunSummary
import io.codex.s23deck.domain.automation.AutomationSafety
import io.codex.s23deck.domain.automation.AutomationTrigger
import io.codex.s23deck.domain.automation.AutomationTriggerEngine
import io.codex.s23deck.domain.automation.hasCurrentSuccessfulTest
import io.codex.s23deck.domain.automation.label
import io.codex.s23deck.domain.automation.revisionFingerprint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AutomationsViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val connectionRepository: ConnectionRepository,
    private val actionRunner: ActionRunner,
    private val triggerEngine: AutomationTriggerEngine,
    private val aiGeneratedContentPlanner: AiGeneratedContentPlanner = AiGeneratedContentPlanner(),
    private val automationScheduler: AutomationScheduler = NoopAutomationScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AutomationsUiState())
    val uiState: StateFlow<AutomationsUiState> = _uiState.asStateFlow()
    private var recipes: List<AutomationRecipe> = emptyList()
    private var triggerCheckJob: Job? = null
    private var lastDeletedRecipe: AutomationRecipe? = null

    init {
        viewModelScope.launch {
            combine(automationRepository.recipes, connectionRepository.config) { recipes, config ->
                recipes to config.isReady
            }.collect { (nextRecipes, connectionReady) ->
                recipes = nextRecipes
                _uiState.update {
                    it.copy(
                        automations = nextRecipes.map { recipe -> recipe.toUiItem() },
                        connectionReady = connectionReady,
                    )
                }
            }
        }
    }

    fun startTriggerMonitor() {
        automationScheduler.start()
    }

    fun run(recipeId: String) = execute(recipeId, testOnly = false)

    fun test(recipeId: String) = execute(recipeId, testOnly = true)

    fun toggle(recipeId: String, enabled: Boolean) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        if (enabled && !recipe.hasCurrentSuccessfulTest()) {
            _uiState.update { it.copy(message = "Validate this automation successfully before enabling it") }
            return
        }
        viewModelScope.launch {
            automationRepository.save(recipe.copy(enabled = enabled))
        }
    }

    fun approveAndRun(recipeId: String) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        if (!_uiState.value.connectionReady) {
            _uiState.update { it.copy(message = "Connect your Mac first") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(runningActionId = recipeId, message = "Approved: ${recipe.title}") }
            val result = runRecipe(recipe, testOnly = false, allowDangerous = true)
            automationRepository.recordRun(recipeId, result)
            _uiState.update { it.copy(runningActionId = null, message = result.message) }
        }
    }

    fun duplicate(recipeId: String) {
        viewModelScope.launch { automationRepository.duplicate(recipeId) }
    }

    fun delete(recipeId: String) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        lastDeletedRecipe = recipe
        viewModelScope.launch {
            automationRepository.delete(recipeId)
            _uiState.update {
                it.copy(
                    message = "Deleted ${recipe.title}",
                    pendingUndo = PendingAutomationUndo(recipe.id, recipe.title),
                )
            }
        }
    }

    fun undoDelete() {
        val undo = _uiState.value.pendingUndo ?: return
        val deletedRecipe = lastDeletedRecipe
        viewModelScope.launch {
            if (deletedRecipe?.id == undo.recipeId) {
                automationRepository.save(deletedRecipe)
                _uiState.update { it.copy(message = "Restored ${deletedRecipe.title}", pendingUndo = null) }
            } else {
                _uiState.update { it.copy(message = "Could not restore ${undo.title}", pendingUndo = null) }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun resetDefaults() {
        viewModelScope.launch { automationRepository.resetDefaults() }
    }

    fun create(input: AutomationDraftInput) {
        val title = input.title.trim()
        val command = input.command.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(message = "Name the automation first") }
            return
        }
        if (command.isBlank()) {
            _uiState.update { it.copy(message = "Add a command to run") }
            return
        }
        val trigger = input.toTrigger().getOrElse { error ->
            _uiState.update { it.copy(message = error.message ?: "Check the trigger value") }
            return
        }
        val recipe = AutomationRecipe(
            id = input.recipeId ?: "custom_${title.slug()}_${System.currentTimeMillis()}",
            title = title,
            description = trigger.label(),
            enabled = input.enabled,
            trigger = trigger,
            steps = listOf(
                ActionSpec.ShellCommand(
                    id = "step_${title.slug()}",
                    title = title,
                    command = command,
                    dangerous = RawCommandPolicy.firstViolation(command) != null,
                ),
            ),
            safety = AutomationSafety(requiresConfirmation = RawCommandPolicy.firstViolation(command) != null),
        )
        viewModelScope.launch {
            automationRepository.save(recipe)
            _uiState.update { it.copy(message = "${recipe.title} saved") }
        }
    }

    fun checkTriggersNow(auto: Boolean = false) {
        if (!auto && !_uiState.value.connectionReady) {
            _uiState.update { it.copy(message = "Connect your Mac first") }
            return
        }
        if (!_uiState.value.connectionReady) return
        if (triggerCheckJob?.isActive == true) {
            if (!auto) _uiState.update { it.copy(message = "Trigger check already running") }
            return
        }
        triggerCheckJob = viewModelScope.launch {
            try {
                val evaluation = triggerEngine.evaluate(recipes)
                _uiState.update {
                    it.copy(
                        triggerMonitorLabel = evaluation.message,
                        message = if (auto || evaluation.dueRecipes.isEmpty()) it.message else evaluation.message,
                    )
                }
                evaluation.dueRecipes.forEach { recipe ->
                    executeTriggered(recipe)
                }
            } finally {
                triggerCheckJob = null
            }
        }
    }

    fun saveGeneratedDraft(draft: GeneratedDraft): Boolean {
        val recipe = aiGeneratedContentPlanner.automationRecipeFromDraft(draft).getOrElse { error ->
            _uiState.update { it.copy(message = error.message ?: "Automation draft cannot be saved") }
            return true
        } ?: return false
        viewModelScope.launch {
            automationRepository.save(recipe)
            _uiState.update { it.copy(message = "${recipe.title} saved") }
        }
        return true
    }

    fun saveArtifact(artifact: AiArtifact): Boolean {
        val recipe = aiGeneratedContentPlanner.automationRecipeFromArtifact(artifact).getOrElse { error ->
            _uiState.update { it.copy(message = error.message ?: "Automation artifact cannot be saved") }
            return true
        } ?: return false
        viewModelScope.launch {
            automationRepository.save(recipe)
            _uiState.update { it.copy(message = "${recipe.title} saved") }
        }
        return true
    }

    fun edit(input: AutomationDraftInput) {
        if (input.recipeId == null) return
        create(input)
    }

    private fun execute(recipeId: String, testOnly: Boolean) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        if (!recipe.enabled && !testOnly) {
            _uiState.update { it.copy(message = "${recipe.title} is disabled") }
            return
        }
        if (!_uiState.value.connectionReady) {
            _uiState.update { it.copy(message = "Connect your Mac first") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(runningActionId = recipeId, message = null) }
            val result = if (!testOnly && recipe.safety.requiresConfirmation) {
                ActionResult(
                    actionId = recipe.id,
                    title = recipe.title,
                    status = ActionResultStatus.RequiresConfirmation,
                    message = "${recipe.title} is waiting for approval",
                )
            } else {
                runRecipe(recipe, testOnly, allowDangerous = false)
            }
            if (testOnly) {
                automationRepository.recordTest(recipeId, result, recipe.revisionFingerprint())
            } else {
                automationRepository.recordRun(recipeId, result)
            }
            _uiState.update {
                it.copy(
                    runningActionId = null,
                    message = if (testOnly) "Test: Validation ${result.message}" else result.message,
                )
            }
        }
    }

    private suspend fun executeTriggered(recipe: AutomationRecipe) {
        _uiState.update { it.copy(runningActionId = recipe.id, message = "Trigger matched: ${recipe.title}") }
        if (recipe.safety.requiresConfirmation || recipe.steps.any { it.dangerous }) {
            val result = ActionResult(
                actionId = recipe.id,
                title = recipe.title,
                status = ActionResultStatus.RequiresConfirmation,
                message = "Trigger matched, but ${recipe.title} needs manual confirmation",
            )
            automationRepository.recordRun(recipe.id, result)
            _uiState.update {
                it.copy(
                    runningActionId = null,
                    message = "Trigger needs confirmation: ${recipe.title}",
                )
            }
            return
        }
        val result = runRecipe(recipe, testOnly = false, allowDangerous = false)
        automationRepository.recordRun(recipe.id, result)
        _uiState.update {
            it.copy(
                runningActionId = null,
                message = "Trigger: ${result.message}",
            )
        }
    }

    private suspend fun runRecipe(
        recipe: AutomationRecipe,
        testOnly: Boolean,
        allowDangerous: Boolean,
    ): ActionResult {
        if (testOnly) return dryRunRecipe(recipe)
        var last = ActionResult(
            actionId = recipe.id,
            title = recipe.title,
            status = ActionResultStatus.Succeeded,
            message = "Automation completed",
        )
        recipe.steps.forEach { step ->
            val result = actionRunner.run(step, allowDangerous = allowDangerous)
            last = result
            if (!result.succeeded) return result
        }
        return last.copy(
            actionId = recipe.id,
            title = recipe.title,
            message = last.message,
        )
    }

    private fun dryRunRecipe(recipe: AutomationRecipe): ActionResult {
        if (recipe.steps.isEmpty()) {
            return ActionResult(
                actionId = recipe.id,
                title = recipe.title,
                status = ActionResultStatus.Failed,
                message = "Recipe has no actions",
            )
        }
        val blocked = recipe.steps.firstNotNullOfOrNull { step ->
            step.validationError()?.let { error -> step.title to error }
        }
        if (blocked != null) {
            return ActionResult(
                actionId = recipe.id,
                title = recipe.title,
                status = ActionResultStatus.Failed,
                message = "${blocked.first}: ${blocked.second}",
            )
        }
        val dangerousCount = recipe.steps.count { it.dangerous } + if (recipe.safety.requiresConfirmation) 1 else 0
        val suffix = if (dangerousCount > 0) " Confirmation will be required to run." else ""
        return ActionResult(
            actionId = recipe.id,
            title = recipe.title,
            status = ActionResultStatus.Succeeded,
            message = "${recipe.title} dry run passed. ${recipe.steps.size} action(s) checked. No Mac command was executed.$suffix",
        )
    }
}

private object NoopAutomationScheduler : AutomationScheduler {
    override fun start() = Unit
}

private fun ActionSpec.validationError(): String? = when (this) {
    is ActionSpec.ShellCommand -> {
        when {
            command.isBlank() -> "Command is empty"
            trustLevel == ShellTrustLevel.Generated ->
                RawCommandPolicy.firstAllowlistViolation(command)?.let { "Needs manual review: $it" }
            else -> RawCommandPolicy.firstViolation(command)?.let { "Blocked command: $it" }
        }
    }
    is ActionSpec.CatalogAction -> if (id.isBlank()) "Action id is empty" else null
    is ActionSpec.DeckActionSpec -> when {
        id.isBlank() -> "Action id is empty"
        action.kind == io.codex.s23deck.domain.ActionKind.Ssh && action.command.isNullOrBlank() -> null
        else -> null
    }
    is ActionSpec.LocalRoute -> if (route.isBlank()) "Route is empty" else null
}

private fun AutomationRecipe.toUiItem(): AutomationItem {
    val lastRunSucceeded = lastRun?.status?.let { it == ActionResultStatus.Succeeded }
    val currentTest = lastTest.takeIf { lastTestRevision == revisionFingerprint() }
    val lastTestSucceeded = currentTest?.status?.let { it == ActionResultStatus.Succeeded }
    return AutomationItem(
        id = id,
        label = title,
        description = description,
        category = AutomationCatalog.groupFor(id).toUiCategory(),
        triggerLabel = trigger.label(),
        draftTriggerType = trigger.toDraftType(),
        draftTriggerValue = trigger.toDraftValue(),
        draftWeekdays = (trigger as? AutomationTrigger.TimeOfDay)?.days.orEmpty(),
        draftCommand = steps.firstNotNullOfOrNull { (it as? ActionSpec.ShellCommand)?.command }.orEmpty(),
        dangerous = safety.requiresConfirmation,
        enabled = enabled,
        lastRunLabel = lastRun?.toLabel(),
        lastRunSucceeded = lastRunSucceeded,
        lastTestLabel = currentTest?.toTestLabel(),
        lastTestSucceeded = lastTestSucceeded,
        canEnable = hasCurrentSuccessfulTest(),
        approvalPending = pendingApproval != null,
        runHistory = runHistory.map { it.toHistoryItem() },
    )
}

private fun AutomationRunSummary.toLabel(): String =
    when (status) {
        ActionResultStatus.Succeeded -> "Last run OK"
        ActionResultStatus.Failed -> "Last run failed"
        ActionResultStatus.RequiresConfirmation -> "Needs confirmation"
    }

private fun AutomationRunSummary.toTestLabel(): String =
    when (status) {
        ActionResultStatus.Succeeded -> "Validation passed"
        ActionResultStatus.Failed -> "Validation failed: ${message.take(80)}"
        ActionResultStatus.RequiresConfirmation -> "Validation needs review"
    }

private fun AutomationRunSummary.toHistoryItem(): AutomationHistoryItem =
    AutomationHistoryItem(
        timestampMillis = timestampMillis,
        statusLabel = toLabel(),
        message = message,
        logs = logs,
        succeeded = status == ActionResultStatus.Succeeded,
        needsApproval = status == ActionResultStatus.RequiresConfirmation,
    )

private fun AutomationTrigger.toDraftType(): AutomationTriggerDraftType = when (this) {
    AutomationTrigger.Manual -> AutomationTriggerDraftType.Manual
    is AutomationTrigger.TimeOfDay -> AutomationTriggerDraftType.TimeOfDay
    is AutomationTrigger.ActiveApp -> AutomationTriggerDraftType.ActiveApp
    is AutomationTrigger.ClipboardContains -> AutomationTriggerDraftType.ClipboardContains
    is AutomationTrigger.WifiSsid -> AutomationTriggerDraftType.WifiSsid
    AutomationTrigger.MacAwake -> AutomationTriggerDraftType.MacAwake
    is AutomationTrigger.FileChanged -> AutomationTriggerDraftType.FileChanged
    is AutomationTrigger.BatteryBelow -> AutomationTriggerDraftType.BatteryBelow
    is AutomationTrigger.AiSuggested -> AutomationTriggerDraftType.Manual
}

private fun AutomationTrigger.toDraftValue(): String = when (this) {
    AutomationTrigger.Manual,
    AutomationTrigger.MacAwake -> ""
    is AutomationTrigger.TimeOfDay -> "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    is AutomationTrigger.ActiveApp -> appName
    is AutomationTrigger.ClipboardContains -> text
    is AutomationTrigger.WifiSsid -> ssid
    is AutomationTrigger.FileChanged -> path
    is AutomationTrigger.BatteryBelow -> percent.coerceIn(1, 100).toString()
    is AutomationTrigger.AiSuggested -> prompt
}

private fun AutomationGroup.toUiCategory(): AutomationCategory = when (this) {
    AutomationGroup.Routines -> AutomationCategory.Routines
    AutomationGroup.Workspace -> AutomationCategory.Workspace
    AutomationGroup.Browser -> AutomationCategory.Browser
    AutomationGroup.Media -> AutomationCategory.Media
    AutomationGroup.System -> AutomationCategory.System
}

private fun AutomationDraftInput.toTrigger(): Result<AutomationTrigger> = runCatching {
    val value = triggerValue.trim()
    when (triggerType) {
        AutomationTriggerDraftType.Manual -> AutomationTrigger.Manual
        AutomationTriggerDraftType.TimeOfDay -> {
            val parts = value.ifBlank { "09:00" }.split(":")
            require(parts.size == 2) { "Use HH:mm time" }
            AutomationTrigger.TimeOfDay(
                hour = parts[0].toInt().coerceIn(0, 23),
                minute = parts[1].toInt().coerceIn(0, 59),
                days = weekdays,
            )
        }
        AutomationTriggerDraftType.ActiveApp -> AutomationTrigger.ActiveApp(value.ifBlank { "Safari" })
        AutomationTriggerDraftType.ClipboardContains -> AutomationTrigger.ClipboardContains(
            value.ifBlank { error("Enter clipboard text to match") },
        )
        AutomationTriggerDraftType.WifiSsid -> AutomationTrigger.WifiSsid(value.ifBlank { error("Enter Wi-Fi name") })
        AutomationTriggerDraftType.MacAwake -> AutomationTrigger.MacAwake
        AutomationTriggerDraftType.FileChanged -> AutomationTrigger.FileChanged(value.ifBlank { "~/Downloads" })
        AutomationTriggerDraftType.BatteryBelow -> AutomationTrigger.BatteryBelow(
            value.toIntOrNull()?.coerceIn(1, 100) ?: 20,
        )
    }
}

private fun String.slug(): String =
    lowercase()
        .map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "automation" }
