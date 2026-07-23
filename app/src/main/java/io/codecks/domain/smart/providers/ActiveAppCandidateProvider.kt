package io.codecks.domain.smart.providers

import io.codecks.domain.smart.SmartActionRef
import io.codecks.domain.smart.SmartAppActionMapping
import io.codecks.domain.smart.SmartCandidate
import io.codecks.domain.smart.SmartCandidateProvider
import io.codecks.domain.smart.SmartContext
import io.codecks.domain.smart.SmartFeedbackSummary
import io.codecks.domain.smart.SmartConfidenceLabel
import io.codecks.domain.smart.SmartRisk
import io.codecks.domain.smart.ScoredSmartCandidate
import io.codecks.domain.smart.smartCandidateId

class ActiveAppCandidateProvider(
    private val mappings: List<SmartAppActionMapping>,
) : SmartCandidateProvider {
    override val id: String = "active-app"

    override fun candidates(
        context: SmartContext,
        actions: List<SmartActionRef>,
        feedback: SmartFeedbackSummary,
        nowMillis: Long,
    ): List<ScoredSmartCandidate> {
        val activeApp = context.activeMacApp?.value?.lowercase() ?: return emptyList()
        val activeAppTokens = appTokens(activeApp)
        if (activeAppTokens.isEmpty()) return emptyList()

        return actions
            .asSequence()
            .filterNot { it.id in blockedActionIds }
            .mapNotNull { action ->
                val text = listOf(action.title, action.description, action.route.orEmpty(), action.id)
                    .joinToString(" ")
                    .lowercase()
                val matchingMappings = mappings.filter { mapping ->
                    mapping.actionTokens.any { actionToken ->
                        text.contains(actionToken)
                    } && mapping.appTokens.any { appToken ->
                        activeAppTokens.any { activeToken -> activeToken.contains(appToken) || appToken.contains(activeToken) }
                    }
                }
                val match = matchingMappings.maxByOrNull { it.score } ?: return@mapNotNull null
                val reason = matchingMappings.joinToString(", ") { it.reason }
                ScoredSmartCandidate(
                    candidate = SmartCandidate(
                        id = smartCandidateId(context.currentSurface, context.activeMacApp, action.id),
                        actionId = action.id,
                        title = action.title,
                        summary = action.description.ifBlank { "Suggested control" },
                        reason = reason,
                        confidenceLabel = SmartConfidenceLabel.Possible,
                        capabilities = action.requiredCapabilities,
                        risks = if (action.dangerous) setOf(SmartRisk.Dangerous) else setOf(SmartRisk.Normal),
                        contextKeys = context.sanitizedKeys(),
                        expiresAtMillis = context.expiresAtMillis,
                    ),
                    score = match.score,
                    providerId = id,
                )
            }
            .toList()
    }

    private fun appTokens(value: String): Set<String> = value
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 }
        .toSet()

    private companion object {
        val blockedActionIds = setOf("blank", "add_button")
    }
}
