package io.codecks.domain.reactive.providers

import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveControlPolicy
import io.codecks.domain.reactive.ReactiveControlProvider
import io.codecks.domain.reactive.ReactiveControlSource
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveStaleBehavior
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.reactiveControlId
import io.codecks.domain.reactive.sha256Hex

private const val MaxShortcutNameLength = 120
private const val MaxShortcutCount = 24

data class AppleShortcutDefinition(
    val stableId: String,
    val displayName: String,
    val acceptsInput: Boolean = false,
    val requiresReview: Boolean = true,
) {
    init {
        require(stableId.isNotBlank()) { "AppleShortcutDefinition stableId must not be blank." }
        require(displayName.isNotBlank()) { "AppleShortcutDefinition displayName must not be blank." }
        require(displayName.length <= MaxShortcutNameLength) {
            "AppleShortcutDefinition displayName is too long."
        }
        require(displayName.none { it == '\n' || it == '\r' || it == '\u0000' }) {
            "AppleShortcutDefinition displayName must be single-line text."
        }
        require(stableId.toByteArray(Charsets.UTF_8).size <= 128) {
            "AppleShortcutDefinition stableId must be at most 128 UTF-8 bytes."
        }
    }
}

data class AppleShortcutCatalog(
    val shortcuts: List<AppleShortcutDefinition>,
    val importedAtMillis: Long,
    val sourceRevision: ActionRevision,
) {
    init {
        require(importedAtMillis >= 0) { "AppleShortcutCatalog importedAtMillis must not be negative." }
        require(shortcuts.size <= MaxShortcutCount) { "AppleShortcutCatalog shortcut limit exceeded." }
        require(shortcuts.map { it.stableId }.distinct() == shortcuts.map { it.stableId }) {
            "AppleShortcutCatalog stable ids must be unique."
        }
    }
}

class AppleShortcutsListParser {
    fun parse(
        output: String,
        importedAtMillis: Long,
    ): AppleShortcutCatalog {
        val shortcuts = output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("Shortcuts", ignoreCase = true) }
            .distinct()
            .take(MaxShortcutCount)
            .map { name ->
                AppleShortcutDefinition(
                    stableId = "shortcut-${sha256Hex(name.lowercase()).take(32)}",
                    displayName = name.take(MaxShortcutNameLength),
                )
            }
            .toList()
        return AppleShortcutCatalog(
            shortcuts = shortcuts,
            importedAtMillis = importedAtMillis,
            sourceRevision = ActionRevision(
                "shortcuts-${sha256Hex(shortcuts.joinToString("|") { it.stableId }).take(64)}",
            ),
        )
    }
}

class AppleShortcutsReactiveControlProvider(
    private val catalog: () -> AppleShortcutCatalog? = { null },
) : ReactiveControlProvider {
    override val id: String = "apple-shortcuts"

    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl> {
        val shortcuts = catalog()?.shortcuts.orEmpty()
        if (shortcuts.isEmpty()) return emptyList()

        return shortcuts.map { shortcut ->
            ReactiveControl(
                id = reactiveControlId(id, state.macId.value, shortcut.stableId),
                providerId = id,
                actionId = shortcut.stableId,
                title = shortcut.displayName,
                subtitle = if (shortcut.acceptsInput) "Run shortcut with input" else "Run Apple Shortcut",
                icon = ReactiveIcon.Generic,
                action = ReactiveAction.Helper(
                    actionId = "apple_shortcuts.run",
                    arguments = mapOf(
                        "shortcutId" to shortcut.stableId,
                        "shortcutName" to shortcut.displayName,
                        "acceptsInput" to shortcut.acceptsInput.toString(),
                    ),
                ),
                source = ReactiveControlSource.ShortcutCatalog,
                basePriority = 44,
                confidence = if (state.isBasicStateExpired(nowMillis)) 35 else 70,
                reason = "apple_shortcut_imported",
                explanation = "Typed Apple Shortcuts helper action for ${shortcut.displayName}",
                policy = if (shortcut.requiresReview) {
                    ReactiveControlPolicy.RequiresReview
                } else {
                    ReactiveControlPolicy.Allow
                },
                requiredCapabilities = setOf(CodecksCapability.MacCommand),
                risk = if (shortcut.requiresReview) ReactiveRisk.Review else ReactiveRisk.Safe,
                staleBehavior = ReactiveStaleBehavior.Deny,
                reversible = false,
                stateRevision = state.snapshotRevision,
                actionRevision = ActionRevision(
                    "shortcut-${sha256Hex("${shortcut.stableId}|${shortcut.displayName}|${shortcut.acceptsInput}").take(64)}",
                ),
                expiresAtMillis = state.capturedAtMillis + context.controlTtlMillis,
            )
        }
    }
}
