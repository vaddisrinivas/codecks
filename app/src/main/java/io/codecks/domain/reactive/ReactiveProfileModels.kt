package io.codecks.domain.reactive

private const val DefaultProfileMaxControls = 6
private const val MinProfileControls = 1
private const val MaxProfileControls = 12

data class ReactiveProfilePreferences(
    val preferredMode: ReactiveTrackpadMode = ReactiveTrackpadMode.Pointer,
    val hiddenControlIds: Set<ControlId> = emptySet(),
    val pinnedControlIds: List<ControlId> = emptyList(),
    val disabledProviderIds: Set<String> = emptySet(),
    val maxControls: Int = DefaultProfileMaxControls,
    val controlTtlMillis: Long = 3_000L,
) {
    init {
        require(pinnedControlIds.distinct() == pinnedControlIds) {
            "ReactiveProfilePreferences pinnedControlIds must not contain duplicates."
        }
        require(disabledProviderIds.none { it.isBlank() }) {
            "ReactiveProfilePreferences disabledProviderIds must not contain blank ids."
        }
        require(maxControls in MinProfileControls..MaxProfileControls) {
            "ReactiveProfilePreferences maxControls must be between $MinProfileControls and $MaxProfileControls."
        }
        require(controlTtlMillis > 0) {
            "ReactiveProfilePreferences controlTtlMillis must be positive."
        }
    }
}

data class ResolvedReactiveProfile(
    val context: ReactiveTrackpadContext,
    val maxControls: Int,
    val disabledProviderIds: Set<String>,
    val stateRevision: Long?,
)

class ReactiveProfileResolver {
    fun resolve(
        preferences: ReactiveProfilePreferences,
        state: MacStateSnapshot?,
        baseContext: ReactiveTrackpadContext = ReactiveTrackpadContext(),
    ): ResolvedReactiveProfile {
        val hiddenIds = baseContext.hiddenControlIds + preferences.hiddenControlIds
        val pinnedIds = (baseContext.pinnedControlIds + preferences.pinnedControlIds)
            .distinct()
            .filterNot { it in hiddenIds }
        val disabledProviders = baseContext.disabledProviderIds + preferences.disabledProviderIds
        val maxControls = baseContext.maxControls ?: preferences.maxControls
        val ttlMillis = minOf(baseContext.controlTtlMillis, preferences.controlTtlMillis)

        return ResolvedReactiveProfile(
            context = ReactiveTrackpadContext(
                mode = preferences.preferredMode,
                controlTtlMillis = ttlMillis,
                hiddenControlIds = hiddenIds,
                pinnedControlIds = pinnedIds,
                disabledProviderIds = disabledProviders,
                maxControls = maxControls,
            ),
            maxControls = maxControls,
            disabledProviderIds = disabledProviders,
            stateRevision = state?.snapshotRevision,
        )
    }
}
