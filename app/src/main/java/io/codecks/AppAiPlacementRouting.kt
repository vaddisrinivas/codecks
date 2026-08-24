package io.codecks

import io.codecks.domain.ai.AiArtifact

internal enum class AiArtifactPlacementRoute { AUTOMATION, DECK }

internal fun routeAiArtifactPlacement(
    artifact: AiArtifact,
    preferredDeckSlot: Int?,
    saveAutomation: (AiArtifact) -> Boolean,
    placeOnDeck: (AiArtifact, Int?) -> Unit,
    onAutomationSaved: () -> Unit,
    onDeckPlacementRequested: () -> Unit,
): AiArtifactPlacementRoute = if (saveAutomation(artifact)) {
    onAutomationSaved()
    AiArtifactPlacementRoute.AUTOMATION
} else {
    placeOnDeck(artifact, preferredDeckSlot)
    onDeckPlacementRequested()
    AiArtifactPlacementRoute.DECK
}
