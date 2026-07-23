package io.codecks.domain.smart

interface SmartCandidateProvider {
    val id: String
    fun candidates(
        context: SmartContext,
        actions: List<SmartActionRef>,
        feedback: SmartFeedbackSummary,
        nowMillis: Long,
    ): List<ScoredSmartCandidate>
}

data class ScoredSmartCandidate(
    val candidate: SmartCandidate,
    val score: Int,
    val providerId: String,
)
