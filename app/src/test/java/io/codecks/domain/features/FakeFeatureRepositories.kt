package io.codecks.domain.features

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEntitlementRepository(
    initialEntitlement: Entitlement = Entitlement(localOnly = true),
    private val refreshResult: Result<Entitlement> = Result.success(initialEntitlement),
) : EntitlementRepository {
    private val current = MutableStateFlow(initialEntitlement)
    override val entitlement: Flow<Entitlement> = current
    override suspend fun currentEntitlement(): Entitlement = current.value
    override suspend fun refresh(): Result<Entitlement> = refreshResult.onSuccess { current.value = it }
}

class FakeFeatureFlagRepository(initialFlags: Map<FeatureFlag, Boolean> = emptyMap()) : FeatureFlagRepository {
    private val current = MutableStateFlow(initialFlags)
    override val flags: Flow<Map<FeatureFlag, Boolean>> = current
    override suspend fun isEnabled(flag: FeatureFlag): Boolean = current.value[flag] == true
    override suspend fun resetDefaults() { current.value = emptyMap() }
    fun set(flag: FeatureFlag, enabled: Boolean) { current.value = current.value + (flag to enabled) }
}
