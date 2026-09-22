package io.codecks.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface AppRoute : NavKey

@Serializable
data object HomeRoute : AppRoute

@Serializable
data object MouseRoute : AppRoute

@Serializable
data object KeyboardRoute : AppRoute

@Serializable
data object ClipboardRoute : AppRoute

@Serializable
data object AutomationsRoute : AppRoute

@Serializable
data object SettingsRoute : AppRoute

@Serializable
data object EditorRoute : AppRoute

@Serializable
data object AiBuilderRoute : AppRoute

@Serializable
data object AiProviderRoute : AppRoute

@Serializable
data object RunLogRoute : AppRoute

@Serializable
data object CommandPaletteRoute : AppRoute
