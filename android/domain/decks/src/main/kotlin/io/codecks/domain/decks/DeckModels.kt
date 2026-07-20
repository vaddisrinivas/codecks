package io.codecks.domain.decks

import io.codecks.domain.targets.TargetSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class Deck(
    val id: String,
    val name: String,
    val pages: List<DeckPage>,
    val styleRef: String,
    val defaultTarget: TargetSelection,
    val version: Int = 1,
)

@Serializable
data class DeckPage(
    val id: String,
    val name: String,
    val grid: KeyGrid,
    val buttons: List<DeckButton>,
)

@Serializable
data class KeyGrid(
    val columns: Int,
    val rows: Int,
)

@Serializable
data class DeckButton(
    val id: String,
    val position: KeyPosition,
    val span: KeySpan = KeySpan(),
    val presentation: ButtonPresentation,
    val actionRef: ActionRef,
    val targetRef: TargetSelection,
    val behavior: ButtonBehavior = ButtonBehavior(),
    val styleOverride: String? = null,
)

@Serializable
data class KeyPosition(
    val column: Int,
    val row: Int,
)

@Serializable
data class KeySpan(
    val columns: Int = 1,
    val rows: Int = 1,
)

@Serializable
data class ButtonPresentation(
    val label: String,
    val glyph: String,
    val accent: String = "blue",
)

@Serializable
data class ActionRef(
    val stableId: String,
    val version: Int = 1,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class ButtonBehavior(
    val confirmDangerous: Boolean = true,
    val repeatOnHold: Boolean = false,
    val disabledReason: String? = null,
)

interface DeckRepository {
    fun observeDecks(): Flow<List<Deck>>
    suspend fun getDeck(id: String): Deck?
    suspend fun upsertDeck(deck: Deck)
    suspend fun quarantineDeck(rawJson: String, reason: String)
}

sealed interface DeckImportResult {
    data class Imported(val deck: Deck) : DeckImportResult
    data class Quarantined(val reason: String) : DeckImportResult
}
