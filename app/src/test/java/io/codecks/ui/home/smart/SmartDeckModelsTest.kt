package io.codecks.ui.home.smart

import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.smart.SmartActionKind
import io.codecks.domain.smart.SmartCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartDeckModelsTest {
    @Test
    fun mediaActionRefUsesCanonicalMacInputClassification() {
        val action = DeckAction(
            id = "next_track",
            label = "Next Track",
            kind = ActionKind.Local,
            icon = ActionIcon.Play,
            route = "hid_media_next",
        )

        val ref = action.toSmartActionRef()

        assertEquals(SmartActionKind.MacInput, ref.kind)
        assertEquals(
            setOf(SmartCapability.LocalNavigation, SmartCapability.MacInput),
            ref.requiredCapabilities,
        )
    }
}
