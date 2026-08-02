package io.codecks.domain.automation

import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.commandRevision
import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.connection.CapabilityCheck
import io.codecks.domain.connection.CapabilityStatus
import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.domain.connection.RemediationAction
import java.security.MessageDigest

data class AutomationRecipe(
    val id: String,
    val title: String,
    val description: String,
    val enabled: Boolean = true,
    val trigger: AutomationTrigger = AutomationTrigger.Manual,
    val steps: List<ActionSpec>,
    val safety: AutomationSafety = AutomationSafety(),
    val lastRun: AutomationRunSummary? = null,
    val runHistory: List<AutomationRunSummary> = emptyList(),
    val lastTest: AutomationRunSummary? = null,
    val lastTestRevision: String? = null,
    val lastPreflight: AutomationPreflightReceipt? = null,
    val lastLiveTest: AutomationLiveTestReceipt? = null,
    val cleanupDefinition: AutomationCleanupDefinition = AutomationCleanupDefinition(),
    val recoveryRequired: Boolean = false,
    val lastWorkerOutcome: AutomationWorkerOutcome? = null,
    val pendingApproval: AutomationRunSummary? = null,
    val stage: AutomationStage = AutomationStage.DRAFT,
    val gateStamp: AutomationGateStamp? = null,
)

data class AutomationPreflightCheck(
    val area: AutomationPreflightArea,
    val passed: Boolean,
    val message: String,
    val capability: CapabilityCheck = area.defaultCapability(passed),
    val mandatory: Boolean = true,
) {
    init {
        require(passed == (capability.status == CapabilityStatus.SATISFIED)) {
            "Legacy pass flag must match typed capability status."
        }
    }

    companion object {
        fun typed(
            area: AutomationPreflightArea,
            capabilityCode: String,
            status: CapabilityStatus,
            issueCode: ConnectionIssueCode?,
            remediation: RemediationAction?,
            checkedAtEpochMs: Long,
            message: String,
            mandatory: Boolean = true,
        ): AutomationPreflightCheck =
            AutomationPreflightCheck(
                area = area,
                passed = status == CapabilityStatus.SATISFIED,
                message = message,
                capability = CapabilityCheck(
                    capabilityCode = capabilityCode,
                    status = status,
                    issueCode = issueCode,
                    remediation = remediation,
                    checkedAtEpochMs = checkedAtEpochMs,
                    validUntilEpochMs = null,
                ),
                mandatory = mandatory,
            )
    }
}

enum class AutomationPreflightArea {
    Identity,
    Connection,
    Provider,
    Tool,
    App,
    Target,
    Permission,
    Path,
    ActionContract,
    Schedule,
}

data class AutomationPreflightReceipt(
    val recipeRevision: String,
    val checkedAtMillis: Long,
    val macIdentity: String,
    val targetId: String,
    val requiredCapabilities: Set<SmartCapability>,
    val checks: List<AutomationPreflightCheck>,
    val commandTools: Set<String>,
    val commandPaths: Set<String>,
    val permissionSnapshot: Set<String>,
    val receiptId: String = automationReceiptId("preflight", recipeRevision, checkedAtMillis),
)

data class AutomationLiveTestAssertion(
    val stepId: String,
    val stepTitle: String,
    val passed: Boolean,
    val message: String,
    val assertionId: String = "",
    val actionRevision: String = "",
    val outcomeCode: AutomationLiveTestOutcomeCode = AutomationLiveTestOutcomeCode.UNKNOWN,
    val terminalStatus: AutomationStepTerminalStatus = AutomationStepTerminalStatus.NOT_STARTED,
    val ordinal: Int = -1,
)

data class AutomationLiveTestCleanup(
    val command: String,
    val passed: Boolean,
    val message: String,
    val outcomeCode: AutomationLiveTestCleanupCode = AutomationLiveTestCleanupCode.UNKNOWN,
    val cleanupId: String = "",
    val actionRevision: String = "",
    val undoGuarantee: AutomationUndoGuarantee = AutomationUndoGuarantee.NONE,
)

data class AutomationLiveTestReceipt(
    val recipeRevision: String,
    val checkedAtMillis: Long,
    val preflightCheckedAtMillis: Long,
    val assertions: List<AutomationLiveTestAssertion>,
    val cleanup: AutomationLiveTestCleanup,
    val macIdentity: String = "",
    val normalizedPlanHash: String = "",
    val preflightReceiptId: String = "",
    val timeoutPolicyCode: String = "",
    val terminalStatus: AutomationLiveTestTerminalStatus = AutomationLiveTestTerminalStatus.UNKNOWN,
    val recoveryRequired: Boolean = false,
    val completedAtMillis: Long = checkedAtMillis,
    val receiptId: String = automationLiveTestReceiptId(
        recipeRevision = recipeRevision,
        normalizedPlanHash = normalizedPlanHash,
        preflightReceiptId = preflightReceiptId,
        timeoutPolicyCode = timeoutPolicyCode,
        terminalStatus = terminalStatus,
        checkedAtMillis = checkedAtMillis,
        completedAtMillis = completedAtMillis,
    ),
)

enum class AutomationLiveTestOutcomeCode(val persistedCode: String) {
    EXIT_CODE_ZERO("exit_code_zero"),
    NON_ZERO_EXIT("non_zero_exit"),
    INTERRUPTED("interrupted"),
    NOT_RUN("not_run"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): AutomationLiveTestOutcomeCode =
            entries.firstOrNull { it.persistedCode == value } ?: UNKNOWN
    }
}

enum class AutomationStepTerminalStatus(val persistedCode: String) {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    TIMED_OUT("timed_out"),
    CANCELLED("cancelled"),
    INTERRUPTED("interrupted"),
    SKIPPED_DEPENDENCY("skipped_dependency"),
    NOT_STARTED("not_started"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): AutomationStepTerminalStatus =
            entries.firstOrNull { it.persistedCode == value } ?: NOT_STARTED
    }
}

enum class AutomationLiveTestCleanupCode(val persistedCode: String) {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    INTERRUPTED("interrupted"),
    NOT_REQUIRED("not_required"),
    SKIPPED("skipped"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): AutomationLiveTestCleanupCode =
            entries.firstOrNull { it.persistedCode == value } ?: UNKNOWN
    }
}

enum class AutomationLiveTestTerminalStatus(val persistedCode: String) {
    PASSED("passed"),
    ASSERTION_FAILED("assertion_failed"),
    TIMED_OUT("timed_out"),
    CANCELLED("cancelled"),
    PROCESS_INTERRUPTED("process_interrupted"),
    STALE_REVISION("stale_revision"),
    PREFLIGHT_MISMATCH("preflight_mismatch"),
    PLAN_REJECTED("plan_rejected"),
    RECOVERY_REQUIRED("recovery_required"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): AutomationLiveTestTerminalStatus =
            entries.firstOrNull { it.persistedCode == value } ?: UNKNOWN
    }
}

enum class AutomationCleanupTrigger(val persistedCode: String) {
    SUCCESS("success"),
    FAILURE("failure"),
    TIMEOUT("timeout"),
    CANCEL("cancel"),
}

enum class AutomationUndoGuarantee(val persistedCode: String) {
    GUARANTEED("guaranteed"),
    BEST_EFFORT("best_effort"),
    NONE("none"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): AutomationUndoGuarantee =
            entries.firstOrNull { it.persistedCode == value } ?: NONE
    }
}

data class AutomationCleanupDefinition(
    val action: ActionSpec.ShellCommand? = null,
    val runAfter: Set<AutomationCleanupTrigger> = emptySet(),
    val undoGuarantee: AutomationUndoGuarantee = AutomationUndoGuarantee.NONE,
) {
    init {
        require(action != null || runAfter.isEmpty()) {
            "Cleanup triggers require a cleanup action."
        }
        require(action != null || undoGuarantee == AutomationUndoGuarantee.NONE) {
            "Undo guarantee requires a cleanup action."
        }
        require(action == null || runAfter.isNotEmpty()) {
            "Cleanup action requires at least one terminal trigger."
        }
        require(
            undoGuarantee != AutomationUndoGuarantee.GUARANTEED ||
                runAfter.containsAll(AutomationCleanupTrigger.entries),
        ) {
            "Guaranteed undo must run after success, failure, timeout, and cancellation."
        }
    }
}

fun AutomationRecipe.revisionFingerprint(): String {
    val stepsToken = steps.joinToString("|") { step ->
        when (step) {
            is ActionSpec.DeckActionSpec -> "deck:${step.id}:${step.action.command}:${step.dangerous}:${step.targetSelector}:${step.commandRevision()}:${step.review.reviewedRevision}:${step.review.checkedRevision}"
            is ActionSpec.CatalogAction -> "catalog:${step.id}:${step.dangerous}:${step.targetSelector}:${step.commandOrigin}:${step.review.reviewedRevision}:${step.review.checkedRevision}"
            is ActionSpec.ShellCommand -> "shell:${step.id}:${step.command}:${step.trustLevel}:${step.dangerous}:${step.targetSelector}:${step.commandOrigin}:${step.commandRevision()}:${step.review.reviewedRevision}:${step.review.checkedRevision}:${step.riskReason}:${step.confirmationTitle}:${step.confirmationBody}"
            is ActionSpec.LocalRoute -> "local:${step.id}:${step.route}:${step.targetSelector}:${step.commandOrigin}:${step.review.reviewedRevision}:${step.review.checkedRevision}"
        }
    }
    val cleanupToken = cleanupDefinition.action?.let { action ->
        listOf(
            action.id,
            action.commandRevision(),
            action.review.reviewedRevision,
            action.review.checkedRevision,
            cleanupDefinition.runAfter.map(AutomationCleanupTrigger::persistedCode).sorted().joinToString(","),
            cleanupDefinition.undoGuarantee.persistedCode,
        ).joinToString(":")
    }.orEmpty()
    return sha256("${trigger}|$stepsToken|${safety.requiresConfirmation}|cleanup=$cleanupToken")
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private const val AUTOMATION_CONNECTION_IDENTITY_VERSION = "connection_identity_v1"

/**
 * Stable trust binding for receipts. The raw host, user, and host key must never enter
 * automation persistence.
 */
fun automationConnectionIdentity(
    host: String,
    port: Int,
    user: String,
    hostKey: String,
): String {
    if (host.isBlank() || user.isBlank() || hostKey.isBlank() || port !in 1..65535) return ""
    return "$AUTOMATION_CONNECTION_IDENTITY_VERSION:${sha256(
        listOf(
            AUTOMATION_CONNECTION_IDENTITY_VERSION,
            host.trim(),
            port,
            user.trim(),
            hostKey.trim(),
        ).joinToString("\u0000"),
    )}"
}

fun isOpaqueAutomationConnectionIdentity(value: String): Boolean =
    value.startsWith("$AUTOMATION_CONNECTION_IDENTITY_VERSION:") &&
        value.length == AUTOMATION_CONNECTION_IDENTITY_VERSION.length + 1 + 64 &&
        value.substringAfter(':').all { it in '0'..'9' || it in 'a'..'f' }

fun AutomationRecipe.hasCurrentSuccessfulTest(): Boolean =
    lastTest?.status == ActionResultStatus.Succeeded && lastTestRevision == revisionFingerprint()

private const val AUTOMATION_ENABLEMENT_TTL_MILLIS = 24 * 60 * 60 * 1000L
private const val AUTOMATION_PREFLIGHT_TTL_MILLIS = 30 * 60 * 1000L

fun AutomationRecipe.hasCurrentValidPreflight(
    nowMillis: Long,
    requiredMacIdentity: String,
    requiredPermissions: Set<String>,
): Boolean {
    val preflight = lastPreflight ?: return false
    if (!preflight.mandatoryChecksSatisfied()) return false
    if (preflight.recipeRevision != revisionFingerprint()) return false
    if (preflight.macIdentity != requiredMacIdentity) return false
    if (!preflight.requiredCapabilities.containsAll(requiredCapabilities())) return false
    if (!preflight.permissionSnapshot.containsAll(requiredPermissions)) return false
    if (nowMillis - preflight.checkedAtMillis > AUTOMATION_PREFLIGHT_TTL_MILLIS) return false
    return true
}

fun AutomationRecipe.hasCurrentValidLiveTest(
    nowMillis: Long,
    requiredMacIdentity: String,
    requiredPermissions: Set<String>,
): Boolean {
    if (recoveryRequired) return false
    val receipt = lastLiveTest ?: return false
    if (receipt.recipeRevision != revisionFingerprint()) return false
    if (!hasCurrentValidPreflight(nowMillis, requiredMacIdentity, requiredPermissions)) return false
    val preflight = lastPreflight ?: return false
    val plan = AutomationExecutionPlanCompiler.compile(this).getOrNull() ?: return false
    if (!receipt.isValidTerminalPass(revisionFingerprint(), plan, preflight)) return false
    if (nowMillis - receipt.preflightCheckedAtMillis > AUTOMATION_ENABLEMENT_TTL_MILLIS) return false
    return nowMillis - receipt.checkedAtMillis <= AUTOMATION_ENABLEMENT_TTL_MILLIS
}

fun AutomationRecipe.requiredCapabilities(): Set<SmartCapability> =
    (steps + listOfNotNull(cleanupDefinition.action))
        .flatMap { it.requiredSmartCapabilities() }
        .toSet()

fun AutomationRecipe.requiredPermissions(): Set<String> =
    (steps + listOfNotNull(cleanupDefinition.action))
        .flatMap { it.requiredPermissions() }
        .toSet()

fun AutomationRecipe.requiredCommandTools(): Set<String> =
    AutomationExecutionPlanCompiler.compile(this).getOrNull()
        ?.let { plan ->
            (plan.actions + listOfNotNull(plan.cleanup?.action))
                .flatMap { it.requiredTools }
                .toSet()
        }
        .orEmpty()

private fun ActionSpec.requiredPermissions(): Set<String> = when (this) {
    is ActionSpec.ShellCommand -> command.requiredPermissions()
    is ActionSpec.CatalogAction -> setOf("automation.command.reviewed")
    is ActionSpec.DeckActionSpec -> this.action.command?.requiredPermissions() ?: emptySet()
    is ActionSpec.LocalRoute -> setOf("local.route")
}

private fun String.requiredPermissions(): Set<String> = lowercasedTokens().let { tokens ->
    buildSet {
        if (tokens.any { it == "osascript" }) add("permission.accessibility")
        if (tokens.any { it == "screencapture" }) add("permission.screenrecording")
        if (tokens.any { it == "tccutil" }) add("permission.tcc")
    }
}

private fun String.lowercasedTokens(): Set<String> =
    trim().lowercase().split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }.toSet()

fun automationPermissionProbeCommand(permission: String): String? = when (permission) {
    "permission.accessibility" ->
        "osascript -e 'tell application \"System Events\" to get name of first process'"
    "permission.screenrecording" ->
        "swift -e 'import CoreGraphics; exit(CGPreflightScreenCaptureAccess() ? 0 : 1)'"
    "permission.tcc" -> "command -v tccutil"
    else -> null
}

private fun ActionSpec.requiredSmartCapabilities(): Set<SmartCapability> = when (this) {
    is ActionSpec.ShellCommand,
    is ActionSpec.LocalRoute,
    is ActionSpec.CatalogAction,
    is ActionSpec.DeckActionSpec,
    -> setOf(SmartCapability.MacCommand)
}

sealed interface AutomationTrigger {
    data object Manual : AutomationTrigger
    data class AiSuggested(val prompt: String) : AutomationTrigger
    data class TimeOfDay(val hour: Int, val minute: Int, val days: Set<String> = emptySet()) : AutomationTrigger
    data class ActiveApp(val appName: String) : AutomationTrigger
    data class ClipboardContains(val text: String) : AutomationTrigger
    data class WifiSsid(val ssid: String) : AutomationTrigger
    data object MacAwake : AutomationTrigger
    data class FileChanged(val path: String) : AutomationTrigger
    data class BatteryBelow(val percent: Int) : AutomationTrigger
}

fun AutomationTrigger.label(): String =
    when (this) {
        AutomationTrigger.Manual -> "Manual"
        is AutomationTrigger.AiSuggested -> "AI suggested"
        is AutomationTrigger.TimeOfDay -> "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        is AutomationTrigger.ActiveApp -> "App: $appName"
        is AutomationTrigger.ClipboardContains -> "Clipboard contains \"$text\""
        is AutomationTrigger.WifiSsid -> "Wi-Fi: $ssid"
        AutomationTrigger.MacAwake -> "Mac awake"
        is AutomationTrigger.FileChanged -> "File: $path"
        is AutomationTrigger.BatteryBelow -> "Battery below ${percent.coerceIn(1, 100)}%"
    }

data class AutomationSafety(
    val requiresConfirmation: Boolean = false,
)

data class AutomationRunSummary(
    val status: ActionResultStatus,
    val message: String,
    val logs: String = message,
    val timestampMillis: Long = System.currentTimeMillis(),
)
