package io.codecks.domain.reactive

data class ReactiveDecision(
    val controls: List<ReactiveControl>,
    val contractErrors: List<String> = emptyList(),
)

interface ReactiveEngine {
    fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): ReactiveDecision
}

interface ReactivePolicy {
    fun allows(
        control: ReactiveControl,
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): Boolean
}

class DefaultReactivePolicy : ReactivePolicy {
    override fun allows(
        control: ReactiveControl,
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): Boolean {
        if (control.expiresAtMillis < nowMillis) return false
        if (control.id in context.hiddenControlIds) return false
        val availableCapabilities = state.capabilities
            .filter { it.availability == CapabilityAvailability.Available }
            .map { it.capability }
            .toSet()
        return control.requiredCapabilities.all { it in availableCapabilities }
    }
}

class DeterministicReactiveEngine(
    private val providers: List<ReactiveControlProvider>,
    private val policy: ReactivePolicy = DefaultReactivePolicy(),
    private val maxControls: Int = 6,
) : ReactiveEngine {
    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): ReactiveDecision {
        val contractErrors = mutableListOf<String>()
        val merged = providers
            .flatMap { provider -> provider.controls(state, context, nowMillis) }
            .groupBy { it.id }
            .mapNotNull { (id, duplicates) ->
                val first = duplicates.first()
                val sameContract = duplicates.all { candidate ->
                    candidate.action == first.action &&
                        candidate.actionRevision == first.actionRevision &&
                        candidate.risk == first.risk &&
                        candidate.requiredCapabilities == first.requiredCapabilities
                }
                if (!sameContract) {
                    contractErrors += "duplicate_mismatch:${id.value}"
                    return@mapNotNull null
                }
                val top = duplicates.maxByOrNull { it.basePriority } ?: return@mapNotNull null
                val mergedReason = duplicates
                    .map { it.reason }
                    .flatMap { it.split(", ") }
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(", ")
                top.copy(reason = mergedReason.ifBlank { top.reason })
            }
            .filter { control -> policy.allows(control, state, context, nowMillis) }

        val ranked = merged
            .sortedWith(
                compareByDescending<ReactiveControl> { reactiveScore(it, state, context, nowMillis) }
                    .thenBy { it.id.value },
            )
            .take(maxControls)

        return ReactiveDecision(
            controls = ranked,
            contractErrors = contractErrors,
        )
    }
}

internal fun reactiveScore(
    control: ReactiveControl,
    state: MacStateSnapshot,
    context: ReactiveTrackpadContext,
    nowMillis: Long,
): Int {
    var score = control.basePriority
    if (control.source == ReactiveControlSource.FrontApp &&
        state.frontApp.status in setOf(ObservationStatus.Fresh, ObservationStatus.Stale)
    ) {
        score += 30
    }
    if (control.risk == ReactiveRisk.Dangerous) {
        score -= 25
    }
    if (state.isBasicStateExpired(nowMillis)) {
        score -= 20
    }
    if (control.action is ReactiveAction.ChangeMode && control.action.mode == context.mode) {
        score -= 10
    }
    return score
}
