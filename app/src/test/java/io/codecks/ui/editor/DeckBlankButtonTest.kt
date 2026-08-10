package io.codecks.ui.editor

import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.ui.icons.hasVisibleDeckIcon
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckBlankButtonTest {
    @Test
    fun blankButtonPreservesChosenSemanticIconAndCustomColor() {
        val action = customDecorAction(3, "Focus zone", "#123ABC", ActionIcon.Lock)
        assertEquals("Focus zone", action.label)
        assertEquals("#123ABC", action.colorHex)
        assertEquals(ActionIcon.Lock, action.icon)
        assertTrue(action.liveSafe)
        assertEquals("decor", action.route)
    }

    @Test
    fun colorParserRejectsMalformedValues() {
        assertNull("#12345".toComposeColorOrNull())
        assertNull("not-color".toComposeColorOrNull())
        assertTrue("#123ABC".toComposeColorOrNull() != null)
    }

    @Test
    fun onlyDecorEmptySuppressesGlyphWhileOtherEmptyActionsKeepFallback() {
        val decor = customDecorAction(0, "", "#123ABC", ActionIcon.Empty)
        val nonDecor = DeckAction("empty_fallback", "Fallback", ActionKind.Local, ActionIcon.Empty, route = "settings")

        assertFalse(decor.hasVisibleDeckIcon())
        assertTrue(nonDecor.hasVisibleDeckIcon())
    }
}
