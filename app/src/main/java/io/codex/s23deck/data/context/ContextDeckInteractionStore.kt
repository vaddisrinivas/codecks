package io.codex.s23deck.data.context

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val INTERACTION_PREFS = "context_deck_interactions"
private const val KEY_EVENTS = "events_json"

data class ContextDeckInteraction(
    val type: String,
    val target: String,
    val label: String,
    val atMillis: Long,
)

object ContextDeckInteractionStore {
    fun record(
        context: Context,
        type: String,
        target: String,
        label: String,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        val prefs = context.applicationContext.getSharedPreferences(INTERACTION_PREFS, Context.MODE_PRIVATE)
        val current = decode(prefs.getString(KEY_EVENTS, null))
        val next = (current + ContextDeckInteraction(type, target, label, atMillis)).takeLast(MAX_EVENTS)
        prefs.edit().putString(KEY_EVENTS, encode(next).toString()).apply()
    }

    fun recent(context: Context): List<ContextDeckInteraction> =
        decode(context.applicationContext.getSharedPreferences(INTERACTION_PREFS, Context.MODE_PRIVATE).getString(KEY_EVENTS, null))

    private fun encode(events: List<ContextDeckInteraction>): JSONArray =
        JSONArray().apply {
            events.forEach { event ->
                put(
                    JSONObject()
                        .put("type", event.type)
                        .put("target", event.target)
                        .put("label", event.label)
                        .put("atMillis", event.atMillis),
                )
            }
        }

    private fun decode(raw: String?): List<ContextDeckInteraction> =
        runCatching {
            val array = JSONArray(raw ?: "[]")
            List(array.length()) { index -> array.optJSONObject(index) }
                .mapNotNull { json ->
                    val type = json?.optString("type").orEmpty()
                    val target = json?.optString("target").orEmpty()
                    val label = json?.optString("label").orEmpty()
                    if (type.isBlank() || target.isBlank()) return@mapNotNull null
                    ContextDeckInteraction(
                        type = type,
                        target = target,
                        label = label,
                        atMillis = json.optLong("atMillis"),
                    )
                }
        }.getOrDefault(emptyList())

    private const val MAX_EVENTS = 200
}
