package io.codecks.core.actions

import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactKind
import io.codecks.domain.ai.GeneratedDraft
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationSafety
import io.codecks.domain.automation.AutomationTrigger
import javax.inject.Inject

class AiGeneratedContentPlanner @Inject constructor() {
    fun deckActionsFromDraft(draft: GeneratedDraft): Result<List<DeckAction>> =
        draft.toDeckActions()

    fun deckActionsFromArtifact(artifact: AiArtifact): Result<List<DeckAction>> = runCatching {
        artifact.actions.mapIndexed { index, action ->
            action.command.requireGeneratedAllowed()
            DeckAction(
                id = "${artifact.id}_${action.id}_$index",
                label = action.title,
                kind = ActionKind.Ssh,
                icon = ActionIcon.Apps,
                description = artifact.description,
                command = action.command,
                dangerous = action.dangerous,
                liveSafe = false,
                requiresTest = true,
            )
        }
    }

    fun automationRecipeFromDraft(draft: GeneratedDraft): Result<AutomationRecipe?> = runCatching {
        if (draft !is GeneratedDraft.Automation) return@runCatching null
        draft.toAutomationRecipe().getOrThrow()
    }

    fun automationRecipeFromArtifact(artifact: AiArtifact): Result<AutomationRecipe?> = runCatching {
        if (artifact.kind != AiArtifactKind.Automation) return@runCatching null
        if (artifact.actions.isEmpty()) error("Automation artifact has no runnable actions")
        AutomationRecipe(
            id = "ai_${artifact.id}",
            title = artifact.title,
            description = artifact.description.ifBlank { "AI-created automation from: ${artifact.prompt}" },
            enabled = false,
            trigger = AutomationTrigger.Manual,
            steps = artifact.actions.map { action ->
                action.command.requireGeneratedAllowed()
                ActionSpec.ShellCommand(
                    id = action.id,
                    title = action.title,
                    command = action.command,
                    trustLevel = ShellTrustLevel.Generated,
                    dangerous = action.dangerous,
                )
            },
            safety = AutomationSafety(artifact.actions.any { it.dangerous }),
        )
    }
}

private fun String.requireGeneratedAllowed() {
    RawCommandPolicy.firstViolation(this)?.let { reason ->
        error("Generated command blocked: $reason")
    }
}
