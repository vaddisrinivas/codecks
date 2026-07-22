package io.codecks.domain.features

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private val localOnlyEntitlement = Entitlement(localOnly = true)

class LocalOnlyEntitlementRepository : EntitlementRepository {
    private val state = MutableStateFlow(localOnlyEntitlement)
    override val entitlement: Flow<Entitlement> = state.asStateFlow()

    override suspend fun currentEntitlement(): Entitlement = state.value

    override suspend fun refresh(): Result<Entitlement> = Result.success(state.value)
}
