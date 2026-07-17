package io.codex.s23deck.domain.commerce

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private val localOnlyEntitlement = Entitlement(
    tier = EntitlementTier.Premium,
    status = EntitlementStatus.Active,
)

class LocalOnlyAccountRepository : AccountRepository {
    private val state = MutableStateFlow<AccountState>(AccountState.SignedOut)
    override val account: Flow<AccountState> = state.asStateFlow()

    override suspend fun signIn(): Result<Account> =
        Result.failure(IllegalStateException("Local-only v1 does not use accounts or login"))

    override suspend fun signOut() {
        state.value = AccountState.SignedOut
    }

    override suspend fun deleteAccount(): Result<Unit> {
        state.value = AccountState.SignedOut
        return Result.success(Unit)
    }
}

class LocalOnlyBillingRepository : BillingRepository {
    private val billingState = MutableStateFlow<BillingState>(
        BillingState.Unavailable("Purchases are disabled for local-only v1"),
    )
    override val state: Flow<BillingState> = billingState.asStateFlow()

    override suspend fun availableProducts(): Result<List<BillingProduct>> =
        Result.success(emptyList())

    override suspend fun purchase(productId: String): Result<PurchaseRecord> =
        Result.failure(IllegalStateException("Purchases are disabled for local-only v1"))
}

class LocalOnlyEntitlementRepository : EntitlementRepository {
    private val state = MutableStateFlow(localOnlyEntitlement)
    override val entitlement: Flow<Entitlement> = state.asStateFlow()

    override suspend fun currentEntitlement(): Entitlement = state.value

    override suspend fun refresh(): Result<Entitlement> = Result.success(state.value)
}
