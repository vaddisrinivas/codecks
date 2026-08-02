package io.codecks.data.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.commandRevision
import io.codecks.data.ConnectionRepository
import io.codecks.domain.automation.AutomationExecutionEngine
import io.codecks.domain.automation.ExecutableAutomation
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationWorkerOutcome
import io.codecks.domain.automation.AutomationWorkerOutcomeCode
import io.codecks.domain.automation.automationConnectionIdentity
import io.codecks.domain.automation.automationPermissionProbeCommand
import io.codecks.domain.automation.evaluateAutomationWorkerEligibility
import io.codecks.domain.automation.requiredCommandTools
import io.codecks.domain.automation.requiredPermissions
import io.codecks.domain.automation.revisionFingerprint
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.DeviceRepository
import io.codecks.domain.device.DeviceGroup
import io.codecks.domain.device.TargetDevice
import io.codecks.domain.device.TargetSelector
import io.codecks.data.ConnectionConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Sole automatic-execution gate. It binds proof, recipe revision, and the current Mac target,
 * then rechecks all three immediately before every action and cleanup dispatch.
 */
@Singleton
class AutomationExecutionCoordinator @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val connectionRepository: ConnectionRepository,
    private val actionRunner: ActionRunner,
    private val deviceRepository: DeviceRepository,
) {
    internal constructor(
        automationRepository: AutomationRepository,
        connectionRepository: ConnectionRepository,
        actionRunner: ActionRunner,
    ) : this(
        automationRepository,
        connectionRepository,
        actionRunner,
        CurrentConnectionTestDeviceRepository,
    )
    suspend fun prepareAutomatic(
        recipeId: String,
        scheduledRevision: String,
        workerAttempt: Int = 0,
    ): AutomationPreparation {
        val recipe = automationRepository.recipes.first().firstOrNull { it.id == recipeId }
            ?: return AutomationPreparation.Blocked("Automation no longer exists")
        val config = connectionRepository.config.first()
        val identity = automationConnectionIdentity(config.host, config.port, config.user, config.hostKey)
        val targetId = currentTargetId(config)
            ?: return AutomationPreparation.Blocked("Execution gate: current Mac target unavailable")
        val tools = recipe.requiredCommandTools()
        val availableTools = tools.filterTo(mutableSetOf()) { tool ->
            connectionRepository.runCommandOnTarget(targetId.value, "command -v ${tool.shellWord()}").isSuccess
        }
        val permissions = recipe.requiredPermissions().filterTo(mutableSetOf()) { permission ->
            automationPermissionProbeCommand(permission)
                ?.let { connectionRepository.runCommandOnTarget(targetId.value, it).isSuccess }
                ?: false
        }
        val eligibility = evaluateAutomationWorkerEligibility(
            recipe = recipe,
            scheduledRevision = scheduledRevision,
            currentMacIdentity = identity,
            availableTools = availableTools,
            currentPermissions = permissions,
            nowMillis = System.currentTimeMillis(),
            workerAttempt = workerAttempt,
        )
        if (eligibility.code !in ELIGIBLE_CODES) {
            return AutomationPreparation.Blocked("Execution gate: ${eligibility.code.persistedCode}", eligibility)
        }
        val preflight = recipe.lastPreflight
            ?: return AutomationPreparation.Blocked("Execution gate: missing preflight", eligibility)
        if (preflight.targetId != identity) {
            return AutomationPreparation.Blocked(
                "Execution gate: target proof changed",
                eligibility.copy(code = AutomationWorkerOutcomeCode.TRUST_CHANGED),
            )
        }
        val pathsReady = preflight.commandPaths.all { path ->
            connectionRepository.runCommandOnTarget(targetId.value, "[ -e ${path.shellPathQuoted()} ]").isSuccess
        }
        if (!pathsReady) return AutomationPreparation.Blocked(
            "Execution gate: required path changed",
            eligibility.copy(code = AutomationWorkerOutcomeCode.REQUIREMENTS_CHANGED),
        )
        val appsReady = preflight.commandApps.all { app ->
            connectionRepository.runCommandOnTarget(targetId.value, "[ -d ${"/Applications/$app.app".shellQuoted()} ]").isSuccess
        }
        if (!appsReady) return AutomationPreparation.Blocked(
            "Execution gate: required app changed",
            eligibility.copy(code = AutomationWorkerOutcomeCode.REQUIREMENTS_CHANGED),
        )
        if (!isPinnedTarget(config, targetId)) {
            return AutomationPreparation.Blocked("Execution gate: current Mac target changed", eligibility)
        }
        return AutomationPreparation.Ready(
            ExecutableAutomation(
                recipe = recipe.pinTo(targetId),
                sourceRecipeId = recipe.id,
                revision = recipe.revisionFingerprint(),
                macIdentity = identity,
                targetId = targetId,
            ),
            eligibility,
        )
    }

    suspend fun pinLiveTest(recipe: AutomationRecipe): Result<ExecutableAutomation> = runCatching {
        val current = automationRepository.recipes.first().firstOrNull { it.id == recipe.id }
            ?: error("Automation no longer exists")
        check(current.revisionFingerprint() == recipe.revisionFingerprint()) {
            "Automation changed before live test"
        }
        val config = connectionRepository.config.first()
        val identity = automationConnectionIdentity(config.host, config.port, config.user, config.hostKey)
        val preflight = requireNotNull(recipe.lastPreflight) { "Preflight is missing" }
        check(preflight.macIdentity == identity) { "Mac identity changed before live test" }
        check(preflight.targetId == identity) { "Mac target changed before live test" }
        val targetId = requireNotNull(currentTargetId(config)) {
            "Current Mac target unavailable"
        }
        ExecutableAutomation(
            recipe = recipe.pinTo(targetId),
            sourceRecipeId = recipe.id,
            revision = recipe.revisionFingerprint(),
            macIdentity = identity,
            targetId = targetId,
        )
    }

    suspend fun run(executable: ExecutableAutomation, allowDangerous: Boolean = false): ActionResult =
        AutomationExecutionEngine(actionRunner).run(executable, allowDangerous) {
            validatePinned(executable)
        }

    suspend fun runManual(recipe: AutomationRecipe, allowDangerous: Boolean): ActionResult {
        val config = connectionRepository.config.first()
        val targetId = currentTargetId(config)
            ?: return ActionResult(
                recipe.id,
                recipe.title,
                ActionResultStatus.Failed,
                "Current Mac target unavailable",
            )
        val executable = ExecutableAutomation(
            recipe = recipe.pinTo(targetId),
            sourceRecipeId = recipe.id,
            revision = recipe.revisionFingerprint(),
            macIdentity = automationConnectionIdentity(config.host, config.port, config.user, config.hostKey),
            targetId = targetId,
        )
        return run(executable, allowDangerous)
    }

    suspend fun validatePinned(executable: ExecutableAutomation): Boolean {
        val current = automationRepository.recipes.first().firstOrNull { it.id == executable.sourceRecipeId }
            ?: return false
        if (current.revisionFingerprint() != executable.revision) return false
        val config = connectionRepository.config.first()
        val identity = automationConnectionIdentity(config.host, config.port, config.user, config.hostKey)
        if (identity != executable.macIdentity) return false
        return currentTargetId(config) == executable.targetId
    }

    internal suspend fun currentTargetId(config: ConnectionConfig): DeviceId? =
        connectionRepository.savedTargets().firstOrNull { target ->
                target.host == config.host && target.port == config.port && target.user == config.user &&
                    target.hostKey == config.hostKey
            }?.id?.let(::DeviceId)
            ?: deviceRepository.currentDeviceId()?.takeIf { candidate ->
                connectionRepository.savedTargets().none { it.id == candidate.value }
            }

    internal suspend fun runProbe(targetId: DeviceId, command: String): Result<String> =
        connectionRepository.runCommandOnTarget(targetId.value, command)

    internal suspend fun isPinnedTarget(config: ConnectionConfig, targetId: DeviceId): Boolean =
        connectionRepository.config.first() == config && currentTargetId(config) == targetId

    companion object {
        private val ELIGIBLE_CODES = setOf(
            AutomationWorkerOutcomeCode.ELIGIBLE,
            AutomationWorkerOutcomeCode.WORKER_RECREATED,
        )
    }
}

sealed interface AutomationPreparation {
    data class Ready(
        val executable: ExecutableAutomation,
        val outcome: AutomationWorkerOutcome,
    ) : AutomationPreparation

    data class Blocked(
        val reason: String,
        val outcome: AutomationWorkerOutcome? = null,
    ) : AutomationPreparation
}

internal enum class AutomationClaimDisposition {
    COMPLETE,
    RELEASE_FOR_RETRY,
}

internal fun AutomationPreparation.claimDisposition(): AutomationClaimDisposition =
    if (this is AutomationPreparation.Blocked && outcome == null) {
        AutomationClaimDisposition.RELEASE_FOR_RETRY
    } else {
        AutomationClaimDisposition.COMPLETE
    }

private fun AutomationRecipe.pinTo(targetId: DeviceId): AutomationRecipe = copy(
    steps = steps.map { it.pinTo(targetId) },
    cleanupDefinition = cleanupDefinition.copy(
        action = cleanupDefinition.action?.pinShellTo(targetId),
    ),
)

private fun ActionSpec.pinTo(targetId: DeviceId): ActionSpec {
    val selector = TargetSelector.SpecificDevice(targetId)
    return when (this) {
        is ActionSpec.ShellCommand -> pinShellTo(targetId)
        is ActionSpec.CatalogAction -> copy(targetSelector = selector)
        is ActionSpec.LocalRoute -> copy(targetSelector = selector)
        is ActionSpec.DeckActionSpec -> action.copy(targetSelector = selector).let { pinned ->
            val revision = pinned.commandRevision()
            copy(
                action = pinned.copy(
                    commandReview = action.commandReview.copy(
                        reviewedRevision = revision.takeIf { action.commandReview.reviewedRevision != null },
                        checkedRevision = revision.takeIf { action.commandReview.checkedRevision != null },
                    ),
                ),
            )
        }
    }
}

private fun ActionSpec.ShellCommand.pinShellTo(targetId: DeviceId): ActionSpec.ShellCommand =
    copy(targetSelector = TargetSelector.SpecificDevice(targetId)).let { pinned ->
        val revision = pinned.commandRevision()
        pinned.copy(
            review = review.copy(
                reviewedRevision = revision.takeIf { review.reviewedRevision != null },
                checkedRevision = revision.takeIf { review.checkedRevision != null },
            ),
        )
    }

private fun String.shellWord(): String {
    require(matches(Regex("[A-Za-z0-9_.+-]+"))) { "Unsafe tool requirement" }
    return this
}

private fun String.shellQuoted(): String = "'${replace("'", "'\"'\"'")}'"
private fun String.shellPathQuoted(): String = when {
    this == "~" -> "\"\$HOME\""
    startsWith("~/") -> "\"\$HOME\"/${drop(2).shellQuoted()}"
    else -> shellQuoted()
}

private object CurrentConnectionTestDeviceRepository : DeviceRepository {
    override suspend fun devices(): List<TargetDevice> = emptyList()
    override suspend fun groups(): List<DeviceGroup> = emptyList()
    override suspend fun currentDeviceId(): DeviceId = DeviceId("current-connection")
}
