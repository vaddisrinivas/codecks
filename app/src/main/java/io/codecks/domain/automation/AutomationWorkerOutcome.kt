package io.codecks.domain.automation

enum class AutomationWorkerOutcomeCode(val persistedCode: String) {
    ELIGIBLE("eligible"),
    EXECUTED("executed"),
    EXECUTION_FAILED("execution_failed"),
    DISABLED("disabled"),
    STALE_REVISION("stale_revision"),
    GATE_INVALID("gate_invalid"),
    PERMISSIONS_CHANGED("permissions_changed"),
    TRUST_CHANGED("trust_changed"),
    TOOLS_CHANGED("tools_changed"),
    RECOVERY_REQUIRED("recovery_required"),
    INTERRUPTED("interrupted"),
    RETRY_SCHEDULED("retry_scheduled"),
    WORKER_RECREATED("worker_recreated"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): AutomationWorkerOutcomeCode =
            entries.firstOrNull { it.persistedCode == value } ?: GATE_INVALID
    }
}

enum class AutomationWorkerRetryDisposition(val persistedCode: String) {
    NONE("none"),
    RETRY("retry"),
    RECREATE("recreate"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): AutomationWorkerRetryDisposition =
            entries.firstOrNull { it.persistedCode == value } ?: NONE
    }
}

data class AutomationWorkerOutcome(
    val code: AutomationWorkerOutcomeCode,
    val checkedAtMillis: Long,
    val scheduledRevision: String,
    val currentRevision: String,
    val retryDisposition: AutomationWorkerRetryDisposition = AutomationWorkerRetryDisposition.NONE,
    val workerAttempt: Int = 0,
) {
    val requiresNeedsReview: Boolean
        get() = code in setOf(
            AutomationWorkerOutcomeCode.STALE_REVISION,
            AutomationWorkerOutcomeCode.GATE_INVALID,
            AutomationWorkerOutcomeCode.PERMISSIONS_CHANGED,
            AutomationWorkerOutcomeCode.TRUST_CHANGED,
            AutomationWorkerOutcomeCode.TOOLS_CHANGED,
            AutomationWorkerOutcomeCode.RECOVERY_REQUIRED,
            AutomationWorkerOutcomeCode.EXECUTION_FAILED,
        )
}

fun AutomationRecipe.withWorkerOutcome(outcome: AutomationWorkerOutcome): AutomationRecipe =
    if (outcome.requiresNeedsReview) {
        copy(
            enabled = false,
            stage = AutomationStage.NEEDS_REVIEW,
            lastWorkerOutcome = outcome,
            gateStamp = gateStamp?.copy(liveTestReceiptId = null),
        )
    } else {
        copy(lastWorkerOutcome = outcome)
    }

fun evaluateAutomationWorkerEligibility(
    recipe: AutomationRecipe,
    scheduledRevision: String,
    currentMacIdentity: String,
    availableTools: Set<String>,
    currentPermissions: Set<String>,
    nowMillis: Long,
    workerAttempt: Int = 0,
): AutomationWorkerOutcome {
    val currentRevision = recipe.revisionFingerprint()
    fun outcome(code: AutomationWorkerOutcomeCode) = AutomationWorkerOutcome(
        code = code,
        checkedAtMillis = nowMillis,
        scheduledRevision = scheduledRevision,
        currentRevision = currentRevision,
        workerAttempt = workerAttempt,
    )
    if (scheduledRevision != currentRevision) {
        return outcome(AutomationWorkerOutcomeCode.STALE_REVISION)
    }
    if (!recipe.enabled) return outcome(AutomationWorkerOutcomeCode.DISABLED)
    if (recipe.recoveryRequired) return outcome(AutomationWorkerOutcomeCode.RECOVERY_REQUIRED)
    val preflight = recipe.lastPreflight ?: return outcome(AutomationWorkerOutcomeCode.GATE_INVALID)
    if (preflight.macIdentity != currentMacIdentity) {
        return outcome(AutomationWorkerOutcomeCode.TRUST_CHANGED)
    }
    val requiredPermissions = recipe.requiredPermissions()
    if (!preflight.permissionSnapshot.containsAll(requiredPermissions) ||
        !currentPermissions.containsAll(requiredPermissions)
    ) {
        return outcome(AutomationWorkerOutcomeCode.PERMISSIONS_CHANGED)
    }
    val requiredTools = recipe.requiredCommandTools()
    if (preflight.commandTools != requiredTools || !availableTools.containsAll(requiredTools)) {
        return outcome(AutomationWorkerOutcomeCode.TOOLS_CHANGED)
    }
    if (!recipe.hasCurrentRevisionGateForExecution() ||
        !recipe.hasCurrentValidLiveTest(
            nowMillis = nowMillis,
            requiredMacIdentity = currentMacIdentity,
            requiredPermissions = requiredPermissions,
        )
    ) {
        return outcome(AutomationWorkerOutcomeCode.GATE_INVALID)
    }
    return outcome(
        if (workerAttempt > 0) {
            AutomationWorkerOutcomeCode.WORKER_RECREATED
        } else {
            AutomationWorkerOutcomeCode.ELIGIBLE
        },
    )
}
