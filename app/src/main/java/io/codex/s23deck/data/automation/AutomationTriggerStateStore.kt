package io.codex.s23deck.data.automation

import android.content.Context

internal interface AutomationTriggerStateStore {
    fun get(recipeId: String): String?
    fun put(recipeId: String, fingerprint: String)
    fun remove(recipeId: String)
}

internal class SharedPreferencesAutomationTriggerStateStore(context: Context) : AutomationTriggerStateStore {
    private val preferences = context.getSharedPreferences("automation_trigger_state", Context.MODE_PRIVATE)

    override fun get(recipeId: String): String? = preferences.getString(recipeId.key(), null)

    override fun put(recipeId: String, fingerprint: String) {
        preferences.edit().putString(recipeId.key(), fingerprint).apply()
    }

    override fun remove(recipeId: String) {
        preferences.edit().remove(recipeId.key()).apply()
    }

    private fun String.key(): String = "recipe_${hashCode().toUInt().toString(16)}"
}

internal class InMemoryAutomationTriggerStateStore : AutomationTriggerStateStore {
    private val values = mutableMapOf<String, String>()

    override fun get(recipeId: String): String? = values[recipeId]

    override fun put(recipeId: String, fingerprint: String) {
        values[recipeId] = fingerprint
    }

    override fun remove(recipeId: String) {
        values.remove(recipeId)
    }
}
