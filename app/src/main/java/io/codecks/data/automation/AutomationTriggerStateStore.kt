package io.codecks.data.automation

import android.content.Context
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface AutomationTriggerStateStore {
    suspend fun get(recipeId: String): String?
    suspend fun put(recipeId: String, fingerprint: String)
    suspend fun compareAndSet(recipeId: String, expected: String?, updated: String?): Boolean
}

internal class SharedPreferencesAutomationTriggerStateStore(context: Context) : AutomationTriggerStateStore {
    private val preferences = context.getSharedPreferences("automation_trigger_state", Context.MODE_PRIVATE)

    private val lock = Any()

    override suspend fun get(recipeId: String): String? = withContext(Dispatchers.IO) {
        synchronized(lock) { readMigrated(recipeId) }
    }

    override suspend fun put(recipeId: String, fingerprint: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readMigrated(recipeId)
            check(
                preferences.edit()
                    .putString(recipeId.key(), fingerprint)
                    .putBoolean(recipeId.migratedKey(), true)
                    .commit(),
            ) {
                "Trigger claim could not be persisted"
            }
        }
    }

    override suspend fun compareAndSet(recipeId: String, expected: String?, updated: String?): Boolean =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (readMigrated(recipeId) != expected) return@withContext false
                val editor = preferences.edit()
                if (updated == null) editor.remove(recipeId.key()) else editor.putString(recipeId.key(), updated)
                editor.putBoolean(recipeId.migratedKey(), true)
                check(editor.commit()) { "Trigger claim could not be persisted" }
                true
            }
        }

    private fun String.key(): String = automationTriggerStateKey(this)
    private fun String.migratedKey(): String = "${automationTriggerStateKey(this)}_legacy_migrated"

    private fun readMigrated(recipeId: String): String? {
        preferences.getString(recipeId.key(), null)?.let { return it }
        if (preferences.getBoolean(recipeId.migratedKey(), false)) return null
        val legacyKey = automationLegacyTriggerStateKey(recipeId)
        val legacy = preferences.getString(legacyKey, null) ?: return null
        // Retain the old key. Legacy Java-hash keys can collide, so consuming it while migrating
        // one recipe would make another colliding recipe look unseen and replay its trigger.
        check(
            preferences.edit()
                .putString(recipeId.key(), legacy)
                .putBoolean(recipeId.migratedKey(), true)
                .commit(),
        ) {
            "Legacy trigger state could not be migrated"
        }
        return legacy
    }
}

internal class InMemoryAutomationTriggerStateStore(
    private val legacyValues: MutableMap<String, String> = mutableMapOf(),
) : AutomationTriggerStateStore {
    private val values = mutableMapOf<String, String>()
    private val migratedRecipeIds = mutableSetOf<String>()

    override suspend fun get(recipeId: String): String? = synchronized(values) {
        values[recipeId] ?: if (recipeId in migratedRecipeIds) null else legacyValues[
            automationLegacyTriggerStateKey(recipeId)
        ]?.also { legacy ->
            values[recipeId] = legacy
            migratedRecipeIds += recipeId
        }
    }

    override suspend fun put(recipeId: String, fingerprint: String) {
        synchronized(values) {
            values[recipeId] = fingerprint
            migratedRecipeIds += recipeId
        }
    }

    override suspend fun compareAndSet(recipeId: String, expected: String?, updated: String?): Boolean =
        synchronized(values) {
            val current = values[recipeId] ?: if (recipeId in migratedRecipeIds) null else legacyValues[
                automationLegacyTriggerStateKey(recipeId)
            ]?.also {
                values[recipeId] = it
                migratedRecipeIds += recipeId
            }
            if (current != expected) return@synchronized false
            if (updated == null) values.remove(recipeId) else values[recipeId] = updated
            migratedRecipeIds += recipeId
            true
        }

    internal fun seedLegacy(recipeId: String, value: String) {
        legacyValues[automationLegacyTriggerStateKey(recipeId)] = value
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

internal fun automationTriggerStateKey(recipeId: String): String = "recipe_${recipeId.sha256()}"
internal fun automationLegacyTriggerStateKey(recipeId: String): String =
    "recipe_${recipeId.hashCode().toUInt().toString(16)}"
