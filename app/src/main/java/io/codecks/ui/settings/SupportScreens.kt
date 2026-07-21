package io.codecks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.designsystem.DeckPage
import io.codecks.ui.theme.DeckBridgeThemeMode
import io.codecks.ui.theme.DeckBridgeThemeSettings

@Composable
fun WidgetScreen(contentPadding: PaddingValues, onAddWidget: () -> Unit) {
    DeckPage(
        contentPadding = contentPadding,
    ) {
        item {
            SettingsPanel {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    headlineContent = { Text("Home screen controls") },
                    supportingContent = { Text("Pin quick entry to Codecks controls from the launcher.") },
                    leadingContent = { Icon(Icons.Outlined.Widgets, contentDescription = null) },
                )
            }
        }
        item {
            DeckActionButton(
                label = "Add widget",
                onClick = onAddWidget,
                icon = Icons.Outlined.Widgets,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            )
        }
    }
}

@Composable
fun AppearanceScreen(
    contentPadding: PaddingValues,
    themeMode: DeckBridgeThemeMode,
    onThemeModeChange: (DeckBridgeThemeMode) -> Unit,
) {
    DeckPage(contentPadding = contentPadding) {
        item {
            ThemeModePanel(
                themeSettings = DeckBridgeThemeSettings(mode = themeMode),
                onThemeModeChange = onThemeModeChange,
            )
        }
        item {
            SettingsPanel {
                AppearanceRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "System theme",
                    body = "Follows Android light and dark mode.",
                    value = "System",
                )
                AppearanceRow(
                    icon = Icons.Outlined.ColorLens,
                    title = "Codecks OLED palette",
                    body = "Uses one consistent dark, edge-lit system across every page.",
                    value = "On",
                )
                AppearanceRow(
                    icon = Icons.Outlined.GridView,
                    title = "Control density",
                    body = "Large controls for Deck and Trackpad, compact rows for settings.",
                    value = "Adaptive",
                )
                AppearanceRow(
                    icon = Icons.Outlined.TouchApp,
                    title = "Press feedback",
                    body = "Selected and pressed states use borders, ripple, and Material state colors.",
                    value = "On",
                )
            }
        }
    }
}

@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
private fun AppearanceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    value: String,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}
