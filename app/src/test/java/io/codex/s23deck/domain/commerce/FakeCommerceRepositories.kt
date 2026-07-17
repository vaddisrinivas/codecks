package io.codex.s23deck.domain.commerce

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAccountRepository(initialState: AccountState = AccountState.SignedOut) : AccountRepository {
    private val state = MutableStateFlow(initialState)
    var deleteAccountCalls: Int = 0
    override val account: Flow<AccountState> = state
    override suspend fun signIn(): Result<Account> = Account("test-account", "user@example.com").let {
        state.value = AccountState.SignedIn(it)
        Result.success(it)
    }
    override suspend fun signOut() { state.value = AccountState.SignedOut }
    override suspend fun deleteAccount(): Result<Unit> {
        deleteAccountCalls += 1
        state.value = AccountState.SignedOut
        return Result.success(Unit)
    }
}

class FakeGoogleTokenProvider(
    private val idToken: String? = "test-id-token",
    private val accessToken: String? = "test-access-token",
) : GoogleTokenProvider {
    override fun currentIdToken(): String? = idToken
    override fun currentAccessToken(): String? = accessToken
}

class FakeCommerceBackendClient(
    private val entitlementDecision: BackendEntitlementDecision = BackendEntitlementDecision(false, "purchase_not_active"),
    private val purchaseDecision: BackendEntitlementDecision = BackendEntitlementDecision(true, "one_time_purchase"),
) : CommerceBackendClient {
    override suspend fun exchangeGoogleIdToken(
        idToken: String,
        appId: String,
        installId: String,
    ): Result<BackendAuthSession> = Result.success(
        BackendAuthSession(
            subject = "test-subject",
            email = "user@example.com",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            accessTokenExpiresAtEpochSeconds = 2_000,
            refreshTokenExpiresAtEpochSeconds = 4_000,
        ),
    )

    override suspend fun refreshSession(
        refreshToken: String,
        appId: String,
        installId: String,
    ): Result<BackendAuthSession> = Result.success(
        BackendAuthSession(
            subject = "test-subject",
            email = "user@example.com",
            accessToken = "access-token-refreshed",
            refreshToken = "refresh-token-refreshed",
            accessTokenExpiresAtEpochSeconds = 5_000,
            refreshTokenExpiresAtEpochSeconds = 6_000,
        ),
    )

    override suspend fun logout(refreshToken: String?): Result<Unit> = Result.success(Unit)

    override suspend fun verifyPlayPurchase(
        idToken: String,
        purchase: BackendPurchaseVerificationRequest,
        appId: String,
        installId: String,
    ): Result<BackendEntitlementDecision> = Result.success(purchaseDecision)

    override suspend fun currentEntitlement(accessToken: String): Result<BackendEntitlementDecision> =
        Result.success(entitlementDecision)

    override suspend fun deleteAccount(accessToken: String): Result<Unit> = Result.success(Unit)
}

class FakeBillingRepository(
    initialState: BillingState = BillingState.Available,
    private val products: List<BillingProduct> = listOf(BillingProduct("premium", "Premium", "$4.99")),
) : BillingRepository {
    private val current = MutableStateFlow(initialState)
    override val state: Flow<BillingState> = current
    override suspend fun availableProducts(): Result<List<BillingProduct>> = when (val value = current.value) {
        BillingState.Available -> Result.success(products)
        is BillingState.Unavailable -> Result.failure(IllegalStateException(value.reason))
    }
    override suspend fun purchase(productId: String): Result<PurchaseRecord> = when (val value = current.value) {
        BillingState.Available -> Result.success(PurchaseRecord(productId, "test-token", true))
        is BillingState.Unavailable -> Result.failure(IllegalStateException(value.reason))
    }
}

class FakeEntitlementRepository(
    initialEntitlement: Entitlement = Entitlement(EntitlementTier.Free, EntitlementStatus.Free),
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
