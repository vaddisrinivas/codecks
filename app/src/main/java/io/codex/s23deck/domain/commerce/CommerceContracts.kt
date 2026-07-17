package io.codex.s23deck.domain.commerce

import android.app.Activity
import io.codex.s23deck.domain.ai.FeatureGate
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    val account: Flow<AccountState>
    suspend fun signIn(): Result<Account>
    suspend fun signOut()
    suspend fun deleteAccount(): Result<Unit>
}

interface BillingRepository {
    val state: Flow<BillingState>
    suspend fun availableProducts(): Result<List<BillingProduct>>
    suspend fun purchase(productId: String): Result<PurchaseRecord>
}

interface EntitlementRepository {
    val entitlement: Flow<Entitlement>
    suspend fun currentEntitlement(): Entitlement
    suspend fun refresh(): Result<Entitlement>
}

interface FeatureFlagRepository {
    val flags: Flow<Map<FeatureFlag, Boolean>>
    suspend fun isEnabled(flag: FeatureFlag): Boolean
    suspend fun resetDefaults()
}

interface ActivityBoundCommerce {
    fun attachActivity(activity: Activity?)
    fun detachActivity(activity: Activity?)
}

interface GoogleTokenProvider {
    fun currentIdToken(): String?
    fun currentAccessToken(): String?
    suspend fun freshAccessToken(): String? = currentAccessToken()
}

interface CommerceBackendClient {
    suspend fun exchangeGoogleIdToken(
        idToken: String,
        appId: String,
        installId: String,
    ): Result<BackendAuthSession>

    suspend fun refreshSession(
        refreshToken: String,
        appId: String,
        installId: String,
    ): Result<BackendAuthSession>

    suspend fun logout(refreshToken: String?): Result<Unit>

    suspend fun verifyPlayPurchase(
        idToken: String,
        purchase: BackendPurchaseVerificationRequest,
        appId: String,
        installId: String,
    ): Result<BackendEntitlementDecision>

    suspend fun currentEntitlement(accessToken: String): Result<BackendEntitlementDecision>

    suspend fun deleteAccount(accessToken: String): Result<Unit>
}

data class Account(
    val id: String,
    val email: String,
)

data class BackendAuthSession(
    val subject: String,
    val email: String?,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtEpochSeconds: Long,
    val refreshTokenExpiresAtEpochSeconds: Long,
)

data class BackendPurchaseVerificationRequest(
    val kind: String,
    val purchaseToken: String,
    val productId: String?,
    val packageName: String,
)

data class BackendEntitlementDecision(
    val granted: Boolean,
    val reason: String,
) {
    fun toEntitlement(): Entitlement = when {
        granted -> Entitlement(EntitlementTier.Premium, EntitlementStatus.Active)
        reason == "verification_failed" -> Entitlement(EntitlementTier.Free, EntitlementStatus.StaleServer)
        else -> Entitlement(EntitlementTier.Free, EntitlementStatus.Free)
    }
}

sealed interface AccountState {
    data object SignedOut : AccountState
    data class SignedIn(val account: Account) : AccountState
}

sealed interface BillingState {
    data object Available : BillingState
    data class Unavailable(val reason: String) : BillingState
}

data class BillingProduct(
    val id: String,
    val title: String,
    val priceLabel: String,
)

data class PurchaseRecord(
    val productId: String,
    val token: String,
    val verified: Boolean,
)

data class Entitlement(
    val tier: EntitlementTier,
    val status: EntitlementStatus,
    val offlineGrace: OfflineGrace = OfflineGrace.None,
) {
    fun allows(gate: FeatureGate): Boolean =
        status in setOf(EntitlementStatus.Active, EntitlementStatus.Trialing) &&
            tier == EntitlementTier.Premium &&
            gate == FeatureGate.AiBuilder
}

enum class EntitlementTier {
    Free,
    Premium,
}

enum class EntitlementStatus {
    SignedOut,
    Free,
    Trialing,
    Active,
    Expired,
    Refunded,
    StaleServer,
    OfflineGrace,
}

sealed interface OfflineGrace {
    data object None : OfflineGrace
    data class Active(val expiresAtEpochMillis: Long) : OfflineGrace
}

enum class FeatureFlag {
    Deck,
    Trackpad,
    Automations,
    Ai,
    Keyboard,
    Clipboard,
    Activity,
    Settings,
    DeckEditor,
    Connection,
    Devices,
    Premium,
    Widget,
    Appearance,
    Advanced,
    AiBuilder,
    ContextDeck,
    Paywall,
    Labs,
    LabAirMouse,
    LabAirTouch,
    LabBackTap,
    LabVolumeKeys,
}
