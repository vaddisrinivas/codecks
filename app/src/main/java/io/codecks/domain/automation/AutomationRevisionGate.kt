package io.codecks.domain.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.domain.privacy.DiagnosticRedactor
import java.security.MessageDigest

const val AUTOMATION_POLICY_VERSION: Int = 1

enum class AutomationStage {
    DRAFT,
    VALIDATED,
    PREFLIGHT_PASSED,
    LIVE_TEST_PASSED,
    ENABLED,
    NEEDS_REVIEW,
}

data class AutomationGateStamp(
    val revisionId: String,
    val policyVersion: Int = AUTOMATION_POLICY_VERSION,
    val capabilityFingerprint: String = "",
    val hostTrustVersion: String? = null,
    val validationReceiptId: String? = null,
    val preflightReceiptId: String? = null,
    val liveTestReceiptId: String? = null,
)

/**
 * Backup payloads are data, never proof. A restored rule must repeat the local validation,
 * live preflight, and live test on this device before it can run.
 */
fun AutomationRecipe.withoutImportedExecutionProof(): AutomationRecipe {
    val revision = revisionFingerprint()
    return copy(
        enabled = false,
        lastRun = null,
        runHistory = emptyList(),
        lastTest = null,
        lastTestRevision = null,
        lastPreflight = null,
        lastLiveTest = null,
        recoveryRequired = false,
        lastWorkerOutcome = null,
        pendingApproval = null,
        stage = AutomationStage.DRAFT,
        gateStamp = AutomationGateStamp(revisionId = revision),
    )
}

fun AutomationRecipe.enforceRevisionGate(previous: AutomationRecipe?): AutomationRecipe {
    val revision = revisionFingerprint()
    val inheritedRecoveryRequired = recoveryRequired || previous?.recoveryRequired == true
    if (previous == null || previous.revisionFingerprint() != revision) {
        return copy(
            enabled = false,
            stage = AutomationStage.DRAFT,
            gateStamp = AutomationGateStamp(revisionId = revision),
            lastTest = null,
            lastTestRevision = null,
            lastPreflight = null,
            lastLiveTest = null,
            recoveryRequired = inheritedRecoveryRequired,
            lastWorkerOutcome = null,
            pendingApproval = null,
        )
    }
    val normalized = copy(
        recoveryRequired = inheritedRecoveryRequired,
    ).normalizePersistedRevisionGate()
    val requestedEnabled = enabled
    return when {
        requestedEnabled && normalized.canPersistEnabled() ->
            normalized.copy(enabled = true, stage = AutomationStage.ENABLED)
        requestedEnabled ->
            normalized.copy(enabled = false)
        normalized.stage == AutomationStage.ENABLED ->
            normalized.copy(enabled = false, stage = AutomationStage.LIVE_TEST_PASSED)
        else -> normalized.copy(enabled = false)
    }
}

fun AutomationRecipe.normalizePersistedRevisionGate(): AutomationRecipe {
    val revision = revisionFingerprint()
    val stamp = gateStamp
    if (stamp == null ||
        stamp.revisionId != revision ||
        stamp.policyVersion != AUTOMATION_POLICY_VERSION
    ) {
        return copy(
            enabled = false,
            stage = AutomationStage.DRAFT,
            gateStamp = AutomationGateStamp(revisionId = revision),
            lastTest = null,
            lastTestRevision = null,
            lastPreflight = null,
            lastLiveTest = null,
            pendingApproval = null,
        )
    }
    val validationValid =
        lastTest?.status == ActionResultStatus.Succeeded &&
            lastTestRevision == revision &&
            stamp.validationReceiptId == automationReceiptId(
                "validation",
                revision,
                lastTest.timestampMillis,
            )
    val preflightValid = validationValid &&
        lastPreflight?.recipeRevision == revision &&
        lastPreflight.mandatoryChecksSatisfied() &&
        stamp.preflightReceiptId == lastPreflight.receiptId &&
        stamp.capabilityFingerprint == lastPreflight.capabilityFingerprint() &&
        stamp.hostTrustVersion == automationOpaqueVersion(lastPreflight.macIdentity)
    val normalizedPlan = AutomationExecutionPlanCompiler.compile(this).getOrNull()
    val liveTestValid = preflightValid &&
        !recoveryRequired &&
        normalizedPlan != null &&
        lastLiveTest?.isValidTerminalPass(
            revision = revision,
            plan = normalizedPlan,
            preflight = lastPreflight,
        ) == true &&
        stamp.liveTestReceiptId == lastLiveTest.receiptId
    val validStage = when (stage) {
        AutomationStage.DRAFT,
        AutomationStage.NEEDS_REVIEW,
        -> true
        AutomationStage.VALIDATED -> validationValid
        AutomationStage.PREFLIGHT_PASSED -> preflightValid
        AutomationStage.LIVE_TEST_PASSED,
        AutomationStage.ENABLED,
        -> liveTestValid
    }
    if (!validStage) {
        return copy(
            enabled = false,
            stage = AutomationStage.NEEDS_REVIEW,
            gateStamp = stamp.copy(
                validationReceiptId = null,
                preflightReceiptId = null,
                liveTestReceiptId = null,
            ),
        )
    }
    return if (enabled && stage != AutomationStage.ENABLED) copy(enabled = false) else this
}

fun AutomationRecipe.withValidationResult(
    result: ActionResult,
    revision: String,
): AutomationRecipe {
    if (revision != revisionFingerprint()) return this
    val summary = AutomationRunSummary(
        status = result.status,
        message = DiagnosticRedactor.redact(result.message, maxLength = 240),
        logs = DiagnosticRedactor.redact(result.logs, maxLength = 1_200),
        timestampMillis = result.timestampMillis,
    )
    if (result.status != ActionResultStatus.Succeeded) {
        return copy(
            enabled = false,
            stage = AutomationStage.DRAFT,
            gateStamp = AutomationGateStamp(revisionId = revision),
            lastTest = summary,
            lastTestRevision = revision,
            lastPreflight = null,
            lastLiveTest = null,
        )
    }
    return copy(
        enabled = false,
        stage = AutomationStage.VALIDATED,
        gateStamp = AutomationGateStamp(
            revisionId = revision,
            validationReceiptId = automationReceiptId("validation", revision, result.timestampMillis),
        ),
        lastTest = summary,
        lastTestRevision = revision,
        lastPreflight = null,
        lastLiveTest = null,
    )
}

fun AutomationRecipe.withPreflightReceipt(receipt: AutomationPreflightReceipt): AutomationRecipe {
    val revision = revisionFingerprint()
    val stamp = gateStamp ?: return this
    if (stage != AutomationStage.VALIDATED ||
        stamp.revisionId != revision ||
        stamp.validationReceiptId == null ||
        receipt.recipeRevision != revision
    ) {
        return this
    }
    if (!receipt.mandatoryChecksSatisfied()) {
        return copy(
            enabled = false,
            lastPreflight = receipt,
            lastLiveTest = null,
            gateStamp = stamp.copy(
                preflightReceiptId = null,
                liveTestReceiptId = null,
            ),
        )
    }
    return copy(
        enabled = false,
        stage = AutomationStage.PREFLIGHT_PASSED,
        gateStamp = stamp.copy(
            capabilityFingerprint = receipt.capabilityFingerprint(),
            hostTrustVersion = automationOpaqueVersion(receipt.macIdentity),
            preflightReceiptId = receipt.receiptId,
            liveTestReceiptId = null,
        ),
        lastPreflight = receipt,
        lastLiveTest = null,
    )
}

fun AutomationRecipe.withLiveTestReceipt(receipt: AutomationLiveTestReceipt): AutomationRecipe {
    val revision = revisionFingerprint()
    val stamp = gateStamp ?: return this
    val preflight = lastPreflight ?: return this
    val redactedReceipt = receipt.redactedTerminal()
    val plan = AutomationExecutionPlanCompiler.compile(this).getOrNull()
    if (stage != AutomationStage.PREFLIGHT_PASSED ||
        stamp.revisionId != revision ||
        stamp.validationReceiptId == null ||
        stamp.preflightReceiptId != preflight.receiptId ||
        plan == null
    ) {
        return this
    }
    val requiresRecovery = recoveryRequired || redactedReceipt.recoveryRequired
    if (requiresRecovery || !redactedReceipt.isValidTerminalPass(revision, plan, preflight)) {
        return copy(
            enabled = false,
            lastLiveTest = redactedReceipt,
            recoveryRequired = requiresRecovery,
            gateStamp = stamp.copy(liveTestReceiptId = null),
        )
    }
    return copy(
        enabled = false,
        stage = AutomationStage.LIVE_TEST_PASSED,
        gateStamp = stamp.copy(liveTestReceiptId = redactedReceipt.receiptId),
        lastLiveTest = redactedReceipt,
        recoveryRequired = false,
    )
}

fun AutomationRecipe.hasCurrentRevisionGateForExecution(): Boolean =
    enabled &&
        !recoveryRequired &&
        stage == AutomationStage.ENABLED &&
        gateStamp?.revisionId == revisionFingerprint() &&
        gateStamp.policyVersion == AUTOMATION_POLICY_VERSION &&
        gateStamp.validationReceiptId != null &&
        gateStamp.preflightReceiptId != null &&
        gateStamp.liveTestReceiptId != null

private fun AutomationRecipe.canPersistEnabled(): Boolean =
    !recoveryRequired &&
        (stage == AutomationStage.LIVE_TEST_PASSED || stage == AutomationStage.ENABLED)

private fun AutomationPreflightReceipt.capabilityFingerprint(): String =
    automationOpaqueVersion(
        buildString {
            append(requiredCapabilities.map(Enum<*>::name).sorted().joinToString(","))
            append('|')
            append(permissionSnapshot.sorted().joinToString(","))
            append('|')
            append(commandTools.sorted().joinToString(","))
            append('|')
            append(
                checks.sortedBy { it.capability.capabilityCode }.joinToString(",") { check ->
                    "${check.capability.capabilityCode}:${check.capability.status.persistedCode}:${check.mandatory}"
                },
            )
        },
    )

internal fun AutomationLiveTestReceipt.isValidTerminalPass(
    revision: String,
    plan: NormalizedAutomationPlan,
    preflight: AutomationPreflightReceipt,
): Boolean {
    if (terminalStatus != AutomationLiveTestTerminalStatus.PASSED) return false
    if (recoveryRequired) return false
    if (recipeRevision != revision || normalizedPlanHash != plan.planHash) return false
    if (preflightReceiptId != preflight.receiptId) return false
    if (preflightCheckedAtMillis != preflight.checkedAtMillis) return false
    if (timeoutPolicyCode != AutomationLiveTestTimeoutPolicy.BOUNDED_V1.persistedCode) return false
    if (completedAtMillis < checkedAtMillis) return false
    if (macIdentity.isNotEmpty()) return false
    if (!cleanup.passed || cleanup.command.isNotEmpty()) return false
    val declaredCleanup = plan.cleanup?.takeIf {
        AutomationCleanupTrigger.SUCCESS in it.runAfter
    }
    if (declaredCleanup == null) {
        if (cleanup.outcomeCode != AutomationLiveTestCleanupCode.NOT_REQUIRED) return false
    } else {
        if (cleanup.outcomeCode != AutomationLiveTestCleanupCode.SUCCEEDED) return false
        if (cleanup.cleanupId != declaredCleanup.action.assertion.assertionId) return false
        if (cleanup.actionRevision != declaredCleanup.action.actionRevision) return false
        if (cleanup.undoGuarantee != declaredCleanup.undoGuarantee) return false
    }
    if (assertions.size != plan.assertions.size) return false
    val assertionsValid = assertions.zip(plan.assertions).all { (actual, expected) ->
        actual.passed &&
            actual.assertionId == expected.assertionId &&
            actual.actionRevision == expected.actionRevision &&
            actual.outcomeCode == AutomationLiveTestOutcomeCode.EXIT_CODE_ZERO &&
            actual.terminalStatus == AutomationStepTerminalStatus.SUCCEEDED &&
            actual.message == actual.outcomeCode.persistedCode
    }
    if (!assertionsValid) return false
    return receiptId == automationLiveTestReceiptId(
        recipeRevision = recipeRevision,
        normalizedPlanHash = normalizedPlanHash,
        preflightReceiptId = preflightReceiptId,
        timeoutPolicyCode = timeoutPolicyCode,
        terminalStatus = terminalStatus,
        checkedAtMillis = checkedAtMillis,
        completedAtMillis = completedAtMillis,
    )
}

fun automationReceiptId(kind: String, revision: String, timestampMillis: Long): String =
    automationOpaqueVersion("$kind|$revision|$timestampMillis")

fun automationLiveTestReceiptId(
    recipeRevision: String,
    normalizedPlanHash: String,
    preflightReceiptId: String,
    timeoutPolicyCode: String,
    terminalStatus: AutomationLiveTestTerminalStatus,
    checkedAtMillis: Long,
    completedAtMillis: Long,
): String = automationOpaqueVersion(
    listOf(
        "live_test_v2",
        recipeRevision,
        normalizedPlanHash,
        preflightReceiptId,
        timeoutPolicyCode,
        terminalStatus.persistedCode,
        checkedAtMillis,
        completedAtMillis,
    ).joinToString("|"),
)

private fun automationOpaqueVersion(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
