package io.codecks.ui.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codecks.core.actions.AiGeneratedContentPlanner
import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.RawCommandPolicy
import io.codecks.core.actions.ShellTrustLevel
import io.codecks.core.actions.commandRevision
import io.codecks.data.ConnectionRepository
import io.codecks.data.automation.AutomationRepository
import io.codecks.data.automation.AutomationScheduler
import io.codecks.data.automation.AutomationExecutionCoordinator
import io.codecks.data.automation.AutomationPreparation
import io.codecks.data.automation.AutomationClaimDisposition
import io.codecks.data.automation.claimDisposition
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactAction
import io.codecks.domain.ai.AiArtifactKind
import io.codecks.domain.ai.AutomationDraft
import io.codecks.domain.ai.GeneratedDraft
import io.codecks.domain.automation.AutomationCatalog
import io.codecks.domain.automation.AutomationGroup
import io.codecks.domain.automation.AutomationActionProbeResult
import io.codecks.domain.automation.AutomationLiveTestActionExecutor
import io.codecks.domain.automation.AutomationLiveTestEngine
import io.codecks.domain.automation.AutomationLiveTestReceipt
import io.codecks.domain.automation.AutomationLiveTestTerminalStatus
import io.codecks.domain.automation.AutomationPreflightArea
import io.codecks.domain.automation.AutomationPreflightCheck
import io.codecks.domain.automation.AutomationPreflightReceipt
import io.codecks.domain.automation.AutomationCapabilityCodes
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationRunSummary
import io.codecks.domain.automation.AutomationSafety
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.AutomationTriggerEngine
import io.codecks.domain.automation.automationPermissionProbeCommand
import io.codecks.domain.automation.automationConnectionIdentity as opaqueAutomationConnectionIdentity
import io.codecks.domain.automation.hasCurrentValidPreflight
import io.codecks.domain.automation.label
import io.codecks.domain.automation.revisionFingerprint
import io.codecks.domain.automation.hasCurrentValidLiveTest
import io.codecks.domain.automation.requiredCapabilities
import io.codecks.domain.automation.requiredCommandTools
import io.codecks.domain.automation.requiredPermissions
import io.codecks.domain.automation.automationRequirementCode
import io.codecks.domain.automation.automationRequiredPreflightCheckCodes
import io.codecks.domain.connection.CapabilityStatus
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val executionCoordinator: AutomationExecutionCoordinator =
        AutomationExecutionCoordinator(automationRepository, connectionRepository, actionRunner),
) : ViewModel() {
    private val _uiState = MutableStateFlow(AutomationsUiState())
    val uiState: StateFlow<AutomationsUiState> = _uiState.asStateFlow()
    private var recipes: List<AutomationRecipe> = emptyList()
    private var triggerCheckJob: Job? = null
    private var lastDeletedRecipe: AutomationRecipe? = null
    private var currentConnectionConfig = io.codecks.data.ConnectionConfig()
    private var terminalProofReady = false

    init {
        viewModelScope.launch {
            combine(
                automationRepository.recipes,
                connectionRepository.config,
                automationRepository.recoveryRequired,
            ) { nextRecipes, config, storageRecoveryRequired ->
                currentConnectionConfig = config
                Triple(nextRecipes, config, storageRecoveryRequired)
            }.collect { (nextRecipes, config, storageRecoveryRequired) ->
                recipes = nextRecipes
                _uiState.update {
                    it.copy(
                        automations = nextRecipes.map {
                            recipe -> recipe.toUiItem(config, terminalProofReady = terminalProofReady)
                        },
                        connectionReady = config.isReady && terminalProofReady,
                        storageRecoveryRequired = storageRecoveryRequired,
                        message = if (storageRecoveryRequired) {
                            "Automation storage needs recovery"
                        } else {
                            it.message
                        },
                    )
                }
            }
        }
    }

    fun setTerminalProofReady(ready: Boolean) {
        terminalProofReady = ready
        _uiState.update {
            it.copy(
                connectionReady = currentConnectionConfig.isReady && ready,
                automations = recipes.map { recipe ->
                    recipe.toUiItem(currentConnectionConfig, terminalProofReady = ready)
                },
            )
        }
        if (!ready) {
            triggerCheckJob?.cancel()
            automationScheduler.stop()
        }
    }

    fun startTriggerMonitor() {
        if (!terminalProofReady) return
        automationScheduler.start()
    }

    fun run(recipeId: String) = execute(recipeId)

    fun validate(recipeId: String) = executeLocalValidation(recipeId)

    fun preflight(recipeId: String) = executePreflight(recipeId)

    fun liveTest(recipeId: String) = executeLiveTest(recipeId)

    fun toggle(recipeId: String, enabled: Boolean) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        if (enabled && (!terminalProofReady || !recipe.canEnable(currentConnectionConfig, nowMillis()))) {
            _uiState.update {
                it.copy(message = "Validation + preflight + live test required before enabling this rule")
            }
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
            val result = runRecipe(recipe, allowDangerous = true)
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
        viewModelScope.launch {
            automationRepository.resetDefaults()
            _uiState.update { it.copy(message = "Automation defaults restored") }
        }
    }

    fun create(input: AutomationDraftInput) {
        val title = input.title.trim()
        val command = input.command.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(message = "Name the rule first") }
            return
        }
        if (command.isBlank()) {
            _uiState.update { it.copy(message = "Add a command to run") }
            return
        }
        RawCommandPolicy.firstViolation(command)?.let { reason ->
            _uiState.update { it.copy(message = "Command blocked: $reason") }
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
                reviewedUserShellCommand(
                    id = "step_${title.slug()}",
                    title = title,
                    command = command,
                ),
            ),
            safety = AutomationSafety(requiresConfirmation = false),
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
                        automations = recipes.map { recipe ->
                            recipe.toUiItem(
                                config = currentConnectionConfig,
                                triggerSimulationReason = evaluation.reasonByRecipeId[recipe.id],
                                terminalProofReady = terminalProofReady,
                            )
                        },
                        triggerMonitorLabel = evaluation.message,
                        lastTriggerCheckedAtMillis = evaluation.checkedAtMillis,
                        nextWindowStartAtMillis = evaluation.nextWindowStartAtMillis,
                        nextWindowEndAtMillis = evaluation.nextWindowEndAtMillis,
                        triggerSimulatorReasons = evaluation.reasonByRecipeId,
                        message = if (auto || evaluation.dueRecipes.isEmpty()) it.message else evaluation.message,
                    )
                }
                evaluation.dueRecipes.forEach { recipe ->
                    evaluation.claimsByRecipeId[recipe.id]?.let { claim -> executeTriggered(recipe, claim) }
                }
            } finally {
                triggerCheckJob = null
            }
        }
    }

    fun saveGeneratedDraft(draft: GeneratedDraft): Boolean {
        val recipe = aiGeneratedContentPlanner.automationRecipeFromDraft(draft).getOrElse { error ->
            _uiState.update { it.copy(message = error.message ?: "Rule draft cannot be saved") }
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
            _uiState.update { it.copy(message = error.message ?: "Rule draft cannot be saved") }
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

    private fun execute(recipeId: String) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        if (!recipe.enabled) {
            _uiState.update { it.copy(message = "${recipe.title} is disabled") }
            return
        }
        if (!_uiState.value.connectionReady) {
            _uiState.update { it.copy(message = "Connect your Mac first") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(runningActionId = recipeId, message = null) }
            val result = if (recipe.safety.requiresConfirmation || recipe.steps.any { it.dangerous }) {
                ActionResult(
                    actionId = recipe.id,
                    title = recipe.title,
                    status = ActionResultStatus.RequiresConfirmation,
                    message = "${recipe.title} is waiting for approval",
                )
            } else {
                runRecipe(recipe, allowDangerous = false)
            }
            automationRepository.recordRun(recipeId, result)
            _uiState.update {
                it.copy(
                    runningActionId = null,
                    message = result.message,
                )
            }
        }
    }

    private fun executeLocalValidation(recipeId: String) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(runningActionId = recipeId, message = null) }
            val result = validateRecipe(recipe)
            automationRepository.recordTest(recipeId, result, recipe.revisionFingerprint())
            _uiState.update {
                it.copy(
                    runningActionId = null,
                    message = if (result.status == ActionResultStatus.Failed) {
                        "Validate: failed: ${result.message}"
                    } else {
                        "Validate: passed: ${result.message}"
                    },
                )
            }
        }
    }

    private fun executePreflight(recipeId: String) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        if (!_uiState.value.connectionReady) {
            _uiState.update { it.copy(message = "Connect your Mac first") }
            return
        }
        val snapshotTime = nowMillis()
        viewModelScope.launch {
            _uiState.update { it.copy(runningActionId = recipeId, message = null) }
            val preflight = runPreflightChecks(recipe, currentConnectionConfig, snapshotTime)
            automationRepository.recordPreflight(recipe.id, preflight)
            _uiState.update {
                it.copy(
                    automations = it.automations.map { item ->
                        if (item.id == recipe.id) {
                            item.copy(
                                lastPreflightLabel = preflight.toLabel(),
                                lastPreflightSucceeded = preflight.checks.all { it.passed },
                                triggerSimulationReason = preflight.checks.joinToString { check ->
                                    "${check.area.name}: ${if (check.passed) "ok" else check.message}"
                                },
                            )
                        } else {
                            item
                        }
                    },
                    runningActionId = null,
                    message = if (preflight.checks.all { it.passed }) {
                        "Preflight passed"
                    } else {
                        "Preflight failed"
                    },
                )
            }
        }
    }

    private fun executeLiveTest(recipeId: String) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        if (!_uiState.value.connectionReady) {
            _uiState.update { it.copy(message = "Connect your Mac first") }
            return
        }
        if (!recipe.hasCurrentValidPreflight(
                nowMillis(),
                connectionIdentity(currentConnectionConfig),
                recipe.requiredPermissions(),
            )
        ) {
            _uiState.update {
                it.copy(message = "Run preflight first for ${recipe.title}")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(runningActionId = recipeId, message = "Live test running") }
            val receipt = runLiveTestExecution(recipe)
                val persisted = automationRepository.recordLiveTest(recipe.id, receipt)
                val passed = persisted && receipt.terminalStatus == AutomationLiveTestTerminalStatus.PASSED
            _uiState.update {
                it.copy(
                    runningActionId = null,
                    automations = it.automations.map { item ->
                        if (item.id == recipe.id) {
                            item.copy(
                                lastLiveTestLabel = receipt.toLabel(),
                                lastLiveTestSucceeded = passed,
                                triggerSimulationReason = if (passed) {
                                    "Ready for background execution."
                                } else {
                                    "Live test failed; background execution blocked."
                                },
                            )
                        } else {
                            item
                        }
                    },
                    message = if (passed) {
                        "Live test passed: ${receipt.assertions.size} assertions"
                    } else {
                        "Live test failed: ${receipt.assertions.count { it.passed }} passed, ${receipt.assertions.size} assertions"
                    },
                )
            }
        }
    }

    private suspend fun executeTriggered(
        recipe: AutomationRecipe,
        claim: io.codecks.domain.automation.AutomationTriggerClaim,
    ) {
        if (claim.recipeRevision != recipe.revisionFingerprint()) {
            triggerEngine.complete(claim)
            return
        }
        _uiState.update { it.copy(runningActionId = recipe.id, message = "Trigger matched: ${recipe.title}") }
        val preparation = executionCoordinator.prepareAutomatic(
            recipeId = recipe.id,
            scheduledRevision = recipe.revisionFingerprint(),
        )
        if (preparation.claimDisposition() == AutomationClaimDisposition.RELEASE_FOR_RETRY) {
            val reason = (preparation as AutomationPreparation.Blocked).reason
            triggerEngine.release(claim)
            _uiState.update {
                it.copy(
                    runningActionId = null,
                    message = "Trigger retry pending: $reason",
                )
            }
            return
        }
        val result = when (preparation) {
            is AutomationPreparation.Ready -> {
                if (recipe.safety.requiresConfirmation || recipe.steps.any { it.dangerous }) {
                    ActionResult(
                        actionId = recipe.id,
                        title = recipe.title,
                        status = ActionResultStatus.RequiresConfirmation,
                        message = "Trigger matched, but ${recipe.title} needs manual confirmation",
                    )
                } else {
                    automationRepository.recordWorkerOutcome(
                        recipe.id,
                        preparation.outcome.copy(
                            code = io.codecks.domain.automation.AutomationWorkerOutcomeCode.EXECUTION_STARTED,
                            checkedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                    executionCoordinator.run(preparation.executable)
                }
            }
            is AutomationPreparation.Blocked -> ActionResult(
                actionId = recipe.id,
                title = recipe.title,
                status = ActionResultStatus.RequiresReview,
                message = preparation.reason,
            )
        }
        automationRepository.recordRun(recipe.id, result)
        if (preparation is AutomationPreparation.Ready) {
            automationRepository.recordWorkerOutcome(
                recipe.id,
                preparation.outcome.copy(
                    code = io.codecks.domain.automation.automaticOutcomeCode(result.status),
                    checkedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
        triggerEngine.complete(claim)
        _uiState.update {
            it.copy(
                runningActionId = null,
                message = "Trigger: ${result.message}",
            )
        }
    }

    private suspend fun runRecipe(recipe: AutomationRecipe, allowDangerous: Boolean): ActionResult {
        return executionCoordinator.runManual(recipe, allowDangerous)
    }

    private suspend fun validateRecipe(recipe: AutomationRecipe): ActionResult {
        if (recipe.steps.isEmpty()) {
            return ActionResult(
                actionId = recipe.id,
                title = recipe.title,
                status = ActionResultStatus.Failed,
                message = "Recipe has no buttons",
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
        val suffix = if (dangerousCount > 0) " Confirmation required for dangerous actions in live execution." else ""
        return ActionResult(
            actionId = recipe.id,
            title = recipe.title,
            status = ActionResultStatus.Succeeded,
            message = "${recipe.title} passed local validation. ${recipe.steps.size} step(s) checked.$suffix",
        )
    }

    private suspend fun runPreflightChecks(
        recipe: AutomationRecipe,
        config: io.codecks.data.ConnectionConfig,
        nowMillis: Long,
    ): AutomationPreflightReceipt {
        val pinnedTargetId = executionCoordinator.currentTargetId(config)
            ?: error("Current Mac target unavailable")
        val checks = mutableListOf<AutomationPreflightCheck>()
        val requiredTools = recipe.requiredCommandTools()
        val requiredPaths = recipe.requiredCommandPaths()
        val requiredApps = recipe.requiredApplications()
        val requestedPermissions = recipe.requiredPermissions()
        val hasExecutableTarget = requiredTools.isNotEmpty() ||
            requiredPaths.isNotEmpty() ||
            requiredApps.isNotEmpty() ||
            requestedPermissions.isNotEmpty() ||
            recipe.steps.isNotEmpty()

        checks += typedPreflightCheck(
            AutomationPreflightArea.Identity,
            AutomationCapabilityCodes.Identity,
            config.isReady,
            if (config.isReady) "Configured identity is present" else "Mac identity not configured",
            nowMillis,
        )
        val connectionReady = executionCoordinator.isPinnedTarget(config, pinnedTargetId)
        checks += typedPreflightCheck(
            AutomationPreflightArea.Connection,
            AutomationCapabilityCodes.Connection,
            connectionReady,
            if (connectionReady) "Connection profile loaded" else "Cannot use current Mac connection profile",
            nowMillis,
        )
        checks += automationCapabilityPreflightCheck(nowMillis)

        requiredTools.forEach { tool ->
            val passed = executionCoordinator.runProbe(pinnedTargetId, "command -v ${tool.shellQuotedForProbe()}").isSuccess
            checks += typedPreflightCheck(
                AutomationPreflightArea.Tool,
                automationRequirementCode(AutomationCapabilityCodes.Tool, tool),
                passed,
                if (passed) "Tool present: $tool" else "Tool missing: $tool",
                nowMillis,
            )
        }
        requiredPaths.forEach { path ->
            val passed = executionCoordinator.runProbe(pinnedTargetId, "[ -e ${path.shellPathForProbe()} ]").isSuccess
            checks += typedPreflightCheck(
                AutomationPreflightArea.Path,
                automationRequirementCode(AutomationCapabilityCodes.Path, path),
                passed,
                if (passed) "Path exists: $path" else "Path missing: $path",
                nowMillis,
            )
        }
        requiredApps.forEach { app ->
            val passed = executionCoordinator.runProbe(
                pinnedTargetId,
                "[ -d ${"/Applications/$app.app".shellQuotedForProbe()} ]",
            ).isSuccess
            checks += typedPreflightCheck(
                AutomationPreflightArea.App,
                automationRequirementCode(AutomationCapabilityCodes.App, app),
                passed,
                if (passed) "Application available: $app" else "Application missing: $app",
                nowMillis,
            )
        }

        val permissionChecks = mutableMapOf<String, Boolean>()
        requestedPermissions.forEach { permission ->
            val command = automationPermissionProbeCommand(permission)
            val passed = command != null && executionCoordinator.runProbe(pinnedTargetId, command).isSuccess
            permissionChecks[permission] = passed
            checks += typedPreflightCheck(
                AutomationPreflightArea.Permission,
                automationRequirementCode(AutomationCapabilityCodes.Permission, permission),
                passed,
                if (passed) "Permission check passed: $permission" else "Permission check failed: $permission",
                nowMillis,
            )
        }

        checks += typedPreflightCheck(
            AutomationPreflightArea.Target,
            AutomationCapabilityCodes.Target,
            hasExecutableTarget,
            if (hasExecutableTarget) {
                "Target selected: ${connectionTargetId(config)}"
            } else {
                "No action targets to check"
            },
            nowMillis,
        )

        check(executionCoordinator.isPinnedTarget(config, pinnedTargetId)) {
            "Mac target changed during preflight"
        }
        return AutomationPreflightReceipt(
            recipeRevision = recipe.revisionFingerprint(),
            checkedAtMillis = nowMillis,
            macIdentity = connectionIdentity(config),
            targetId = connectionTargetId(config),
            requiredCapabilities = recipe.requiredCapabilities(),
            checks = checks,
            commandTools = requiredTools,
            commandPaths = requiredPaths,
            commandApps = requiredApps,
            permissionSnapshot = permissionChecks.filterValues { it }.keys,
            requiredPermissions = requestedPermissions,
            requiredCheckCodes = automationRequiredPreflightCheckCodes(
                requiredTools,
                requiredPaths,
                requiredApps,
                requestedPermissions,
            ),
        )
    }

    private suspend fun runLiveTestExecution(recipe: AutomationRecipe): AutomationLiveTestReceipt {
        val preflight = requireNotNull(recipe.lastPreflight)
        val executable = executionCoordinator.pinLiveTest(recipe).getOrElse {
            return AutomationLiveTestEngine(
                executor = AutomationLiveTestActionExecutor {
                    AutomationActionProbeResult(exitCode = null, interrupted = true)
                },
            ).run(
                recipe = recipe,
                preflight = preflight,
                currentRevision = { "target-changed" },
            )
        }
        return AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { action ->
                if (!executionCoordinator.validatePinned(executable)) {
                    return@AutomationLiveTestActionExecutor AutomationActionProbeResult(exitCode = null)
                }
                val step = executable.recipe.steps.getOrNull(action.ordinal)
                    ?: executable.recipe.cleanupDefinition.action
                    ?: return@AutomationLiveTestActionExecutor AutomationActionProbeResult(exitCode = null)
                val result = actionRunner.run(step, allowDangerous = false)
                AutomationActionProbeResult(exitCode = if (result.succeeded) 0 else 1)
            },
        ).run(
            recipe = recipe,
            preflight = preflight,
            currentRevision = {
                recipes.firstOrNull { it.id == recipe.id }?.revisionFingerprint()
                    ?: "missing-recipe"
            },
        )
    }

    private suspend fun automationCapabilityPreflightCheck(checkedAtMillis: Long): AutomationPreflightCheck {
        val connected = connectionRepository.config.first().isReady
        val passed = connected && currentConnectionConfig.hostKey.isNotBlank()
        return typedPreflightCheck(
            AutomationPreflightArea.Provider,
            AutomationCapabilityCodes.Provider,
            passed,
            if (passed) "Provider ready" else "Provider not ready",
            checkedAtMillis,
        )
    }

    private fun connectionIdentity(config: io.codecks.data.ConnectionConfig): String =
        automationConnectionIdentity(config)

    private fun connectionTargetId(config: io.codecks.data.ConnectionConfig): String =
        if (config.isReady) connectionIdentity(config) else "current"

    private fun nowMillis(): Long = System.currentTimeMillis()

    private fun AutomationRecipe.canEnable(config: io.codecks.data.ConnectionConfig, nowMillis: Long): Boolean {
        if (!config.isReady) return false
        return hasCurrentValidLiveTest(nowMillis, connectionIdentity(config), requiredPermissions())
    }
}

private fun typedPreflightCheck(
    area: AutomationPreflightArea,
    capabilityCode: String,
    passed: Boolean,
    message: String,
    checkedAtMillis: Long,
): AutomationPreflightCheck = AutomationPreflightCheck.typed(
    area = area,
    capabilityCode = capabilityCode,
    status = if (passed) CapabilityStatus.SATISFIED else CapabilityStatus.BLOCKED,
    issueCode = null,
    remediation = null,
    checkedAtEpochMs = checkedAtMillis,
    message = message,
)

private object NoopAutomationScheduler : AutomationScheduler {
    override fun start() = Unit
    override fun stop() = Unit
}

private fun ActionSpec.validationError(): String? = when (this) {
    is ActionSpec.ShellCommand -> {
        when {
            command.isBlank() -> "Command is empty"
            else -> RawCommandPolicy.firstViolation(command)?.let { "Blocked command: $it" }
        }
    }
    is ActionSpec.CatalogAction -> if (id.isBlank()) "Action id is empty" else null
    is ActionSpec.DeckActionSpec -> when {
        id.isBlank() -> "Action id is empty"
        action.kind == io.codecks.domain.ActionKind.Ssh && action.command.isNullOrBlank() -> "Action command is empty"
        else -> null
    }
    is ActionSpec.LocalRoute -> if (route.isBlank()) "Route is empty" else null
}

private fun reviewedUserShellCommand(
    id: String,
    title: String,
    command: String,
): ActionSpec.ShellCommand {
    val targetSelector = io.codecks.domain.device.TargetSelector.CurrentDevice
    return ActionSpec.ShellCommand(
        id = id,
        title = title,
        command = command,
        trustLevel = ShellTrustLevel.UserReviewed,
        dangerous = false,
        targetSelector = targetSelector,
        commandOrigin = CommandOrigin.UserAuthored,
        review = CommandReview(
            reviewedRevision = commandRevision(
                command = command,
                targetSelector = targetSelector,
                origin = CommandOrigin.UserAuthored,
                dangerous = false,
            ),
        ),
    )
}

private fun AutomationRecipe.requiredCommandPaths(): Set<String> =
    (steps + listOfNotNull(cleanupDefinition.action))
    .mapNotNull { spec ->
        when (spec) {
            is ActionSpec.ShellCommand -> spec.command
            is ActionSpec.DeckActionSpec -> spec.action.command
            is ActionSpec.CatalogAction -> null
            is ActionSpec.LocalRoute -> null
        }
    }
    .flatMap { command ->
        command.shellTokens()
            .filter { token -> token.startsWith("/") || token.startsWith("~/") }
    }
    .map { it.trimEnd(';', '&') }
    .toSet()

private fun AutomationRecipe.requiredApplications(): Set<String> =
    (steps + listOfNotNull(cleanupDefinition.action))
    .flatMap { spec ->
        val command = when (spec) {
            is ActionSpec.ShellCommand -> spec.command
            is ActionSpec.DeckActionSpec -> spec.action.command
            is ActionSpec.CatalogAction -> null
            is ActionSpec.LocalRoute -> null
        } ?: return@flatMap emptyList()
        Regex("open\\s+-a\\s+(?:\"([^\"]+)\"|'([^']+)'|([^\\s]+))").findAll(command).map {
            (it.groupValues[1].ifBlank { it.groupValues[2] }).trim()
                .ifBlank { it.groupValues[3].trim() }
        }.toList()
    }
    .toSet()

internal fun String.shellQuotedForProbe(): String = "'${replace("'", "'\"'\"'")}'"
internal fun String.shellPathForProbe(): String = when {
    this == "~" -> "\"\$HOME\""
    startsWith("~/") -> "\"\$HOME\"/${drop(2).shellQuotedForProbe()}"
    else -> shellQuotedForProbe()
}

internal fun String.shellTokens(): List<String> {
    val result = mutableListOf<String>()
    val token = StringBuilder()
    var quote: Char? = null
    var escaped = false
    fun flush() {
        if (token.isNotEmpty()) result += token.toString().also { token.clear() }
    }
    forEach { char ->
        when {
            escaped -> { token.append(char); escaped = false }
            char == '\\' && quote != '\'' -> escaped = true
            quote != null && char == quote -> quote = null
            quote == null && (char == '\'' || char == '"') -> quote = char
            quote == null && char.isWhitespace() -> flush()
            else -> token.append(char)
        }
    }
    if (escaped) token.append('\\')
    flush()
    return result
}

private fun AutomationRecipe.toUiItem(
    config: io.codecks.data.ConnectionConfig,
    triggerSimulationReason: String? = null,
    terminalProofReady: Boolean = true,
): AutomationItem {
    val lastValidation = lastTest?.let { currentTest ->
        if (lastTestRevision == revisionFingerprint()) {
            currentTest to (currentTest.status == ActionResultStatus.Succeeded)
        } else {
            null
        }
    }
    val lastPreflight = this.lastPreflight
    val lastLiveTest = this.lastLiveTest
    val preflightPassed = lastPreflight?.checks?.all { it.passed } == true
    val liveTestPassed = lastLiveTest?.assertions?.isNotEmpty() == true &&
        lastLiveTest.assertions.all { it.passed } && lastLiveTest.cleanup.passed
    val preflightLabel = lastPreflight?.toLabel()
    val liveTestLabel = lastLiveTest?.toLabel()
    val canEnableNow = terminalProofReady && config.isReady && hasCurrentValidLiveTest(
        nowMillis = System.currentTimeMillis(),
        requiredMacIdentity = automationConnectionIdentity(config),
        requiredPermissions = requiredPermissions(),
    )
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
        dangerous = safety.requiresConfirmation || steps.any { it.dangerous },
        enabled = enabled,
        lastRunLabel = lastRun?.toLabel(),
        lastRunSucceeded = lastRun?.status == ActionResultStatus.Succeeded,
        lastTestLabel = lastValidation?.first?.toTestLabel(),
        lastTestSucceeded = lastValidation?.second,
        lastPreflightLabel = preflightLabel,
        lastPreflightSucceeded = preflightPassed,
        lastLiveTestLabel = liveTestLabel,
        lastLiveTestSucceeded = liveTestPassed,
        cleanupPassed = lastLiveTest?.cleanup?.passed,
        recoveryRequired = recoveryRequired,
        canEnable = canEnableNow,
        triggerSimulationReason = triggerSimulationReason,
        approvalPending = pendingApproval != null,
        runHistory = runHistory.map { it.toHistoryItem() },
    )
}

private fun automationConnectionIdentity(config: io.codecks.data.ConnectionConfig): String =
    opaqueAutomationConnectionIdentity(
        host = config.host,
        port = config.port,
        user = config.user,
        hostKey = config.hostKey,
    )

private fun AutomationPreflightReceipt.toLabel(): String =
    when {
        checks.all { it.passed } -> "Preflight passed"
        else -> "Preflight failed"
    }

private fun AutomationLiveTestReceipt.toLabel(): String {
    val allPassed = assertions.all { it.passed } && cleanup.passed
    return if (allPassed) "Live test passed" else "Live test failed"
}

private fun AutomationRunSummary.toLabel(): String =
    when (status) {
        ActionResultStatus.Succeeded -> "Last run OK"
        ActionResultStatus.Failed -> "Last run failed"
        ActionResultStatus.RequiresConfirmation -> "Needs confirmation"
        ActionResultStatus.RequiresReview -> "Needs review"
    }

private fun AutomationRunSummary.toTestLabel(): String =
    when (status) {
        ActionResultStatus.Succeeded -> "Validation passed"
        ActionResultStatus.Failed -> "Validation failed: ${message.take(80)}"
        ActionResultStatus.RequiresConfirmation -> "Validation needs review"
        ActionResultStatus.RequiresReview -> "Validation needs review"
    }

private fun AutomationRunSummary.toHistoryItem(): AutomationHistoryItem =
    AutomationHistoryItem(
        timestampMillis = timestampMillis,
        statusLabel = toLabel(),
        message = message,
        logs = logs,
        succeeded = status == ActionResultStatus.Succeeded,
        needsApproval = status == ActionResultStatus.RequiresConfirmation || status == ActionResultStatus.RequiresReview,
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
