package io.codecks.domain.smart

class DeterministicSmartEngine(
    private val policy: SmartPolicy = DefaultSmartPolicy(),
) : SmartEngine {
    override fun suggest(
        context: SmartContext,
        actions: List<SmartActionRef>,
        feedback: SmartFeedbackSummary,
        nowMillis: Long,
    ): SmartDecision {
        if (context.isExpired(nowMillis)) return SmartDecision(emptyList(), SmartUnavailable.Expired)
        val appKey = context.activeMacApp?.sanitizeSmartKey().orEmpty()
        val recentOrder = context.recentActionIds.mapIndexed { index, id -> id to index }.toMap()
        val previousActionId = context.recentActionIds.firstOrNull()
        val candidates = actions
            .asSequence()
            .filterNot { it.id in blockedActionIds }
            .mapNotNull { action -> action.toCandidate(context, appKey, recentOrder, previousActionId, feedback) }
            .sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenBy { it.candidate.title })
            .map { it.candidate }
            .toList()
        return policy.filter(context, candidates, nowMillis)
    }

    private fun SmartActionRef.toCandidate(
        context: SmartContext,
        appKey: String,
        recentOrder: Map<String, Int>,
        previousActionId: String?,
        feedback: SmartFeedbackSummary,
    ): ScoredCandidate? {
        val surfaceKey = context.currentSurface.sanitizeSmartKey()
        val scopedAppKey = appKey.ifBlank { "any" }
        val candidateId = "smart:$surfaceKey:$scopedAppKey:$id"
        val neverKey = "$appKey:$id"
        if (candidateId in feedback.hiddenCandidateIds || neverKey in feedback.neverAppActionKeys) return null
        val haystack = listOf(id, title, description, route.orEmpty()).joinToString(" ").lowercase()
        var score = feedback.actionScores[id] ?: 0
        val reasons = mutableListOf<String>()
        previousActionId
            ?.takeIf { it != id }
            ?.let { previous ->
                feedback.transitionScores["$previous->$id"]?.let { transitionScore ->
                    score += transitionScore
                    reasons += "often follows recent button"
                }
            }
        recentOrder[id]?.let { index ->
            score += (18 - index * 3).coerceAtLeast(4)
            reasons += "recently used"
        }
        val appTokens = appKey.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
        if (appTokens.isNotEmpty() && appTokens.any { haystack.contains(it) }) {
            score += 18
            reasons += "matches current Mac app"
        }
        if (requiresMac && context.macConnected) {
            score += 8
            reasons += "Mac ready"
        }
        if (!context.macConnected && route == "settings") {
            score += 16
            reasons += "repair connection"
        }
        if (context.coarsePhoneContext == "desktop" && haystack.containsAny(listOf("keyboard", "clipboard", "trackpad", "browser"))) {
            score += 6
            reasons += "fits desktop mode"
        }
        if (dangerous) score -= 30
        if (score < 6) return null
        val risks = buildSet {
            add(if (dangerous) SmartRisk.Dangerous else SmartRisk.Normal)
        }
        val capabilities = buildSet {
            if (requiresMac) add(SmartCapability.MacCommand)
            if (route != null) add(SmartCapability.LocalNavigation)
            if (route == "settings") add(SmartCapability.ConnectionRepair)
            if (route == "keyboard") add(SmartCapability.Keyboard)
            if (route == "clipboard") add(SmartCapability.Clipboard)
        }
        return ScoredCandidate(
            score = score,
            candidate = SmartCandidate(
                id = candidateId,
                actionId = id,
                title = title,
                summary = description.ifBlank { "Suggested control" },
                reason = reasons.joinToString().ifBlank { "possible fit" },
                confidenceLabel = when {
                    score >= 24 -> SmartConfidenceLabel.VeryLikely
                    score >= 14 -> SmartConfidenceLabel.Likely
                    else -> SmartConfidenceLabel.Possible
                },
                capabilities = capabilities,
                risks = risks,
                contextKeys = context.sanitizedKeys(),
                expiresAtMillis = context.expiresAtMillis,
            ),
        )
    }

    private data class ScoredCandidate(val score: Int, val candidate: SmartCandidate)

    private companion object {
        val blockedActionIds = setOf("blank", "add_button")
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

private fun String.containsAny(tokens: List<String>): Boolean = tokens.any { contains(it) }
