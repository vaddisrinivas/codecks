package io.codecks.domain

import io.codecks.core.actions.commandRevision

/**
 * Smart Suggestions are an immediate-run surface. AI-generated commands may only enter it
 * after the current command revision has completed the explicit test flow.
 */
fun DeckAction.isRunnableFromSmartSuggestion(): Boolean {
    if (commandOrigin != CommandOrigin.AiGenerated) return true

    val currentRevision = commandRevision() ?: return false
    return !requiresTest &&
        liveSafe &&
        commandReview.checkedRevision == currentRevision
}
