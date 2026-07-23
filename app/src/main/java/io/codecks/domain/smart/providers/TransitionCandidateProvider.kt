package io.codecks.domain.smart.providers

import io.codecks.domain.smart.SmartActionRef
import io.codecks.domain.smart.SmartCandidate
import io.codecks.domain.smart.SmartCandidateProvider
import io.codecks.domain.smart.SmartContext
import io.codecks.domain.smart.SmartFeedbackSummary
import io.codecks.domain.smart.SmartRisk
import io.codecks.domain.smart.SmartConfidenceLabel
import io.codecks.domain.smart.ScoredSmartCandidate
import io.codecks.domain.smart.smartCandidateId
import io.codecks.domain.smart.smartTransitionKey

class TransitionCandidateProvider : SmartCandidateProvider {
    override val id: String = "transition"

    override fun candidates(
        context: SmartContext,
        actions: List<SmartActionRef>,
        feedback: SmartFeedbackSummary,
        nowMillis: Long,
    ): List<ScoredSmartCandidate> {
        val previousActionId = context.recentActionIds.firstOrNull() ?: return emptyList()
        return actions
            .asSequence()
            .filterNot { it.id in blockedActionIds }
            .mapNotNull { action ->
                val key = smartTransitionKey(
                    surface = context.currentSurface,
                    appKey = context.activeMacApp,
                    macId = context.selectedMacId,
                    previousActionId = previousActionId,
                    nextActionId = action.id,
                )
                val score = feedback.transitionScores[key] ?: 0
                if (score == 0) return@mapNotNull null
                val candidate = SmartCandidate(
                    id = smartCandidateId(context.currentSurface, context.activeMacApp, action.id),
                    actionId = action.id,
                    title = action.title,
                    summary = action.description.ifBlank { "Suggested control" },
                    reason = "often follows your recent action",
                    confidenceLabel = SmartConfidenceLabel.Possible,
                    capabilities = action.requiredCapabilities,
                    risks = if (action.dangerous) setOf(SmartRisk.Dangerous) else setOf(SmartRisk.Normal),
                    contextKeys = context.sanitizedKeys(),
                    expiresAtMillis = context.expiresAtMillis,
                )
                ScoredSmartCandidate(candidate = candidate, score = score, providerId = id)
            }
            .toList()
    }

    private companion object {
        val blockedActionIds = setOf("blank", "add_button")
    }
}
