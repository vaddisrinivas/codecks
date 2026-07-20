package io.codecks.data.decks

import io.codecks.domain.decks.DeckImportResult
import io.codecks.domain.decks.DefaultDeckFactory
import io.codecks.domain.decks.KeySpan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckJsonCodecTest {
    private val codec = DeckJsonCodec()
    private val rawJson = Json { encodeDefaults = true }

    @Test
    fun `default deck exports and imports strictly`() {
        val deck = DefaultDeckFactory.mainDeck()
        val exported = codec.export(deck)

        val result = codec.import(exported)

        assertTrue(result is DeckImportResult.Imported)
        assertEquals(deck, (result as DeckImportResult.Imported).deck)
    }

    @Test
    fun `overlapping buttons are quarantined`() {
        val deck = DefaultDeckFactory.mainDeck()
        val firstPage = deck.pages.single()
        val overlapping = deck.copy(
            pages = listOf(
                firstPage.copy(
                    buttons = firstPage.buttons.mapIndexed { index, button ->
                        if (index == 1) {
                            button.copy(position = firstPage.buttons.first().position)
                        } else {
                            button
                        }
                    },
                ),
            ),
        )

        val quarantine = codec.import(rawJson.encodeToString(overlapping))
        assertTrue(quarantine is DeckImportResult.Quarantined)
    }

    @Test
    fun `out of bounds span is quarantined`() {
        val deck = DefaultDeckFactory.mainDeck()
        val firstPage = deck.pages.single()
        val invalid = deck.copy(
            pages = listOf(
                firstPage.copy(
                    buttons = firstPage.buttons.mapIndexed { index, button ->
                        if (index == 0) button.copy(span = KeySpan(columns = 5)) else button
                    },
                ),
            ),
        )

        val result = codec.import(rawJson.encodeToString(invalid))

        assertTrue(result is DeckImportResult.Quarantined)
    }

    @Test
    fun `repository imports valid deck and quarantines invalid json`() = runTest {
        val repository = InMemoryDeckRepository()
        val validJson = codec.export(DefaultDeckFactory.mainDeck())

        val imported = repository.importDeck(validJson)
        val quarantined = repository.importDeck("""{"version": 2}""")

        assertTrue(imported is DeckImportResult.Imported)
        assertTrue(quarantined is DeckImportResult.Quarantined)
        assertEquals(1, repository.observeDecks().first().size)
        assertEquals(1, repository.observeQuarantine().first().size)
    }
}
