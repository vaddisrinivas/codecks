package io.codex.s23deck.core.actions

import io.codex.s23deck.domain.ai.ActionCapability
import io.codex.s23deck.domain.ai.ActionDefinition
import io.codex.s23deck.domain.ai.ActionDraft
import io.codex.s23deck.domain.ai.ActionStep
import io.codex.s23deck.domain.ai.ActionStepTypes
import io.codex.s23deck.domain.ai.DraftReviewMetadata
import io.codex.s23deck.domain.ai.GeneratedDraft
import io.codex.s23deck.domain.ai.SafetyLevel
import io.codex.s23deck.domain.ai.SafetyMetadata
import io.codex.s23deck.domain.ai.TargetSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDraftConvertersTest {
    @Test
    fun toAiArtifact_preservesReviewMetadataForHumanReview() {
        val artifact = GeneratedDraft.Action(
            ActionDraft(
                prompt = "open docs",
                providerModel = "gpt-5.5",
                metadata = DraftReviewMetadata(
                    message = "Ready",
                    assumptions = listOf("Use browser docs"),
                ),
                definition = ActionDefinition(
                    id = "docs",
                    title = "Docs",
                    description = "Open docs",
                    requiredCapabilities = listOf(ActionCapability.Browser),
                    target = TargetSelector.ActiveDevice,
                    safety = SafetyMetadata(
                        level = SafetyLevel.Dangerous,
                        requiresConfirmation = true,
                        confirmationTitle = "Open external docs?",
                    ),
                    steps = listOf(
                        ActionStep(
                            id = "open",
                            type = ActionStepTypes.OpenUrl,
                            label = "Open docs",
                            url = "https://docs.example.com",
                            confirmedDangerous = true,
                        ),
                    ),
                ),
            ),
        ).toAiArtifact().getOrThrow()

        assertEquals(listOf("Use browser docs"), artifact.review.assumptions)
        assertEquals("Active Mac", artifact.review.target)
        assertTrue(artifact.review.requiresConfirmation)
        assertEquals(listOf("Browser"), artifact.review.requiredCapabilities)
        assertEquals("Open URL", artifact.review.steps.single().type)
        assertEquals("https://docs.example.com", artifact.review.steps.single().summary)
    }
}
