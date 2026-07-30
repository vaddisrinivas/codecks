package io.codecks.domain.automation

import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.commandRevision
import io.codecks.domain.smart.SmartCapability
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
    val pendingApproval: AutomationRunSummary? = null,
)

data class AutomationPreflightCheck(
    val area: AutomationPreflightArea,
    val passed: Boolean,
    val message: String,
)

enum class AutomationPreflightArea {
    Identity,
    Connection,
    Provider,
    Tool,
    App,
    Target,
    Permission,
    Path,
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
)

data class AutomationLiveTestAssertion(
    val stepId: String,
    val stepTitle: String,
    val passed: Boolean,
    val message: String,
)

data class AutomationLiveTestCleanup(
    val command: String,
    val passed: Boolean,
    val message: String,
)

data class AutomationLiveTestReceipt(
    val recipeRevision: String,
    val checkedAtMillis: Long,
    val preflightCheckedAtMillis: Long,
    val assertions: List<AutomationLiveTestAssertion>,
    val cleanup: AutomationLiveTestCleanup,
    val macIdentity: String,
)

fun AutomationRecipe.revisionFingerprint(): String {
    val stepsToken = steps.joinToString("|") { step ->
        when (step) {
            is ActionSpec.DeckActionSpec -> "deck:${step.id}:${step.action.command}:${step.dangerous}:${step.targetSelector}:${step.commandRevision()}:${step.review.reviewedRevision}:${step.review.checkedRevision}"
            is ActionSpec.CatalogAction -> "catalog:${step.id}:${step.dangerous}:${step.targetSelector}:${step.commandOrigin}:${step.review.reviewedRevision}:${step.review.checkedRevision}"
            is ActionSpec.ShellCommand -> "shell:${step.id}:${step.command}:${step.trustLevel}:${step.dangerous}:${step.targetSelector}:${step.commandOrigin}:${step.commandRevision()}:${step.review.reviewedRevision}:${step.review.checkedRevision}:${step.riskReason}:${step.confirmationTitle}:${step.confirmationBody}"
            is ActionSpec.LocalRoute -> "local:${step.id}:${step.route}:${step.targetSelector}:${step.commandOrigin}:${step.review.reviewedRevision}:${step.review.checkedRevision}"
        }
    }
    return sha256("${trigger}|$stepsToken|${safety.requiresConfirmation}")
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

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
    val receipt = lastLiveTest ?: return false
    if (receipt.recipeRevision != revisionFingerprint()) return false
    if (receipt.macIdentity != requiredMacIdentity) return false
    if (!hasCurrentValidPreflight(nowMillis, requiredMacIdentity, requiredPermissions)) return false
    if (nowMillis - receipt.preflightCheckedAtMillis > AUTOMATION_ENABLEMENT_TTL_MILLIS) return false
    return nowMillis - receipt.checkedAtMillis <= AUTOMATION_ENABLEMENT_TTL_MILLIS
}

fun AutomationRecipe.requiredCapabilities(): Set<SmartCapability> =
    steps.flatMap { it.requiredSmartCapabilities() }.toSet()

fun AutomationRecipe.requiredPermissions(): Set<String> = steps.flatMap { it.requiredPermissions() }.toSet()

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
