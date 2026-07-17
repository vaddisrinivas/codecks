package io.codex.s23deck.core.actions

import io.codex.s23deck.domain.ActionIcon
import io.codex.s23deck.domain.ActionKind
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.domain.ai.AiArtifact
import io.codex.s23deck.domain.ai.AiArtifactKind
import io.codex.s23deck.domain.ai.GeneratedDraft
import io.codex.s23deck.domain.automation.AutomationRecipe
import io.codex.s23deck.domain.automation.AutomationSafety
import io.codex.s23deck.domain.automation.AutomationTrigger
import javax.inject.Inject

class AiGeneratedContentPlanner @Inject constructor() {
    fun deckActionsFromDraft(draft: GeneratedDraft): Result<List<DeckAction>> =
        draft.toDeckActions()

    fun deckActionsFromArtifact(artifact: AiArtifact): Result<List<DeckAction>> = runCatching {
        artifact.actions.mapIndexed { index, action ->
            action.command.requireGeneratedSafeTemplate()
            DeckAction(
                id = "${artifact.id}_${action.id}_$index",
                label = action.title,
                kind = ActionKind.Ssh,
                icon = ActionIcon.Apps,
                description = artifact.description,
                command = action.command,
                dangerous = action.dangerous,
                liveSafe = !action.dangerous,
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
            trigger = AutomationTrigger.Manual,
            steps = artifact.actions.map { action ->
                action.command.requireGeneratedSafeTemplate()
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

private fun String.requireGeneratedSafeTemplate() {
    RawCommandPolicy.firstAllowlistViolation(this)?.let { reason ->
        error("Generated command needs manual review: $reason")
    }
}
