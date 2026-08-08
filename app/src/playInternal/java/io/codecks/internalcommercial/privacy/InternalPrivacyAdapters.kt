package io.codecks.internalcommercial.privacy

import io.codecks.domain.commercial.CommercialDecision
import io.codecks.domain.commercial.CommercialDenyReason
import io.codecks.domain.commercial.CommercialExecutionPolicy
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.CommercialPrivacyService
import io.codecks.domain.commercial.CommercialSurface
import io.codecks.domain.commercial.PrivacyActionResult
import io.codecks.domain.commercial.PrivacyConsentStatus
import io.codecks.domain.commercial.PrivacyStatusResult

internal enum class PrivacyDiscoveryEntry {
    PRIVACY_CENTER,
    UNAPPROVED_ROUTE,
}

internal data class PrivacyOptionsState(
    val consentStatus: PrivacyConsentStatus,
    val isPrivacyOptionsRequired: Boolean,
)

internal interface ConsentProviderPort {
    suspend fun currentState(): PrivacyOptionsState
    suspend fun requestConsent(operationId: CommercialOperationId): PrivacyActionResult
    suspend fun openPrivacyOptions(operationId: CommercialOperationId): PrivacyActionResult
}

internal sealed interface PrivacyDiscoveryResult {
    data class Ready(val service: InternalPrivacyService) : PrivacyDiscoveryResult
    data class Denied(
        val code: PrivacyDiscoveryDenyCode,
        val policyReason: CommercialDenyReason? = null,
    ) : PrivacyDiscoveryResult
}

internal enum class PrivacyDiscoveryDenyCode {
    UNAPPROVED_ENTRY,
    COMMERCIAL_POLICY_DENIED,
}

/** Provider construction is delayed until an allowed action on the explicit privacy entry. */
internal class InternalPrivacyService private constructor(
    providerFactory: () -> ConsentProviderPort,
) : CommercialPrivacyService {
    private val provider by lazy(providerFactory)

    override suspend fun currentStatus(): PrivacyStatusResult =
        PrivacyStatusResult.Known(provider.currentState().consentStatus)

    suspend fun currentOptionsState(): PrivacyOptionsState = provider.currentState()

    override suspend fun requestConsent(operationId: CommercialOperationId): PrivacyActionResult =
        provider.requestConsent(operationId)

    override suspend fun openPrivacyOptions(operationId: CommercialOperationId): PrivacyActionResult =
        provider.openPrivacyOptions(operationId)

    companion object {
        fun discover(
            entry: PrivacyDiscoveryEntry,
            policy: CommercialExecutionPolicy,
            providerFactory: () -> ConsentProviderPort,
        ): PrivacyDiscoveryResult {
            if (entry != PrivacyDiscoveryEntry.PRIVACY_CENTER) {
                return PrivacyDiscoveryResult.Denied(PrivacyDiscoveryDenyCode.UNAPPROVED_ENTRY)
            }
            val decision = policy.decide(CommercialSurface.ADS)
            return when (decision) {
                is CommercialDecision.Allowed -> PrivacyDiscoveryResult.Ready(
                    InternalPrivacyService(providerFactory),
                )
                is CommercialDecision.Denied -> PrivacyDiscoveryResult.Denied(
                    PrivacyDiscoveryDenyCode.COMMERCIAL_POLICY_DENIED,
                    decision.reason,
                )
            }
        }
    }
}
