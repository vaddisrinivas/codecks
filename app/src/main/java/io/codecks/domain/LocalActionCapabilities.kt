package io.codecks.domain

import io.codecks.domain.smart.SmartActionKind
import io.codecks.domain.smart.SmartCapability

private val macInputRoutes = setOf(
    "hid_media_play_pause",
    "hid_media_next",
    "hid_media_previous",
)

fun DeckAction.smartRequiredCapabilities(): Set<SmartCapability> =
    when (smartActionKind()) {
        SmartActionKind.MacCommand -> setOf(SmartCapability.MacCommand)
        SmartActionKind.MacInput -> setOf(
            SmartCapability.LocalNavigation,
            SmartCapability.MacInput,
        )
        SmartActionKind.LocalNavigation -> when (route) {
            "keyboard", "text" -> setOf(
                SmartCapability.LocalNavigation,
                SmartCapability.Keyboard,
            )
            "clipboard" -> setOf(
                SmartCapability.LocalNavigation,
                SmartCapability.Clipboard,
            )
            "settings", "setup_scan" -> setOf(
                SmartCapability.LocalNavigation,
                SmartCapability.ConnectionRepair,
            )
            else -> setOf(SmartCapability.LocalNavigation)
        }
    }

fun DeckAction.smartActionKind(): SmartActionKind =
    when {
        kind == ActionKind.Ssh -> SmartActionKind.MacCommand
        kind == ActionKind.Local && route in macInputRoutes -> SmartActionKind.MacInput
        else -> SmartActionKind.LocalNavigation
    }
