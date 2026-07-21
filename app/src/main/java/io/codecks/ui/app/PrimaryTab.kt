package io.codecks.ui.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import io.codecks.navigation.AiBuilderRoute
import io.codecks.navigation.AutomationsRoute
import io.codecks.navigation.ClipboardRoute
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.KeyboardRoute
import io.codecks.navigation.MouseRoute
import io.codecks.navigation.SettingsRoute

enum class PrimaryTab(
    val route: NavKey,
    val label: String,
    val icon: ImageVector,
) {
    Deck(HomeRoute, "Deck", Icons.Outlined.GridView),
    Trackpad(MouseRoute, "Trackpad", Icons.Outlined.Mouse),
    Keyboard(KeyboardRoute, "Keyboard", Icons.Outlined.Keyboard),
    Clipboard(ClipboardRoute, "Clipboard", Icons.Outlined.ContentPaste),
    Automations(AutomationsRoute, "Rules", Icons.Outlined.Bolt),
    Ai(AiBuilderRoute, "AI", Icons.Outlined.AutoAwesome),
    Settings(SettingsRoute, "Settings", Icons.Outlined.Settings),
}
