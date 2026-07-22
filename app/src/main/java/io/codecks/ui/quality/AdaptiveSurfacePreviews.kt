package io.codecks.ui.quality

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.ui.automations.AutomationCategory
import io.codecks.ui.automations.AutomationItem
import io.codecks.ui.automations.AutomationsScreen
import io.codecks.ui.automations.AutomationsUiState
import io.codecks.ui.home.HomeScreen
import io.codecks.ui.home.HomeUiState
import io.codecks.ui.theme.CodecksTheme

private const val PHONE_SPEC = "spec:width=393dp,height=873dp,dpi=440"
private const val FOLDABLE_SPEC = "spec:width=673dp,height=841dp,dpi=480"
private const val TABLET_SPEC = "spec:width=1280dp,height=800dp,dpi=240"

@Preview(name = "Deck phone", device = PHONE_SPEC, showBackground = true)
@Preview(name = "Deck foldable", device = FOLDABLE_SPEC, showBackground = true)
@Preview(name = "Deck tablet", device = TABLET_SPEC, showBackground = true)
@Composable
private fun DeckAdaptivePreview() {
    CodecksTheme {
        HomeScreen(
            state = HomeUiState(
                actions = previewDeckActions(),
                allActions = previewDeckActions(),
                activeMacApp = "Keynote",
                connectionReady = true,
            ),
            contentPadding = PaddingValues(0.dp),
            onAction = {},
        )
    }
}

@Preview(name = "Automations phone", device = PHONE_SPEC, showBackground = true)
@Preview(name = "Automations foldable", device = FOLDABLE_SPEC, showBackground = true)
@Preview(name = "Automations tablet", device = TABLET_SPEC, showBackground = true)
@Composable
private fun AutomationsAdaptivePreview() {
    CodecksTheme {
        AutomationsScreen(
            state = AutomationsUiState(
                automations = previewAutomations(),
                connectionReady = true,
                triggerMonitorLabel = "3 triggers active",
            ),
            contentPadding = PaddingValues(0.dp),
            onRunAutomation = {},
            onCreateWithAi = {},
        )
    }
}

private fun previewDeckActions(): List<DeckAction> =
    listOf(
        DeckAction("open_arc", "Open Arc", ActionKind.Ssh, ActionIcon.Browser, "Launch Arc"),
        DeckAction("mute", "Mute", ActionKind.Ssh, ActionIcon.Volume, "Mute current tab"),
        DeckAction("present", "Present", ActionKind.Ssh, ActionIcon.Play, "Start presentation"),
        DeckAction("trackpad", "Trackpad", ActionKind.Local, ActionIcon.Mouse, "Open trackpad", route = "mouse"),
        DeckAction("add_button", "Add", ActionKind.Local, ActionIcon.Add, "Add a control"),
    )

private fun previewAutomations(): List<AutomationItem> =
    listOf(
        AutomationItem(
            id = "morning",
            label = "Morning setup",
            description = "Open calendar, browser, and music",
            category = AutomationCategory.Routines,
            triggerLabel = "Weekdays 09:00",
            lastRunLabel = "Last OK today",
            lastRunSucceeded = true,
        ),
        AutomationItem(
            id = "focus",
            label = "Focus mode",
            description = "Silence notifications and open notes",
            category = AutomationCategory.Workspace,
            triggerLabel = "When Mac wakes",
            lastRunLabel = "Never run",
        ),
        AutomationItem(
            id = "music",
            label = "Media cleanup",
            description = "Pause media when meetings start",
            category = AutomationCategory.Media,
            triggerLabel = "Active app: Zoom",
            enabled = false,
        ),
    )
