package io.codecks.data.decks

import io.codecks.core.common.CodecksClock
import io.codecks.domain.decks.DeckImportResult
import io.codecks.domain.decks.DefaultDeckFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDeckStoreTest {
    private val codec = DeckJsonCodec()
    private val clock = object : CodecksClock {
        override fun nowEpochMillis(): Long = 42L
    }

    @Test
    fun deckEntityRoundTripsThroughStrictCodec() {
        val deck = DefaultDeckFactory.mainDeck()

        val entity = deck.toEntity(codec, clock)
        val imported = entity.toDeck(codec)

        assertEquals(deck.id, entity.id)
        assertEquals(deck.name, entity.name)
        assertEquals(42L, entity.updatedAtEpochMillis)
        assertTrue(imported is DeckImportResult.Imported)
        assertEquals(deck, (imported as DeckImportResult.Imported).deck)
    }

    @Test
    fun quarantineEntityPreservesRawJsonAndReason() {
        val entity = """{"bad":true}""".toQuarantineEntity("Missing pages", clock)

        assertEquals("""{"bad":true}""", entity.rawJson)
        assertEquals("Missing pages", entity.reason)
        assertEquals(42L, entity.createdAtEpochMillis)
    }
}
