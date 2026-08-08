package io.codecks.domain.commercial

enum class OperationalDenySource {
    PRODUCTION_DARK,
    EMERGENCY,
    COMPATIBILITY,
    ROLLOUT_EXCLUSION,
}

class SubtractiveOperationalConfig(
    deniedSurfaces: Set<CommercialSurface>,
    val revision: Long,
    val expiresAtEpochMillis: Long?,
    val source: OperationalDenySource,
) {
    val deniedSurfaces: Set<CommercialSurface> = deniedSurfaces.toSet()

    init {
        require(revision >= 0L)
        require(expiresAtEpochMillis == null || expiresAtEpochMillis >= 0L)
    }

    fun denies(surface: CommercialSurface): Boolean = surface in deniedSurfaces
}

sealed interface OperationalConfigResult {
    data class DenyState(val config: SubtractiveOperationalConfig) : OperationalConfigResult
    data class Unavailable(val code: CommercialUnavailableCode) : OperationalConfigResult
}

interface CommercialOperationalConfigService {
    suspend fun currentDenyState(): OperationalConfigResult
    suspend fun refreshDenyState(operationId: CommercialOperationId): OperationalConfigResult
}
