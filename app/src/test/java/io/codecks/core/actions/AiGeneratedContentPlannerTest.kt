package io.codecks.core.actions

import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactAction
import io.codecks.domain.ai.AiArtifactKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGeneratedContentPlannerTest {
    private val planner = AiGeneratedContentPlanner()

    @Test
    fun automationArtifactPlansAutomationRecipe() {
        val recipe = planner.automationRecipeFromArtifact(
            AiArtifact(
                id = "artifact1",
                kind = AiArtifactKind.Automation,
                title = "Morning setup",
                description = "",
                prompt = "prep workspace",
                actions = listOf(
                    AiArtifactAction("open_calendar", "Open Calendar", "open 'https://calendar.google.com'", dangerous = false),
                    AiArtifactAction("focus", "Focus", "caffeinate -u -t 30", dangerous = true),
                ),
            ),
        ).getOrThrow()

        assertNotNull(recipe)
        requireNotNull(recipe)
        assertEquals("ai_artifact1", recipe.id)
        assertEquals("AI-created automation from: prep workspace", recipe.description)
        assertEquals(2, recipe.steps.size)
        assertTrue(recipe.safety.requiresConfirmation)
    }

    @Test
    fun automationArtifactRejectsUnsafeGeneratedCommand() {
        val result = planner.automationRecipeFromArtifact(
            AiArtifact(
                id = "artifact1",
                kind = AiArtifactKind.Automation,
                title = "Bad cleanup",
                prompt = "cleanup",
                actions = listOf(
                    AiArtifactAction("cleanup", "Cleanup", "rm -rf /tmp/nope", dangerous = true),
                ),
            ),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("manual review"))
    }

    @Test
    fun nonAutomationArtifactDoesNotClaimAutomationSave() {
        val recipe = planner.automationRecipeFromArtifact(
            AiArtifact(
                id = "button1",
                kind = AiArtifactKind.Button,
                title = "Open Notes",
                prompt = "notes",
                actions = listOf(AiArtifactAction("open_notes", "Open Notes", "open -a Notes")),
            ),
        ).getOrThrow()

        assertNull(recipe)
    }

    @Test
    fun deckActionsFromArtifactKeepDangerFlag() {
        val actions = planner.deckActionsFromArtifact(
            AiArtifact(
                id = "deck1",
                kind = AiArtifactKind.Deck,
                title = "Deck",
                prompt = "deck",
                actions = listOf(AiArtifactAction("focus", "Focus", "caffeinate -u -t 30", dangerous = true)),
            ),
        ).getOrThrow()

        assertEquals(1, actions.size)
        assertTrue(actions.single().dangerous)
        assertFalse(actions.single().liveSafe)
    }
}
