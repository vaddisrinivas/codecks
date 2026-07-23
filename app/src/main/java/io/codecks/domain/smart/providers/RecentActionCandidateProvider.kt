package io.codecks.domain.smart.providers

import io.codecks.domain.smart.SmartActionRef
import io.codecks.domain.smart.SmartCandidate
import io.codecks.domain.smart.SmartCandidateProvider
import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.smart.SmartConfidenceLabel
import io.codecks.domain.smart.SmartContext
import io.codecks.domain.smart.SmartFeedbackSummary
import io.codecks.domain.smart.ScoredSmartCandidate
import io.codecks.domain.smart.SmartRisk
import io.codecks.domain.smart.smartCandidateId

class RecentActionCandidateProvider : SmartCandidateProvider {
    override val id: String = "recent-action"

    override fun candidates(
        context: SmartContext,
        actions: List<SmartActionRef>,
        feedback: SmartFeedbackSummary,
        nowMillis: Long,
    ): List<ScoredSmartCandidate> {
        val recentOrder = context.recentActionIds.mapIndexed { index, id -> id to index }.toMap()
        val appTokens = context.activeMacApp?.value
            ?.lowercase()
            ?.split(Regex("[^a-z0-9]+"))
            ?.filter { it.length >= 3 }
            ?.toSet()
            .orEmpty()

        return actions
            .asSequence()
            .filterNot { it.id in blockedActionIds }
            .mapNotNull { action ->
                val scoreFromRecent = recentOrder[action.id]?.let { index ->
                    if (index == 0) 0 else (16 - index * 3).coerceAtLeast(3)
                } ?: 0

                val actionText = listOf(
                    action.title,
                    action.description,
                    action.route.orEmpty(),
                ).joinToString(" ")
                    .lowercase()

                val scoreFromAppMatch = when {
                    appTokens.isNotEmpty() && appTokens.any { actionText.contains(it) } -> 8
                    else -> 0
                }
                val macReadyBonus = if (SmartCapability.MacCommand in action.requiredCapabilities && context.macConnected) 6 else 0
                val score = scoreFromRecent + scoreFromAppMatch + macReadyBonus
                if (score <= 0) return@mapNotNull null

                ScoredSmartCandidate(
                    candidate = action.toCandidate(
                        context = context,
                        baseScore = score,
                        reason = buildString {
                            if (scoreFromRecent > 0) append("recently used")
                            if (scoreFromAppMatch > 0) append(if (isNotEmpty()) ", " else "").also { append("matches app context") }
                            if (macReadyBonus > 0) append(if (isNotEmpty()) ", " else "").also { append("mac ready") }
                        },
                    ),
                    score = score,
                    providerId = id,
                )
            }
            .toList()
    }

    private fun SmartActionRef.toCandidate(
        context: SmartContext,
        baseScore: Int,
        reason: String,
    ): SmartCandidate = SmartCandidate(
        id = smartCandidateId(context.currentSurface, context.activeMacApp, id),
        actionId = id,
        title = title,
        summary = description.ifBlank { "Suggested control" },
        reason = reason.ifBlank { "possible fit" },
        confidenceLabel = confidenceLabelFromScore(baseScore),
        capabilities = requiredCapabilities,
        risks = buildSet {
            if (dangerous) {
                add(SmartRisk.Dangerous)
            } else {
                add(SmartRisk.Normal)
            }
        },
        contextKeys = context.sanitizedKeys(),
        expiresAtMillis = context.expiresAtMillis,
    )

    private fun confidenceLabelFromScore(score: Int): SmartConfidenceLabel = when {
        score >= 24 -> SmartConfidenceLabel.VeryLikely
        score >= 14 -> SmartConfidenceLabel.Likely
        else -> SmartConfidenceLabel.Possible
    }

    private companion object {
        val blockedActionIds = setOf("blank", "add_button")
    }
}
