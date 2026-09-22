package io.codecks.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavKey
import io.codecks.domain.DeckAction
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.icons.deckImageVectorOrNull

internal fun quickDeckActions(actions: List<DeckAction>): List<DeckAction> = actions
    .filterNot { it.id == "blank" || it.id == "add_button" }
    .distinctBy(DeckAction::id)
    .take(8)

internal fun groupedNavigationDestinations(
    destinations: List<RouteDescriptor<out io.codecks.navigation.AppRoute>>,
): List<Pair<RouteGroup, List<RouteDescriptor<out io.codecks.navigation.AppRoute>>>> =
    RouteGroup.entries.mapNotNull { group ->
        destinations.filter { it.group == group }.takeIf { it.isNotEmpty() }?.let { group to it }
    }

@Composable
internal fun CodecksNavigationDrawerContent(
    currentRoute: NavKey,
    destinations: List<RouteDescriptor<out io.codecks.navigation.AppRoute>>,
    actions: List<DeckAction>,
    selectedActionId: String?,
    actionRunning: Boolean,
    onDestinationSelected: (NavKey) -> Unit,
    onAction: (DeckAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalDrawerSheet {
        CodecksNavigationDrawerBody(
            currentRoute = currentRoute,
            destinations = destinations,
            actions = actions,
            selectedActionId = selectedActionId,
            actionRunning = actionRunning,
            onDestinationSelected = onDestinationSelected,
            onAction = onAction,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun CodecksPermanentNavigationDrawerContent(
    currentRoute: NavKey,
    destinations: List<RouteDescriptor<out io.codecks.navigation.AppRoute>>,
    actions: List<DeckAction>,
    selectedActionId: String?,
    actionRunning: Boolean,
    onDestinationSelected: (NavKey) -> Unit,
    onAction: (DeckAction) -> Unit,
    onDismiss: () -> Unit,
) {
    PermanentDrawerSheet {
        CodecksNavigationDrawerBody(
            currentRoute = currentRoute,
            destinations = destinations,
            actions = actions,
            selectedActionId = selectedActionId,
            actionRunning = actionRunning,
            onDestinationSelected = onDestinationSelected,
            onAction = onAction,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun CodecksNavigationDrawerBody(
    currentRoute: NavKey,
    destinations: List<RouteDescriptor<out io.codecks.navigation.AppRoute>>,
    actions: List<DeckAction>,
    selectedActionId: String?,
    actionRunning: Boolean,
    onDestinationSelected: (NavKey) -> Unit,
    onAction: (DeckAction) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Escape || event.key == Key.Back)
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
    ) {
            Text(
                "Quick Deck",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            val quickActions = quickDeckActions(actions)
            if (quickActions.isEmpty()) {
                Text(
                    "No active Deck actions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickActions.forEach { action ->
                        DeckActionButton(
                            label = action.label,
                            icon = action.deckImageVectorOrNull(),
                            enabled = !actionRunning,
                            selected = selectedActionId == action.id,
                            onClick = {
                                onAction(action)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("quick-deck-action-${action.id}"),
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                "App navigation",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            groupedNavigationDestinations(destinations).forEach { (group, groupDestinations) ->
                Text(
                    group.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                groupDestinations.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            onDestinationSelected(destination.route)
                            onDismiss()
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp)
                            .testTag("drawer-${destination.testTag}"),
                    )
                }
            }
        }
    }
