package io.codecks.data.features

import android.content.Context
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.FeatureFlag
import io.codecks.domain.features.FeatureFlagRegistry
import io.codecks.domain.features.FeatureFlagRepository
import io.codecks.domain.features.LocalFeaturePreferenceMigration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "codecks.features"
private const val KEY_FEATURE_FLAG_VERSION = "feature_flag_schema_version"

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
            FeatureFlagRegistry.userPreferenceFlags.forEach { remove(LocalFeaturePreferenceMigration.storedKey(it)) }
            preferences.all.keys
                .filter(LocalFeaturePreferenceMigration::isReservedCommercialStoredKey)
                .forEach(::remove)
        }?.apply()
    }

    fun set(flag: FeatureFlag, enabled: Boolean) {
        require(flag in FeatureFlagRegistry.userPreferenceFlags) { "Only product/labs flags are locally editable" }
        state.value = state.value + (flag to enabled)
        preferences?.edit()?.putBoolean(LocalFeaturePreferenceMigration.storedKey(flag), enabled)?.apply()
    }

    private fun loadFlags(defaults: Map<FeatureFlag, Boolean>): Map<FeatureFlag, Boolean> {
        val prefs = preferences ?: return defaults
        return LocalFeaturePreferenceMigration.load(prefs.all, defaults)
    }

    private fun migrateStoredFlags() {
        val prefs = preferences ?: return
        val version = prefs.getInt(KEY_FEATURE_FLAG_VERSION, 0)
        val reservedKeys = prefs.all.keys.filter(LocalFeaturePreferenceMigration::isReservedCommercialStoredKey)
        if (version >= LocalFeaturePreferenceMigration.CURRENT_SCHEMA_VERSION && reservedKeys.isEmpty()) return
        prefs.edit().apply {
            reservedKeys.forEach(::remove)
            // Values are preserved; defaults are supplied only for missing or corrupt entries.
            if (version < LocalFeaturePreferenceMigration.CURRENT_SCHEMA_VERSION) {
                putInt(KEY_FEATURE_FLAG_VERSION, LocalFeaturePreferenceMigration.CURRENT_SCHEMA_VERSION)
            }
        }.apply()
    }
}
