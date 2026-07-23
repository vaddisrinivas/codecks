package io.codecks.domain

import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.smart.SmartActionKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalActionCapabilitiesTest {
    @Test
    fun sshActionRequiresMacCommandCapability() {
        val action = DeckAction(
            id = "finder",
            label = "Finder",
            kind = ActionKind.Ssh,
            icon = ActionIcon.Finder,
            command = "open -a Finder",
            commandOrigin = CommandOrigin.Bundled,
        )

        assertEquals(
            setOf(SmartCapability.MacCommand),
            action.smartRequiredCapabilities(),
        )
    }

    @Test
    fun mediaActionsRequireMacInputCapability() {
        listOf(
            "hid_media_play_pause",
            "hid_media_next",
            "hid_media_previous",
        ).forEach { route ->
            val action = DeckAction(
                id = route,
                label = route,
                kind = ActionKind.Local,
                icon = ActionIcon.Control,
                route = route,
                command = "noop",
                commandOrigin = CommandOrigin.Bundled,
            )

            assertEquals(
                setOf(SmartCapability.LocalNavigation, SmartCapability.MacInput),
                action.smartRequiredCapabilities(),
            )
            assertEquals(SmartActionKind.MacInput, action.smartActionKind())
        }
    }

    @Test
    fun localNavigationAndSshUseTheirCanonicalKinds() {
        val local = DeckAction(
            id = "trackpad",
            label = "Trackpad",
            kind = ActionKind.Local,
            icon = ActionIcon.Mouse,
            route = "trackpad",
        )
        val ssh = DeckAction(
            id = "finder",
            label = "Finder",
            kind = ActionKind.Ssh,
            icon = ActionIcon.Finder,
            command = "open -a Finder",
        )

        assertEquals(SmartActionKind.LocalNavigation, local.smartActionKind())
        assertEquals(SmartActionKind.MacCommand, ssh.smartActionKind())
    }

    @Test
    fun keyboardAndTextRequireKeyboardCapability() {
        val action = DeckAction(
            id = "keyboard",
            label = "Keyboard",
            kind = ActionKind.Local,
            icon = ActionIcon.Control,
            route = "keyboard",
            command = "noop",
            commandOrigin = CommandOrigin.Bundled,
        )

        assertEquals(
            setOf(SmartCapability.LocalNavigation, SmartCapability.Keyboard),
            action.smartRequiredCapabilities(),
        )
    }
}
