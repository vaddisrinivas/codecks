package io.codex.s23deck.domain.context

import io.codex.s23deck.domain.DeckAction
import org.json.JSONArray
import org.json.JSONObject

enum class ContextSignalType {
    ActiveMacApp,
    Notification,
    Connection,
    TimeOfDay,
    Interaction,
    Usage,
    Surface,
}

data class ContextSignal(
    val type: ContextSignalType,
    val label: String,
    val value: String,
    val weight: Int,
    val sourceId: String = "",
    val observedAtMillis: Long = 0L,
)

data class ContextSnapshot(
    val signals: List<ContextSignal>,
    val createdAtMillis: Long,
) {
    val activeMacApp: String?
        get() = signals.firstOrNull { it.type == ContextSignalType.ActiveMacApp }?.value

    val macConnected: Boolean
        get() = signals.any { it.type == ContextSignalType.Connection && it.value.equals("Connected", ignoreCase = true) }

    val notificationSources: List<String>
        get() = signals.filter { it.type == ContextSignalType.Notification }.map { it.value }.distinct()
}

enum class ContextDeckTileKind {
    App,
    Action,
    Inspect,
}

data class ContextDeckTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: ContextDeckTileKind,
    val score: Int,
    val reason: String,
    val packageName: String? = null,
    val action: DeckAction? = null,
)

data class UserContextSnapshot(
    val activeMacApp: String?,
    val macConnected: Boolean,
    val notificationSources: List<String>,
    val recentApps: List<RecentContextApp> = emptyList(),
    val surface: SurfaceContext = SurfaceContext(),
    val hourOfDay: Int,
) {
    fun snapshot(createdAtMillis: Long = System.currentTimeMillis()): ContextSnapshot =
        ContextSnapshot(signals = signals(), createdAtMillis = createdAtMillis)

    fun signals(): List<ContextSignal> = buildList {
        activeMacApp
            ?.takeIf { it.isNotBlank() }
            ?.let {
                add(
                    ContextSignal(
                        type = ContextSignalType.ActiveMacApp,
                        label = "Mac app",
                        value = it,
                        weight = 8,
                    ),
                )
            }
        if (macConnected) {
            add(ContextSignal(ContextSignalType.Connection, "Mac", "Connected", 5))
        }
        add(
            ContextSignal(
                type = ContextSignalType.Surface,
                label = "Surface",
                value = surface.label,
                weight = if (surface.desktopMode) 8 else 3,
            ),
        )
        if (surface.externalDisplayConnected) {
            add(ContextSignal(ContextSignalType.Surface, "Display", "External connected", 6))
        }
        if (surface.keyboardConnected || surface.pointerConnected) {
            add(
                ContextSignal(
                    type = ContextSignalType.Surface,
                    label = "Input",
                    value = listOfNotNull(
                        "keyboard".takeIf { surface.keyboardConnected },
                        "pointer".takeIf { surface.pointerConnected },
                    ).joinToString(" + "),
                    weight = 5,
                ),
            )
        }
        notificationSources
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
            .forEach { source ->
                add(ContextSignal(ContextSignalType.Notification, "Notification", source, 4))
            }
        recentApps
            .take(4)
            .forEach { app ->
                add(
                    ContextSignal(
                        type = ContextSignalType.Usage,
                        label = "Recent app",
                        value = app.label,
                        weight = 6,
                        sourceId = app.packageName,
                        observedAtMillis = app.lastUsedAtMillis,
                    ),
                )
            }
        add(
            ContextSignal(
                type = ContextSignalType.TimeOfDay,
                label = "Time",
                value = when (hourOfDay) {
                    in 5..10 -> "Morning setup"
                    in 11..16 -> "Work session"
                    in 17..21 -> "Evening wrap"
                    else -> "Focus mode"
                },
                weight = 2,
            ),
        )
    }
}

data class RankedContextAction(
    val action: DeckAction,
    val score: Int,
    val reason: String,
)

data class ContextApp(
    val packageName: String,
    val label: String,
)

data class RecentContextApp(
    val packageName: String,
    val label: String,
    val lastUsedAtMillis: Long,
    val foregroundMillis: Long,
)

data class SurfaceContext(
    val label: String = "Phone",
    val desktopMode: Boolean = false,
    val externalDisplayConnected: Boolean = false,
    val keyboardConnected: Boolean = false,
    val pointerConnected: Boolean = false,
)

data class RankedContextApp(
    val app: ContextApp,
    val score: Int,
    val reason: String,
)

object ContextAppPromptBuilder {
    fun build(
        snapshot: UserContextSnapshot,
        apps: List<ContextApp>,
        maxApps: Int = 80,
    ): String {
        val compactApps = apps
            .sortedBy { it.label.lowercase() }
            .take(maxApps.coerceAtLeast(8))
        return buildString {
            appendLine("You are ranking Android launcher apps for Codecks Context Deck.")
            appendLine("Return JSON only. Choose apps that fit the current context.")
            appendLine("Do not invent packages. Pick only from the provided app list.")
            appendLine("Do not fill slots. If the context supports only one app, return only one app.")
            appendLine("Never suggest travel, shopping, games, or entertainment unless the context directly names them.")
            appendLine("Codecks will reject suggestions that cannot be explained by the context signals.")
            appendLine("Use this shape:")
            appendLine("""{"schemaVersion":1,"reason":"short summary","apps":[{"packageName":"...","label":"...","reason":"..."}]}""")
            appendLine()
            appendLine("Context signals:")
            snapshot.snapshot().signals.forEach { signal ->
                appendLine("- ${signal.label}: ${signal.value}")
            }
            if (snapshot.recentApps.isNotEmpty()) {
                appendLine()
                appendLine("Recent phone app usage:")
                snapshot.recentApps.take(12).forEach { app ->
                    appendLine("- ${app.label} (${app.packageName}); lastUsedBucket=${app.lastUsedAtMillis / 300000}; foregroundMinutes=${app.foregroundMillis / 60000}")
                }
            }
            appendLine()
            appendLine("Available apps:")
            compactApps.forEach { app ->
                appendLine("- ${app.label} (${app.packageName})")
            }
        }
    }
}

object ContextDeckTileRanker {
    fun rank(
        snapshot: UserContextSnapshot,
        apps: List<ContextApp>,
        actions: List<DeckAction>,
        limit: Int = 12,
    ): List<ContextDeckTile> {
        val contextSnapshot = snapshot.snapshot()
        val appTiles = ContextAppRanker.rank(snapshot, apps, limit = limit)
            .map {
                ContextDeckTile(
                    id = "app:${it.app.packageName}",
                    title = it.app.label,
                    subtitle = "App",
                    kind = ContextDeckTileKind.App,
                    score = it.score,
                    reason = it.reason,
                    packageName = it.app.packageName,
                )
            }
        val actionTiles = AiContextRanker.rank(snapshot, actions, limit = limit)
            .map {
                ContextDeckTile(
                    id = "action:${it.action.id}",
                    title = it.action.label,
                    subtitle = it.action.description.ifBlank { "Action" },
                    kind = ContextDeckTileKind.Action,
                    score = it.score,
                    reason = it.reason,
                    action = it.action,
                )
            }
        val inspectTile = ContextDeckTile(
            id = "inspect:context",
            title = "Inspect context",
            subtitle = "${contextSnapshot.signals.size} live signals",
            kind = ContextDeckTileKind.Inspect,
            score = 1,
            reason = "show what Codecks knows",
        )
        return (appTiles + actionTiles + inspectTile)
            .sortedWith(compareByDescending<ContextDeckTile> { it.score }.thenBy { it.title })
            .take(limit.coerceAtLeast(1))
    }
}

object ContextAppSuggestionParser {
    fun parse(payload: String, availableApps: List<ContextApp>): List<RankedContextApp> {
        val byPackage = availableApps.associateBy { it.packageName }
        val root = JSONObject(payload)
        val array = root.optJSONArray("apps") ?: JSONArray()
        return List(array.length()) { index -> array.optJSONObject(index) }
            .mapNotNull { json ->
                val packageName = json?.optString("packageName").orEmpty()
                val app = byPackage[packageName] ?: return@mapNotNull null
                RankedContextApp(
                    app = app,
                    score = 100 - indexPenalty(packageName),
                    reason = json.optString("reason").ifBlank { "LLM suggested" },
                )
            }
            .distinctBy { it.app.packageName }
    }

    private fun indexPenalty(packageName: String): Int =
        (packageName.length % 7) + (packageName.hashCode() and 0x3)
}

object ContextAppRanker {
    fun rank(
        snapshot: UserContextSnapshot,
        apps: List<ContextApp>,
        limit: Int = 8,
    ): List<RankedContextApp> {
        val activeApp = snapshot.activeMacApp.orEmpty().lowercase()
        val recentPackages = snapshot.recentApps.mapIndexed { index, app -> app.packageName to index }.toMap()
        val desktopMode = snapshot.surface.desktopMode
        return apps
            .map { app -> app.rankAgainst(activeApp, recentPackages, desktopMode) }
            .filter { it.score >= MIN_EXPLAINED_APP_SCORE }
            .sortedWith(compareByDescending<RankedContextApp> { it.score }.thenBy { it.app.label })
            .take(limit.coerceAtLeast(1))
    }

    private fun ContextApp.rankAgainst(
        activeApp: String,
        recentPackages: Map<String, Int>,
        desktopMode: Boolean,
    ): RankedContextApp {
        val text = "$label $packageName".lowercase()
        var score = 1
        val reasons = mutableListOf<String>()
        if (activeApp.isNotBlank() && text.containsAny(activeApp.splitWords())) {
            score += 12
            reasons += "matches Mac app"
        }
        recentPackages[packageName]?.let { index ->
            score += (14 - index).coerceAtLeast(4)
            reasons += "recently used"
        }
        when {
            activeApp.contains("chrome") && text.containsAny(listOf("chrome", "browser", "chatgpt", "gmail")) -> {
                score += 8
                reasons += "browser context"
            }
            activeApp.contains("terminal") && text.containsAny(listOf("termux", "github", "chatgpt", "drive")) -> {
                score += 8
                reasons += "developer context"
            }
            activeApp.contains("finder") && text.containsAny(listOf("files", "drive", "photos", "gallery")) -> {
                score += 8
                reasons += "file context"
            }
        }
        if (desktopMode && text.containsAny(listOf("chrome", "gmail", "slack", "github", "drive", "files", "calendar", "chatgpt", "terminal"))) {
            score += 5
            reasons += "desktop surface"
        }
        return RankedContextApp(
            app = this,
            score = score,
            reason = reasons.joinToString(),
        )
    }

    private const val MIN_EXPLAINED_APP_SCORE = 8
}

object AiContextRanker {
    fun rank(
        snapshot: UserContextSnapshot,
        actions: List<DeckAction>,
        limit: Int = 6,
    ): List<RankedContextAction> {
        val app = snapshot.activeMacApp.orEmpty().lowercase()
        val notificationText = snapshot.notificationSources.joinToString(" ").lowercase()
        return actions
            .asSequence()
            .filterNot { it.id in setOf("blank", "add_button") }
            .map { action -> action.rankAgainst(app, notificationText, snapshot) }
            .sortedWith(
                compareByDescending<RankedContextAction> { it.score }
                    .thenBy { it.action.label },
            )
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    private fun DeckAction.rankAgainst(
        app: String,
        notificationText: String,
        snapshot: UserContextSnapshot,
    ): RankedContextAction {
        val haystack = listOf(id, label, description, route.orEmpty(), command.orEmpty())
            .joinToString(" ")
            .lowercase()
        var score = if (kind.name == "Local") 3 else 2
        val reasons = mutableListOf<String>()
        if (snapshot.macConnected && kind.name == "Ssh") {
            score += 6
            reasons += "Mac is connected"
        }
        if (app.isNotBlank() && haystack.containsAny(app.splitWords())) {
            score += 12
            reasons += "matches $app"
        }
        if (notificationText.isNotBlank() && haystack.containsAny(notificationText.splitWords())) {
            score += 8
            reasons += "matches phone context"
        }
        when {
            app.contains("chrome") && haystack.containsAny(listOf("browser", "tab", "reload", "url")) -> {
                score += 10
                reasons += "browser controls"
            }
            app.contains("terminal") && haystack.containsAny(listOf("terminal", "shell", "command", "diagnostic")) -> {
                score += 10
                reasons += "terminal workflow"
            }
            app.contains("finder") && haystack.containsAny(listOf("finder", "file", "folder")) -> {
                score += 10
                reasons += "file workflow"
            }
        }
        if (haystack.containsAny(listOf("trackpad", "mouse", "keyboard"))) {
            score += if (snapshot.macConnected) 4 else 1
        }
        if (dangerous) score -= 12
        return RankedContextAction(
            action = this,
            score = score,
            reason = reasons.joinToString().ifBlank { "safe default" },
        )
    }
}

private fun String.splitWords(): List<String> =
    lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 }

private fun String.containsAny(tokens: List<String>): Boolean =
    tokens.any { contains(it) }
