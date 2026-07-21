package io.codecks.ui.automations

enum class AutomationCategory(val label: String) {
    Routines("Routines"),
    Workspace("Workspace"),
    Browser("Browser"),
    Media("Media"),
    System("System"),
}

data class AutomationItem(
    val id: String,
    val label: String,
    val description: String,
    val category: AutomationCategory,
    val triggerLabel: String = "Manual",
    val draftTriggerType: AutomationTriggerDraftType = AutomationTriggerDraftType.Manual,
    val draftTriggerValue: String = "",
    val draftWeekdays: Set<String> = emptySet(),
    val draftCommand: String = "",
    val dangerous: Boolean = false,
    val enabled: Boolean = true,
    val lastRunLabel: String? = null,
    val lastRunSucceeded: Boolean? = null,
    val lastTestLabel: String? = null,
    val lastTestSucceeded: Boolean? = null,
    val canEnable: Boolean = false,
    val approvalPending: Boolean = false,
    val runHistory: List<AutomationHistoryItem> = emptyList(),
) {
    val runHistoryLabels: List<String>
        get() = runHistory.map { "${it.statusLabel}: ${it.message}" }
}

data class AutomationHistoryItem(
    val timestampMillis: Long,
    val statusLabel: String,
    val message: String,
    val logs: String,
    val succeeded: Boolean,
    val needsApproval: Boolean,
)

data class AutomationsUiState(
    val automations: List<AutomationItem> = emptyList(),
    val runningActionId: String? = null,
    val connectionReady: Boolean = false,
    val triggerMonitorLabel: String = "Triggers idle",
    val message: String? = null,
    val pendingUndo: PendingAutomationUndo? = null,
)

data class PendingAutomationUndo(
    val recipeId: String,
    val title: String,
)

enum class AutomationTriggerDraftType(val label: String, val hint: String) {
    Manual("Manual", "No value needed"),
    TimeOfDay("Time", "09:00"),
    ActiveApp("App", "Safari or Chrome"),
    ClipboardContains("Clipboard", "Text to match"),
    WifiSsid("Wi-Fi", "SSID"),
    MacAwake("Mac awake", "No value needed"),
    FileChanged("File", "~/Downloads"),
    BatteryBelow("Battery", "20"),
}

data class AutomationDraftInput(
    val recipeId: String? = null,
    val title: String,
    val triggerType: AutomationTriggerDraftType,
    val triggerValue: String,
    val command: String,
    val enabled: Boolean = false,
    val weekdays: Set<String> = emptySet(),
)
