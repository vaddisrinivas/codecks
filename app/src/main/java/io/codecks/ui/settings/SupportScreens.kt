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
import io.codecks.ui.theme.CodecksThemeMode
import io.codecks.ui.theme.CodecksThemeSettings

@Composable
fun AppearanceScreen(
    contentPadding: PaddingValues,
    themeMode: CodecksThemeMode,
    onThemeModeChange: (CodecksThemeMode) -> Unit,
) {
    DeckPage(contentPadding = contentPadding) {
        item {
            ThemeModePanel(
                themeSettings = CodecksThemeSettings(mode = themeMode),
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
