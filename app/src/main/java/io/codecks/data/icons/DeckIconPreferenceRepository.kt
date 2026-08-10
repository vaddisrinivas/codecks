package io.codecks.data.icons

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.codecks.domain.icons.IconPreferenceState
import io.codecks.domain.icons.SemanticIconId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.deckIconDataStore by preferencesDataStore(name = "deck_icon_preferences")

class DeckIconPreferenceRepository(context: Context) {
    private val appContext = context.applicationContext

    val state: Flow<IconPreferenceState> = appContext.deckIconDataStore.data.map { preferences ->
        DeckIconPreferenceCodec.decode(preferences[STATE].orEmpty())
    }

    suspend fun toggleFavorite(id: SemanticIconId) = mutate { it.toggleFavorite(id) }

    suspend fun recordRecent(id: SemanticIconId) = mutate { it.recordRecent(id) }

    private suspend fun mutate(transform: (IconPreferenceState) -> IconPreferenceState) {
        appContext.deckIconDataStore.edit { preferences ->
            val current = DeckIconPreferenceCodec.decode(preferences[STATE].orEmpty())
            preferences[STATE] = DeckIconPreferenceCodec.encode(transform(current))
        }
    }

    private companion object { val STATE = stringPreferencesKey("state_v1") }
}

internal object DeckIconPreferenceCodec {
    private const val VERSION = "v1"

    fun encode(state: IconPreferenceState): String = buildString {
        appendLine(VERSION)
        appendLine(state.favorites.joinToString(",") { it.value })
        append(state.recents.joinToString(",") { it.value })
    }.take(MAX_BYTES)

    fun decode(raw: String): IconPreferenceState {
        if (raw.length > MAX_BYTES) return IconPreferenceState()
        val lines = raw.lines()
        if (lines.firstOrNull() != VERSION) return IconPreferenceState()
        return runCatching {
            IconPreferenceState(
                favorites = decodeIds(lines.getOrNull(1)).take(IconPreferenceState.MAX_FAVORITES),
                recents = decodeIds(lines.getOrNull(2)).take(IconPreferenceState.MAX_RECENTS),
            )
        }.getOrDefault(IconPreferenceState())
    }

    private fun decodeIds(raw: String?): List<SemanticIconId> = raw.orEmpty()
        .split(',')
        .asSequence()
        .filter(String::isNotBlank)
        .distinct()
        .map(::SemanticIconId)
        .toList()

    private const val MAX_BYTES = 8 * 1024
}
