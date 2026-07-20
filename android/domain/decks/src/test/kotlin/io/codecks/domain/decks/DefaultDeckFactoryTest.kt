package io.codecks.domain.decks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeckFactoryTest {
    @Test
    fun `default deck preserves four by five topology with trackpad span`() {
        val deck = DefaultDeckFactory.mainDeck()
        val page = deck.pages.single()

        assertEquals(4, page.grid.columns)
        assertEquals(5, page.grid.rows)
        assertEquals(18, page.buttons.size)

        val trackpad = page.buttons.single { it.id == "button-trackpad" }
        assertEquals(0, trackpad.position.column)
        assertEquals(4, trackpad.position.row)
        assertEquals(3, trackpad.span.columns)

        assertTrue(page.buttons.none { it.position.column >= page.grid.columns })
        assertTrue(page.buttons.none { it.position.row >= page.grid.rows })
    }
}

