package io.codecks.data.decks

import io.codecks.domain.actions.CoreActionCatalog
import io.codecks.domain.decks.Deck
import io.codecks.domain.decks.DeckButton
import io.codecks.domain.decks.DeckImportResult
import io.codecks.domain.decks.DeckRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DeckJsonCodec(
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = false
        prettyPrint = true
    },
) {
    fun export(deck: Deck): String {
        validate(deck)
        return json.encodeToString(deck)
    }

    fun import(rawJson: String): DeckImportResult = try {
        val deck = json.decodeFromString<Deck>(rawJson)
        validate(deck)
        DeckImportResult.Imported(deck)
    } catch (error: IllegalArgumentException) {
        DeckImportResult.Quarantined(error.message ?: "Invalid deck")
    } catch (error: SerializationException) {
        DeckImportResult.Quarantined(error.message ?: "Invalid deck JSON")
    }

    fun validate(deck: Deck) {
        require(deck.version == 1) { "Unsupported deck version: ${deck.version}" }
        require(deck.id.isNotBlank()) { "Deck id required" }
        require(deck.name.isNotBlank()) { "Deck name required" }
        require(deck.pages.isNotEmpty()) { "Deck must contain at least one page" }
        require(deck.pages.map { it.id }.toSet().size == deck.pages.size) { "Duplicate page ids" }

        deck.pages.forEach { page ->
            require(page.grid.columns in 1..12) { "Grid columns out of range on ${page.id}" }
            require(page.grid.rows in 1..12) { "Grid rows out of range on ${page.id}" }
            require(page.buttons.map { it.id }.toSet().size == page.buttons.size) { "Duplicate button ids on ${page.id}" }

            val occupied = mutableSetOf<Pair<Int, Int>>()
            page.buttons.forEach { button ->
                validateButton(button)
                val columnRange = button.position.column until (button.position.column + button.span.columns)
                val rowRange = button.position.row until (button.position.row + button.span.rows)
                require(columnRange.last < page.grid.columns) { "Button ${button.id} exceeds grid columns" }
                require(rowRange.last < page.grid.rows) { "Button ${button.id} exceeds grid rows" }

                rowRange.forEach { row ->
                    columnRange.forEach { column ->
                        require(occupied.add(column to row)) { "Button ${button.id} overlaps another button" }
                    }
                }
            }
        }
    }

    private fun validateButton(button: DeckButton) {
        require(button.id.isNotBlank()) { "Button id required" }
        require(button.position.column >= 0) { "Button ${button.id} column must be non-negative" }
        require(button.position.row >= 0) { "Button ${button.id} row must be non-negative" }
        require(button.span.columns in 1..4) { "Button ${button.id} column span out of range" }
        require(button.span.rows in 1..4) { "Button ${button.id} row span out of range" }
        require(button.presentation.label.isNotBlank()) { "Button ${button.id} label required" }
        CoreActionCatalog.requireDefinition(button.actionRef.stableId)
    }
}

class InMemoryDeckRepository(
    initialDecks: List<Deck> = emptyList(),
    private val codec: DeckJsonCodec = DeckJsonCodec(),
) : DeckRepository {
    private val decks = MutableStateFlow(initialDecks.associateBy { it.id })
    private val quarantined = MutableStateFlow<List<QuarantinedDeck>>(emptyList())

    override fun observeDecks(): Flow<List<Deck>> = decks.map { it.values.sortedBy { deck -> deck.name } }

    override suspend fun getDeck(id: String): Deck? = decks.value[id]

    override suspend fun upsertDeck(deck: Deck) {
        codec.validate(deck)
        decks.value = decks.value + (deck.id to deck)
    }

    override suspend fun quarantineDeck(rawJson: String, reason: String) {
        quarantined.value = quarantined.value + QuarantinedDeck(rawJson = rawJson, reason = reason)
    }

    suspend fun importDeck(rawJson: String): DeckImportResult {
        val result = codec.import(rawJson)
        when (result) {
            is DeckImportResult.Imported -> upsertDeck(result.deck)
            is DeckImportResult.Quarantined -> quarantineDeck(rawJson, result.reason)
        }
        return result
    }

    fun observeQuarantine(): Flow<List<QuarantinedDeck>> = quarantined
}

data class QuarantinedDeck(
    val rawJson: String,
    val reason: String,
)

