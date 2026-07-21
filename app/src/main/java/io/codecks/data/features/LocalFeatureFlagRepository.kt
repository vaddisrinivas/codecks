package io.codecks.data.features

import android.content.Context
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.FeatureFlag
import io.codecks.domain.features.FeatureFlagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "codecks.features"
private const val KEY_FEATURE_FLAG_PREFIX = "feature_flag."
private const val KEY_FEATURE_FLAG_VERSION = "feature_flag_schema_version"
private const val FEATURE_FLAG_SCHEMA_VERSION = 3

class LocalFeatureFlagRepository(
    context: Context? = null,
    private val initialFlags: Map<FeatureFlag, Boolean> = DEFAULT_FEATURE_FLAGS,
) : FeatureFlagRepository {
    private val preferences = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateStoredFlags()
    }

    private val state = MutableStateFlow(loadFlags(initialFlags))
    override val flags: Flow<Map<FeatureFlag, Boolean>> = state.asStateFlow()

    override suspend fun isEnabled(flag: FeatureFlag): Boolean =
        state.value[flag] ?: (initialFlags[flag] == true)

    override suspend fun resetDefaults() {
        state.value = initialFlags
        preferences?.edit()?.apply {
            FeatureFlag.entries.forEach { remove(KEY_FEATURE_FLAG_PREFIX + it.name) }
        }?.apply()
    }

    fun set(flag: FeatureFlag, enabled: Boolean) {
        state.value = state.value + (flag to enabled)
        preferences?.edit()?.putBoolean(KEY_FEATURE_FLAG_PREFIX + flag.name, enabled)?.apply()
    }

    private fun loadFlags(defaults: Map<FeatureFlag, Boolean>): Map<FeatureFlag, Boolean> {
        val prefs = preferences ?: return defaults
        return FeatureFlag.entries.fold(defaults) { flags, flag ->
            val key = KEY_FEATURE_FLAG_PREFIX + flag.name
            if (prefs.contains(key)) flags + (flag to prefs.getBoolean(key, defaults[flag] == true)) else flags
        }
    }

    private fun migrateStoredFlags() {
        val prefs = preferences ?: return
        val version = prefs.getInt(KEY_FEATURE_FLAG_VERSION, 0)
        if (version >= FEATURE_FLAG_SCHEMA_VERSION) return
        prefs.edit().apply {
            listOf(
                FeatureFlag.ContextDeck,
                FeatureFlag.Keyboard,
                FeatureFlag.Clipboard,
                FeatureFlag.Labs,
                FeatureFlag.LabAirMouse,
                FeatureFlag.LabAirTouch,
                FeatureFlag.LabBackTap,
                FeatureFlag.LabVolumeKeys,
            ).forEach { flag ->
                putBoolean(KEY_FEATURE_FLAG_PREFIX + flag.name, false)
            }
            putInt(KEY_FEATURE_FLAG_VERSION, FEATURE_FLAG_SCHEMA_VERSION)
        }.apply()
    }
}
