package io.codecks.domain.automation

import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec

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
    val pendingApproval: AutomationRunSummary? = null,
)

fun AutomationRecipe.revisionFingerprint(): String {
    val stepsToken = steps.joinToString("|") { step ->
        when (step) {
            is ActionSpec.DeckActionSpec -> "deck:${step.id}:${step.action.command}:${step.dangerous}:${step.targetSelector}"
            is ActionSpec.CatalogAction -> "catalog:${step.id}:${step.dangerous}:${step.targetSelector}"
            is ActionSpec.ShellCommand -> "shell:${step.id}:${step.command}:${step.trustLevel}:${step.dangerous}:${step.targetSelector}"
            is ActionSpec.LocalRoute -> "local:${step.id}:${step.route}:${step.targetSelector}"
        }
    }
    return "${trigger}|$stepsToken|${safety.requiresConfirmation}".hashCode().toUInt().toString(16)
}

fun AutomationRecipe.hasCurrentSuccessfulTest(): Boolean =
    lastTest?.status == ActionResultStatus.Succeeded && lastTestRevision == revisionFingerprint()

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
