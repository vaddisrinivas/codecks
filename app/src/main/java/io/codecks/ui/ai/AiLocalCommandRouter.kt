package io.codecks.ui.ai

import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.domain.DeckAction
import io.codecks.ui.theme.CodecksAccent
import io.codecks.ui.theme.CodecksBorderStyle
import io.codecks.ui.theme.CodecksShapeStyle
import io.codecks.ui.theme.CodecksSurfaceStyle
import io.codecks.ui.theme.CodecksThemeMode

internal suspend fun handleLocalAiCommand(
    prompt: String,
    actions: List<DeckAction>,
    @Suppress("UNUSED_PARAMETER") onRunAction: (DeckAction) -> Unit,
    @Suppress("UNUSED_PARAMETER") trackpadSettings: TrackpadSettings,
    @Suppress("UNUSED_PARAMETER") onTrackpadSettingsChange: (((TrackpadSettings) -> TrackpadSettings) -> Unit)?,
    @Suppress("UNUSED_PARAMETER") onThemeModeChange: (CodecksThemeMode) -> Unit,
    @Suppress("UNUSED_PARAMETER") onThemeAccentChange: (CodecksAccent) -> Unit,
    @Suppress("UNUSED_PARAMETER") onThemeSurfaceStyleChange: (CodecksSurfaceStyle) -> Unit,
    @Suppress("UNUSED_PARAMETER") onThemeBorderStyleChange: (CodecksBorderStyle) -> Unit,
    @Suppress("UNUSED_PARAMETER") onThemeShapeStyleChange: (CodecksShapeStyle) -> Unit,
    onOpenDeck: () -> Unit,
    onOpenTrackpad: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiSettings: () -> Unit,
): AiLocalCommandResult? {
    val text = prompt.normalizedCommandText()
    if (text.isBlank()) return null

    if (text.hasAnyWord("theme", "color", "colors", "bland", "oled", "dark", "light", "accent", "border", "shape")) {
        onOpenSettings()
        return AiLocalCommandResult("Opened Settings. Use Appearance to change theme, color, borders, and shape; AI did not change them.")
    }

    if (text.hasAnyPhrase("ai settings", "api key", "provider", "model")) {
        onOpenAiSettings()
        return AiLocalCommandResult("Opened AI settings.")
    }
    if (text.hasAnyPhrase("feature flag", "feature flags", "labs", "settings")) {
        onOpenSettings()
        return AiLocalCommandResult("Opened Settings.")
    }
    if (text.hasAnyWord("trackpad", "mouse")) {
        onOpenTrackpad()
        return AiLocalCommandResult("Opened Trackpad.")
    }
    if (text.hasAnyWord("deck", "buttons")) {
        onOpenDeck()
        return AiLocalCommandResult("Opened Deck.")
    }

    val creationIntent = text.hasAnyWord("create", "make", "build", "generate", "new") &&
        text.hasAnyWord("button", "deck", "automation", "workflow")
    if (creationIntent) return null

    val matchedAction = actions
        .map { it to it.aiCommandScore(text) }
        .filter { (_, score) -> score > 0 }
        .maxByOrNull { (_, score) -> score }
        ?.first
    if (matchedAction != null) {
        val shouldRun = text.hasAnyWord("trigger", "run", "execute", "press", "tap", "click")
        if (shouldRun) {
            return AiLocalCommandResult(
                message = "Found ${matchedAction.label}. Open it on Deck and press Run there.",
                actionId = matchedAction.id,
                actionLabel = matchedAction.label,
            )
        }
        return AiLocalCommandResult(
            message = "Found ${matchedAction.label}. Open it on Deck.",
            actionId = matchedAction.id,
            actionLabel = matchedAction.label,
        )
    }

    return null
}

private fun DeckAction.aiCommandScore(query: String): Int {
    val labelText = label.normalizedCommandText()
    val idText = id.normalizedCommandText()
    val labelWords = labelText.matchWords()
    val idWords = idText.matchWords()
    val queryWords = query.matchWords()
    return when {
        labelText.isNotBlank() && query.contains(labelText) -> 120
        idText.isNotBlank() && query.contains(idText) -> 110
        labelWords.isNotEmpty() && queryWords.containsAll(labelWords) -> 95
        idWords.isNotEmpty() && queryWords.containsAll(idWords) -> 90
        labelWords.size == 1 && labelWords.any(queryWords::contains) -> 80
        idWords.size == 1 && idWords.any(queryWords::contains) -> 75
        else -> 0
    }
}

private fun String.normalizedCommandText(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

private fun String.hasAnyWord(vararg words: String): Boolean =
    words.any { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(this) }

private fun String.hasAnyPhrase(vararg phrases: String): Boolean =
    phrases.any { phrase -> contains(phrase.normalizedCommandText()) }

private fun String.matchWords(): Set<String> =
    split(" ")
        .filter { it.length >= 3 && it !in setOf("button", "buttons", "deck", "control", "controls") }
        .toSet()
