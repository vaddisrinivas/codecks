package io.codecks.feature.deck

import io.codecks.domain.decks.DefaultDeckFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckScreenMappingTest {
    @Test
    fun `default deck maps to unique lazy grid keys`() {
        val deck = DefaultDeckFactory.mainDeck().toDeckUiModel()

        val ids = deck.keys.map { it.id }

        assertEquals(ids.distinct(), ids)
        assertTrue(deck.keys.any { it.id == "button-trackpad" && it.actionId == "android.trackpad.open" })
        assertTrue(deck.keys.any { it.id == "button-keyboard" && it.actionId == "android.keyboard.open" })
    }

    @Test
    fun `local deck actions route to local task screens`() {
        assertEquals(
            DeckKeyRoute.TRACKPAD,
            DeckKeyUiModel(id = "button-trackpad", title = "Trackpad", actionId = "android.trackpad.open").route(),
        )
        assertEquals(
            DeckKeyRoute.KEYBOARD,
            DeckKeyUiModel(id = "button-keyboard", title = "Keys", actionId = "android.keyboard.open").route(),
        )
        assertEquals(DeckKeyRoute.TRACKPAD, DeckKeyUiModel(id = "trackpad", title = "Trackpad").route())
        assertEquals(DeckKeyRoute.KEYBOARD, DeckKeyUiModel(id = "keyboard", title = "Keyboard").route())
        assertEquals(
            DeckKeyRoute.ACTION,
            DeckKeyUiModel(id = "button-finder", title = "Finder", actionId = "mac.finder.open").route(),
        )
    }
}
