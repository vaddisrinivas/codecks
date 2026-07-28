package io.codecks.domain.reactive.providers

import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveCatalogControlSpec
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveControlProvider
import io.codecks.domain.reactive.ReactiveControlSource
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.reactiveControlId

data class ReactiveAppActionMapping(
    val appTokens: Set<String>,
    val actionTokens: Set<String>,
    val reason: String,
    val score: Int,
) {
    init {
        require(appTokens.isNotEmpty()) { "ReactiveAppActionMapping appTokens must not be empty." }
        require(actionTokens.isNotEmpty()) { "ReactiveAppActionMapping actionTokens must not be empty." }
        require(reason.isNotBlank()) { "ReactiveAppActionMapping reason must not be blank." }
    }
}

class ActiveAppReactiveControlProvider(
    private val mappings: List<ReactiveAppActionMapping> = defaultReactiveAppMappings(),
    private val controls: List<ReactiveCatalogControlSpec> = defaultReactiveFrontAppControls(),
) : ReactiveControlProvider {
    override val id: String = "active-app"

    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl> {
        val frontApp = state.frontApp
        if (frontApp.status == ObservationStatus.Unavailable || frontApp.value == null) return emptyList()

        val activeApp = frontApp.value
        val appIdentity = listOf(
            activeApp.bundleId,
            activeApp.displayName,
            activeApp.kind.name,
        ).joinToString(" ")
        val activeAppTokens = appTokens(appIdentity)
        if (activeAppTokens.isEmpty()) return emptyList()

        return controls.mapNotNull { spec ->
            val text = listOf(spec.actionId, spec.title, spec.subtitle.orEmpty())
                .joinToString(" ")
                .lowercase()
            val matchingMappings = mappings.filter { mapping ->
                mapping.actionTokens.any { actionToken -> text.contains(actionToken.lowercase()) } &&
                    mapping.appTokens.any { appToken ->
                        val lowered = appToken.lowercase()
                        activeAppTokens.any { activeToken ->
                            activeToken.contains(lowered) || lowered.contains(activeToken)
                        }
                    }
            }
            val bestMatch = matchingMappings.maxByOrNull { it.score } ?: return@mapNotNull null
            ReactiveControl(
                id = reactiveControlId(id, appIdentity, spec.actionId),
                providerId = id,
                actionId = spec.actionId,
                title = spec.title,
                subtitle = spec.subtitle,
                icon = spec.icon,
                action = ReactiveAction.ExistingCatalog(spec.actionId),
                source = ReactiveControlSource.FrontApp,
                basePriority = bestMatch.score,
                confidence = when (frontApp.status) {
                    ObservationStatus.Fresh -> bestMatch.score.coerceIn(0, 100)
                    ObservationStatus.Stale -> (bestMatch.score - 20).coerceIn(0, 100)
                    else -> 0
                },
                reason = matchingMappings.joinToString(", ") { it.reason },
                explanation = "${spec.title} matches ${activeApp.displayName}",
                requiredCapabilities = spec.requiredCapabilities,
                risk = spec.risk,
                reversible = spec.reversible,
                stateRevision = state.snapshotRevision,
                actionRevision = spec.actionRevision(),
                expiresAtMillis = state.capturedAtMillis + context.controlTtlMillis,
            )
        }
    }

    private fun appTokens(value: String): Set<String> = value
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 }
        .toSet()
}

internal fun MacAppKind.reactiveCategoryTokens(): Set<String> = when (this) {
    MacAppKind.Terminal -> setOf("terminal", "shell", "console")
    MacAppKind.Browser -> setOf("browser", "web", "tab")
    MacAppKind.Finder -> setOf("finder", "file", "folder")
    MacAppKind.CodeEditor -> setOf("code", "editor", "ide")
    MacAppKind.Meeting -> setOf("meeting", "call", "zoom")
    MacAppKind.Calendar -> setOf("calendar")
    MacAppKind.Media -> setOf("media", "music", "video")
    MacAppKind.Mail -> setOf("mail", "email")
    MacAppKind.Messages -> setOf("message", "chat")
    MacAppKind.Generic -> emptySet()
}

internal fun defaultReactiveAppMappings(): List<ReactiveAppActionMapping> = listOf(
    ReactiveAppActionMapping(
        appTokens = setOf("chrome", "safari", "firefox", "browser", "arc"),
        actionTokens = setOf("browser", "reload", "tab"),
        reason = "browser_frontmost",
        score = 74,
    ),
    ReactiveAppActionMapping(
        appTokens = setOf("finder", "file", "folder"),
        actionTokens = setOf("finder", "file"),
        reason = "finder_frontmost",
        score = 68,
    ),
    ReactiveAppActionMapping(
        appTokens = setOf("terminal", "iterm", "shell", "console"),
        actionTokens = setOf("terminal", "enter"),
        reason = "terminal_frontmost",
        score = 70,
    ),
)

internal fun defaultReactiveFrontAppControls(): List<ReactiveCatalogControlSpec> = listOf(
    ReactiveCatalogControlSpec(
        actionId = "browser_back",
        title = "Back",
        subtitle = "Previous browser page",
        icon = io.codecks.domain.reactive.ReactiveIcon.ArrowLeft,
        requiredCapabilities = setOf(io.codecks.domain.reactive.CodecksCapability.KeyboardInput),
    ),
    ReactiveCatalogControlSpec(
        actionId = "browser_reload",
        title = "Reload",
        subtitle = "Refresh current browser tab",
        icon = io.codecks.domain.reactive.ReactiveIcon.Reload,
        requiredCapabilities = setOf(io.codecks.domain.reactive.CodecksCapability.KeyboardInput),
    ),
    ReactiveCatalogControlSpec(
        actionId = "finder_new_window",
        title = "New Finder Window",
        subtitle = "Open another Finder window",
        icon = io.codecks.domain.reactive.ReactiveIcon.Finder,
        requiredCapabilities = setOf(io.codecks.domain.reactive.CodecksCapability.MacCommand),
    ),
    ReactiveCatalogControlSpec(
        actionId = "terminal_enter",
        title = "Enter",
        subtitle = "Send Enter to Terminal",
        icon = io.codecks.domain.reactive.ReactiveIcon.Terminal,
        requiredCapabilities = setOf(io.codecks.domain.reactive.CodecksCapability.KeyboardInput),
    ),
)
