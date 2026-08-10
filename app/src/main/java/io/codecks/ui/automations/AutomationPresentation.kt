package io.codecks.ui.automations

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.RawCommandPolicy
import io.codecks.core.actions.ShellTrustLevel
import io.codecks.core.actions.commandRevision
import io.codecks.data.automation.AutomationScheduler
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.ai.AutomationDraft
import io.codecks.domain.automation.AutomationCatalog
import io.codecks.domain.automation.AutomationGroup
import io.codecks.domain.automation.AutomationLiveTestReceipt
import io.codecks.domain.automation.AutomationPreflightArea
import io.codecks.domain.automation.AutomationPreflightCheck
import io.codecks.domain.automation.AutomationPreflightReceipt
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationRunSummary
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.automationConnectionIdentity as opaqueAutomationConnectionIdentity
import io.codecks.domain.automation.label
import io.codecks.domain.automation.revisionFingerprint
import io.codecks.domain.automation.hasCurrentValidLiveTest
import io.codecks.domain.automation.requiredPermissions
import io.codecks.domain.connection.CapabilityStatus
import kotlinx.coroutines.flow.first

internal fun typedPreflightCheck(
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

internal object NoopAutomationScheduler : AutomationScheduler {
    override fun start() = Unit
    override fun stop() = Unit
}

internal fun ActionSpec.validationError(): String? = when (this) {
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

internal fun reviewedUserShellCommand(
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

internal fun AutomationRecipe.requiredCommandPaths(): Set<String> =
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

internal fun AutomationRecipe.requiredApplications(): Set<String> =
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

internal fun AutomationRecipe.toUiItem(
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

internal fun automationConnectionIdentity(config: io.codecks.data.ConnectionConfig): String =
    opaqueAutomationConnectionIdentity(
        host = config.host,
        port = config.port,
        user = config.user,
        hostKey = config.hostKey,
    )

internal fun AutomationPreflightReceipt.toLabel(): String =
    when {
        checks.all { it.passed } -> "Preflight passed"
        else -> "Preflight failed"
    }

internal fun AutomationLiveTestReceipt.toLabel(): String {
    val allPassed = assertions.all { it.passed } && cleanup.passed
    return if (allPassed) "Live test passed" else "Live test failed"
}

internal fun AutomationRunSummary.toLabel(): String =
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

internal fun AutomationDraftInput.toTrigger(): Result<AutomationTrigger> = runCatching {
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

internal fun String.slug(): String =
    lowercase()
        .map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "automation" }
