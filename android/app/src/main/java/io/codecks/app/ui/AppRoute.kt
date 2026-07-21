package io.codecks.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    val id: String
    val label: String
    val icon: ImageVector

    @Serializable
    data object Deck : AppRoute {
        override val id = "deck"
        override val label = "Deck"
        override val icon = Icons.Rounded.Dashboard
    }

    @Serializable
    data object Codex : AppRoute {
        override val id = "codex"
        override val label = "Codex"
        override val icon = Icons.Rounded.Bolt
    }

    @Serializable
    data object Trackpad : AppRoute {
        override val id = "trackpad"
        override val label = "Trackpad"
        override val icon = Icons.Rounded.TouchApp
    }

    @Serializable
    data object Keyboard : AppRoute {
        override val id = "keyboard"
        override val label = "Keyboard"
        override val icon = Icons.Rounded.Keyboard
    }

    @Serializable
    data object Automations : AppRoute {
        override val id = "automations"
        override val label = "Automations"
        override val icon = Icons.Rounded.AutoAwesome
    }

    @Serializable
    data object Settings : AppRoute {
        override val id = "settings"
        override val label = "Settings"
        override val icon = Icons.Rounded.Settings
    }

    companion object {
        val topLevel = listOf(Deck, Codex, Trackpad, Automations, Settings)
        val all = topLevel + Keyboard

        fun fromId(id: String): AppRoute = all.firstOrNull { it.id == id } ?: Deck

        fun keyboardReturnTarget(originId: String): AppRoute =
            fromId(originId).takeUnless { it == Keyboard } ?: Deck
    }
}
