package io.codex.s23deck.data.context

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import io.codex.s23deck.DeckWidgetProvider
import io.codex.s23deck.domain.context.RankedContextApp
import io.codex.s23deck.domain.context.RankedContextAction
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS = "context_deck_widget"
private const val KEY_JSON = "snapshot_json"

data class ContextDeckWidgetSnapshot(
    val title: String,
    val subtitle: String,
    val suggestions: List<String>,
    val apps: List<WidgetContextApp> = emptyList(),
)

data class WidgetContextApp(
    val packageName: String,
    val label: String,
)

object ContextDeckWidgetState {
    fun save(context: Context, snapshot: ContextDeckWidgetSnapshot) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, snapshot.toJson().toString())
            .apply()
        refreshWidgets(context)
    }

    fun fromRanked(
        activeMacApp: String?,
        notificationCount: Int,
        ranked: List<RankedContextAction>,
        rankedApps: List<RankedContextApp> = emptyList(),
        tileCount: Int = 0,
    ): ContextDeckWidgetSnapshot =
        ContextDeckWidgetSnapshot(
            title = "Context Deck",
            subtitle = buildString {
                append(activeMacApp?.takeIf { it.isNotBlank() } ?: "Local signals")
                if (notificationCount > 0) append(" • $notificationCount phone alerts")
                if (tileCount > 0) append(" • $tileCount tiles")
            },
            suggestions = ranked.map { it.action.label }.take(16),
            apps = rankedApps.map {
                WidgetContextApp(
                    packageName = it.app.packageName,
                    label = it.app.label,
                )
            }.take(16),
        )

    fun load(context: Context): ContextDeckWidgetSnapshot {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null)
        return raw?.let(::decode) ?: ContextDeckWidgetSnapshot(
            title = "Context Deck",
            subtitle = "Open Codecks to rank controls",
            suggestions = listOf("Deck", "Trackpad", "AI", "Automations"),
            apps = emptyList(),
        )
    }

    private fun refreshWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, DeckWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isNotEmpty()) {
            DeckWidgetProvider().onUpdate(context, manager, ids)
        }
    }

    private fun ContextDeckWidgetSnapshot.toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("subtitle", subtitle)
        .put("suggestions", JSONArray().apply { suggestions.forEach(::put) })
        .put(
            "apps",
            JSONArray().apply {
                apps.forEach { app ->
                    put(
                        JSONObject()
                            .put("packageName", app.packageName)
                            .put("label", app.label),
                    )
                }
            },
        )

    private fun decode(raw: String): ContextDeckWidgetSnapshot? = runCatching {
        val json = JSONObject(raw)
        val suggestions = json.optJSONArray("suggestions")
        val apps = json.optJSONArray("apps")
        ContextDeckWidgetSnapshot(
            title = json.optString("title", "Context Deck"),
            subtitle = json.optString("subtitle", "Local signals"),
            suggestions = List(suggestions?.length() ?: 0) { index -> suggestions!!.optString(index) }
                .filter { it.isNotBlank() },
            apps = List(apps?.length() ?: 0) { index -> apps!!.optJSONObject(index) }
                .mapNotNull { app ->
                    val packageName = app?.optString("packageName").orEmpty()
                    val label = app?.optString("label").orEmpty()
                    if (packageName.isBlank() || label.isBlank()) null else WidgetContextApp(packageName, label)
                },
        )
    }.getOrNull()
}
