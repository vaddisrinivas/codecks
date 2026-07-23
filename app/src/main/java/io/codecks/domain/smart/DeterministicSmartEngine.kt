package io.codecks.domain.smart

import io.codecks.domain.smart.providers.ActiveAppCandidateProvider
import io.codecks.domain.smart.providers.ConnectionRepairCandidateProvider
import io.codecks.domain.smart.providers.RecentActionCandidateProvider
import io.codecks.domain.smart.providers.TransitionCandidateProvider

class DeterministicSmartEngine(
    private val policy: SmartPolicy = DefaultSmartPolicy(),
    private val providers: List<SmartCandidateProvider> = defaultProviders(),
) : SmartEngine {

    override fun suggest(
        context: SmartContext,
        actions: List<SmartActionRef>,
        feedback: SmartFeedbackSummary,
        nowMillis: Long,
    ): SmartDecision {
        if (context.isExpired(nowMillis)) return SmartDecision(emptyList(), SmartUnavailable.Expired)
        val mergedCandidates = providers
            .flatMap { provider ->
                provider.candidates(context, actions, feedback, nowMillis)
            }
            .filter { candidate ->
                candidate.candidate.id !in feedback.suppressedContextActionKeys &&
                    candidate.candidate.actionId !in feedback.globallySuppressedActionIds
            }
            .groupBy { it.candidate.id }
            .mapNotNull { (_, duplicates) ->
                val top = duplicates.maxByOrNull { it.score } ?: return@mapNotNull null
                val mergedReasons = duplicates
                    .map { it.candidate.reason }
                    .flatMap { it.split(", ") }
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(", ")
                val feedbackScore = top.candidate.actionId
                    ?.let { feedback.actionScores[it] } ?: 0
                val dangerPenalty = if (SmartRisk.Dangerous in top.candidate.risks) 30 else 0
                val finalScore = top.score + feedbackScore - dangerPenalty
                if (finalScore <= 0) return@mapNotNull null
                top.copy(
                    candidate = top.candidate.copy(reason = mergedReasons.ifBlank { top.candidate.reason }),
                    score = finalScore,
                )
            }

        val ranked = mergedCandidates
            .sortedWith(compareByDescending<ScoredSmartCandidate> { it.score }.thenBy { it.candidate.id })
            .take(5)
            .map { it.candidate }

        return policy.filter(context, ranked, nowMillis)
    }

    private companion object {
        fun defaultProviders(): List<SmartCandidateProvider> = listOf(
            RecentActionCandidateProvider(),
            TransitionCandidateProvider(),
            ActiveAppCandidateProvider(emptyList()),
            ConnectionRepairCandidateProvider(),
        )
    }
}

class DefaultSmartPolicy : SmartPolicy {
    override fun filter(context: SmartContext, candidates: List<SmartCandidate>, nowMillis: Long): SmartDecision {
        if (context.isExpired(nowMillis)) return SmartDecision(emptyList(), SmartUnavailable.Expired)
        val allowed = candidates
            .filter { candidate -> candidate.expiresAtMillis >= nowMillis }
            .filter { candidate -> candidate.capabilities.all { it in context.supportedCapabilities } }
            .filterNot { SmartRisk.Unsupported in it.risks }
            .take(5)
        return if (allowed.isEmpty()) {
            SmartDecision(emptyList(), SmartUnavailable.NoCandidates)
        } else {
            SmartDecision(allowed)
        }
    }
}
