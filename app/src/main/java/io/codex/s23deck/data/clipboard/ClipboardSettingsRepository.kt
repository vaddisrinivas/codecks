package io.codex.s23deck.data.clipboard

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codex.s23deck.domain.clipboard.ClipboardSyncMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.clipboardDataStore by preferencesDataStore(name = "clipboard_sync")

data class ClipboardSyncSettings(
    val mode: ClipboardSyncMode = ClipboardSyncMode.Off,
    val intervalMinutes: Int = 5,
)

@Singleton
class ClipboardSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val settings: Flow<ClipboardSyncSettings> = context.clipboardDataStore.data.map { preferences ->
        ClipboardSyncSettings(
            mode = preferences[MODE]
                ?.let { runCatching { ClipboardSyncMode.valueOf(it) }.getOrNull() }
                ?: ClipboardSyncMode.Off,
            intervalMinutes = (preferences[INTERVAL_MINUTES] ?: 5).coerceIn(1, 240),
        )
    }

    suspend fun saveMode(mode: ClipboardSyncMode) {
        context.clipboardDataStore.edit { preferences ->
            preferences[MODE] = mode.name
        }
    }

    suspend fun saveIntervalMinutes(minutes: Int) {
        context.clipboardDataStore.edit { preferences ->
            preferences[INTERVAL_MINUTES] = minutes.coerceIn(1, 240)
        }
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
    }
}
