package io.codex.s23deck.domain.automation

import io.codex.s23deck.core.actions.ActionSpec
import io.codex.s23deck.domain.DeckAction

object AutomationCatalog {
    val focusedActionIds = listOf(
        "coding_start",
        "meeting_start",
        "focus_1h",
        "stop_caffeine",
        "browser_deck",
    )

    fun groupFor(actionId: String): AutomationGroup = when (actionId) {
        "coding_start", "meeting_start" -> AutomationGroup.Routines
        "browser_deck" -> AutomationGroup.Browser
        "focus_1h", "stop_caffeine" -> AutomationGroup.System
        else -> AutomationGroup.Workspace
    }

    fun defaultRecipes(actionsById: Map<String, DeckAction>): List<AutomationRecipe> =
        listOfNotNull(
            defaultRecipe(
                id = "time_focus_0900",
                actionId = "focus_1h",
                title = "Morning Focus",
                description = "Start a one-hour focus block on weekday mornings.",
                trigger = AutomationTrigger.TimeOfDay(9, 0, setOf("Mon", "Tue", "Wed", "Thu", "Fri")),
                actionsById = actionsById,
            ),
            defaultRecipe(
                id = "app_browser_deck",
                actionId = "browser_deck",
                title = "Browser Deck",
                description = "Switch to browser controls when Safari, Chrome, Arc, or Firefox is frontmost.",
                trigger = AutomationTrigger.ActiveApp("Browser"),
                actionsById = actionsById,
            ),
            defaultRecipe(
                id = "clipboard_link_capture",
                actionId = "chatgpt",
                title = "Clipboard Link Helper",
                description = "Prepare AI help when the clipboard contains a web link.",
                trigger = AutomationTrigger.ClipboardContains("http"),
                actionsById = actionsById,
            ),
            defaultRecipe(
                id = "wifi_work_setup",
                actionId = "coding_start",
                title = "Work Wi-Fi Setup",
                description = "Open the coding workspace when joining a trusted work Wi-Fi.",
                trigger = AutomationTrigger.WifiSsid("Work"),
                actionsById = actionsById,
            ),
            defaultRecipe(
                id = "mac_awake_restore",
                actionId = "finder",
                title = "Mac Awake Restore",
                description = "Bring Finder forward after the Mac wakes.",
                trigger = AutomationTrigger.MacAwake,
                actionsById = actionsById,
            ),
            defaultRecipe(
                id = "downloads_changed",
                actionId = "downloads",
                title = "Downloads Changed",
                description = "Open Downloads when new files arrive.",
                trigger = AutomationTrigger.FileChanged("~/Downloads"),
                actionsById = actionsById,
            ),
            defaultRecipe(
                id = "battery_low_display",
                actionId = "sleep_display",
                title = "Battery Saver",
                description = "Sleep the display when battery drops low.",
                trigger = AutomationTrigger.BatteryBelow(20),
                actionsById = actionsById,
            ),
        )
}

private fun defaultRecipe(
    id: String,
    actionId: String,
    title: String,
    description: String,
    trigger: AutomationTrigger,
    actionsById: Map<String, DeckAction>,
): AutomationRecipe? {
    val action = actionsById[actionId] ?: return null
    return AutomationRecipe(
        id = id,
        title = title,
        description = description,
        enabled = false,
        trigger = trigger,
        steps = listOf(ActionSpec.DeckActionSpec(action)),
        safety = AutomationSafety(requiresConfirmation = action.dangerous),
    )
}

enum class AutomationGroup {
    Routines,
    Workspace,
    Browser,
    Media,
    System,
}
