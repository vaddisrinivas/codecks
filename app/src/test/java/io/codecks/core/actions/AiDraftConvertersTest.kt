package io.codecks.core.actions

import io.codecks.domain.ai.ActionCapability
import io.codecks.domain.ai.ActionDefinition
import io.codecks.domain.ai.ActionDraft
import io.codecks.domain.ai.ActionStep
import io.codecks.domain.ai.ActionStepTypes
import io.codecks.domain.ai.DraftReviewMetadata
import io.codecks.domain.ai.GeneratedDraft
import io.codecks.domain.ai.SafetyLevel
import io.codecks.domain.ai.SafetyMetadata
import io.codecks.domain.ai.TargetSelector
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.CommandOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                        confirmationBody = "This opens a site outside Codecks.",
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
        assertEquals("This opens a site outside Codecks.", artifact.review.riskReason)
        assertEquals(listOf("Browser"), artifact.review.requiredCapabilities)
        assertEquals("Open URL", artifact.review.steps.single().type)
        assertEquals("https://docs.example.com", artifact.review.steps.single().summary)
    }

    @Test
    fun generatedAction_persistsDisabledUntilTested() {
        val action = actionDefinition().toDeckAction(index = 0, count = 1).getOrThrow()

        assertTrue(action.dangerous)
        assertFalse(action.liveSafe)
        assertTrue(action.requiresTest)
        assertEquals(CommandOrigin.AiGenerated, action.commandOrigin)
        assertEquals(action.commandRevision(), action.commandReview.reviewedRevision)
        assertEquals(null, action.commandReview.checkedRevision)
    }

    @Test
    fun generatedAutomation_persistsDisabledManualOnly() {
        val automation = actionDefinition().toAutomationRecipe("open docs").getOrThrow()

        assertFalse(automation.enabled)
        assertEquals(AutomationTrigger.Manual, automation.trigger)
        assertTrue(automation.safety.requiresConfirmation)
    }

    private fun actionDefinition(): ActionDefinition =
        ActionDefinition(
            id = "docs",
            title = "Docs",
            description = "Open docs",
            requiredCapabilities = listOf(ActionCapability.Browser),
            target = TargetSelector.ActiveDevice,
            safety = SafetyMetadata(
                level = SafetyLevel.Dangerous,
                requiresConfirmation = true,
                confirmationTitle = "Open external docs?",
                confirmationBody = "This opens a site outside Codecks.",
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
        )
}
