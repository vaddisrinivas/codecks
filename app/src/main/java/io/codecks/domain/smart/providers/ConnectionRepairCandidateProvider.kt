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

class ConnectionRepairCandidateProvider : SmartCandidateProvider {
    override val id: String = "connection-repair"

    override fun candidates(
        context: SmartContext,
        actions: List<SmartActionRef>,
        feedback: SmartFeedbackSummary,
        nowMillis: Long,
    ): List<ScoredSmartCandidate> {
        if (context.macConnected) return emptyList()
        return actions
            .asSequence()
            .filter { action ->
                action.route in repairRoutes &&
                    action.id !in blockedActionIds
            }
            .map { action ->
                ScoredSmartCandidate(
                    candidate = SmartCandidate(
                        id = smartCandidateId(context.currentSurface, context.activeMacApp, action.id),
                        actionId = action.id,
                        title = action.title,
                        summary = action.description.ifBlank { "Suggested control" },
                        reason = "repair connection",
                        confidenceLabel = SmartConfidenceLabel.Possible,
                        capabilities = action.requiredCapabilities,
                        risks = if (action.dangerous) {
                            setOf(SmartRisk.Dangerous)
                        } else {
                            setOf(SmartRisk.Normal)
                        },
                        contextKeys = context.sanitizedKeys(),
                        expiresAtMillis = context.expiresAtMillis,
                    ),
                    score = 16,
                    providerId = id,
                )
            }
            .toList()
    }

    private companion object {
        val repairRoutes = setOf("settings", "setup_scan")
        val blockedActionIds = setOf("blank", "add_button")
    }
}
