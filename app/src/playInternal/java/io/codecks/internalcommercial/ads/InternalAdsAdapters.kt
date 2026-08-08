package io.codecks.internalcommercial.ads

import io.codecks.domain.commercial.AdEligibilityRequest
import io.codecks.domain.commercial.AdEligibilityResult
import io.codecks.domain.commercial.AdIneligibilityCode
import io.codecks.domain.commercial.ApprovedAdPlacement
import io.codecks.domain.commercial.CommercialAdEligibilityService
import io.codecks.domain.commercial.CommercialDecision
import io.codecks.domain.commercial.CommercialDenyReason
import io.codecks.domain.commercial.CommercialExecutionPolicy
import io.codecks.domain.commercial.CommercialSurface
import io.codecks.domain.commercial.PrivacyConsentStatus

internal const val MIN_ORGANIC_ITEMS_BEFORE_AD = 6

/** Every app surface is classified; only three discovery surfaces map to a placement. */
internal enum class AdHostSurface(val placement: ApprovedAdPlacement?) {
    ROUTINE_BANK(ApprovedAdPlacement.ROUTINE_BANK_CARD),
    THEME_GALLERY(ApprovedAdPlacement.THEME_GALLERY_CARD),
    REWARDED_PREVIEW(ApprovedAdPlacement.REWARDED_PREVIEW),
    TRACKPAD(null),
    OVERLAY(null),
    LOCKSCREEN(null),
    KEYBOARD(null),
    DECK(null),
    EDITOR(null),
    CATALOG(null),
    CLIPBOARD(null),
    SSH(null),
    REPAIR(null),
    AUTOMATION(null),
    AI(null),
    PURCHASE(null),
    SIGN_IN(null),
    RESTORE(null),
    DIAGNOSTICS(null),
    WIDGETS(null),
    OPEN(null),
    EXIT(null),
    DEX(null),
}

internal enum class AdAccountState {
    VERIFIED,
    GUEST,
    UNKNOWN,
    CORRUPT,
    SWITCHING,
}

internal data class AdRuntimeState(
    val isForeground: Boolean,
    val isRotationSettled: Boolean,
    val isDexMode: Boolean,
    val isLockscreen: Boolean,
    val accountState: AdAccountState,
)

internal data class InternalAdRequest(
    val surface: AdHostSurface,
    val policy: CommercialExecutionPolicy,
    val consentStatus: PrivacyConsentStatus,
    val runtime: AdRuntimeState,
    val organicItemsBefore: Int,
    val userRequestedReward: Boolean,
    val hasAdFreeEntitlement: Boolean,
)

internal enum class InternalAdDenyCode {
    FORBIDDEN_SURFACE,
    ROTATION_UNSETTLED,
    DEX_MODE,
    LOCKSCREEN,
    ACCOUNT_UNVERIFIED,
    PRODUCTION_DARK,
    COMMERCIAL_POLICY_DENIED,
    CONSENT_NOT_GRANTED,
    BACKGROUND,
    TOO_FEW_ORGANIC_ITEMS,
    REWARD_NOT_REQUESTED,
    AD_FREE_ENTITLEMENT,
    REQUEST_REJECTED,
}

internal sealed interface InternalAdRequestResult {
    data class Requested(val placement: ApprovedAdPlacement) : InternalAdRequestResult
    data class Denied(val code: InternalAdDenyCode) : InternalAdRequestResult
}

internal interface AdRequestPort {
    fun request(placement: ApprovedAdPlacement): Boolean
}

/** IDs remain opaque in diagnostics even after a real provider adapter is added. */
internal class RedactedAdId private constructor(private val value: String) {
    init {
        require(value.isNotBlank())
    }

    override fun toString(): String = "RedactedAdId(redacted)"

    companion object {
        fun fromProvider(value: String): RedactedAdId = RedactedAdId(value)
    }
}

internal class RedactedAssignmentId private constructor(private val value: String) {
    init {
        require(value.isNotBlank())
    }

    override fun toString(): String = "RedactedAssignmentId(redacted)"

    companion object {
        fun fromTrustedConfig(value: String): RedactedAssignmentId =
            RedactedAssignmentId(value)
    }
}

internal object AdLayoutContract {
    const val reservedSlot: Boolean = true
    const val replacesContent: Boolean = false
    const val replacesControl: Boolean = false
    const val shiftsVisibleContentAfterLoad: Boolean = false
}

internal object InternalAdEligibilityService : CommercialAdEligibilityService {
    override fun evaluate(request: AdEligibilityRequest): AdEligibilityResult {
        if (request.hasAdFreeEntitlement) {
            return AdEligibilityResult.Denied(AdIneligibilityCode.AD_FREE_ENTITLEMENT)
        }
        when (val decision = request.commercialDecision) {
            is CommercialDecision.Denied -> return AdEligibilityResult.Denied(
                if (decision.reason == CommercialDenyReason.BUILD_PRODUCTION_DARK) {
                    AdIneligibilityCode.PRODUCTION_DARK
                } else {
                    AdIneligibilityCode.COMMERCIAL_POLICY_DENIED
                },
            )
            is CommercialDecision.Allowed -> Unit
        }
        if (request.consentStatus !in setOf(PrivacyConsentStatus.GRANTED, PrivacyConsentStatus.NOT_REQUIRED)) {
            return AdEligibilityResult.Denied(AdIneligibilityCode.CONSENT_NOT_GRANTED)
        }
        if (!request.isForeground) return AdEligibilityResult.Denied(AdIneligibilityCode.BACKGROUND)
        if (
            request.placement != ApprovedAdPlacement.REWARDED_PREVIEW &&
            request.organicItemsBefore < MIN_ORGANIC_ITEMS_BEFORE_AD
        ) {
            return AdEligibilityResult.Denied(AdIneligibilityCode.TOO_FEW_ORGANIC_ITEMS)
        }
        if (request.placement == ApprovedAdPlacement.REWARDED_PREVIEW && !request.userRequestedReward) {
            return AdEligibilityResult.Denied(AdIneligibilityCode.REWARD_NOT_REQUESTED)
        }
        return AdEligibilityResult.Eligible(request.placement)
    }
}

/** Request port construction happens only after every fail-closed gate passes. */
internal class InternalAdRequestCoordinator(
    private val requesterFactory: () -> AdRequestPort,
    private val eligibility: CommercialAdEligibilityService = InternalAdEligibilityService,
) {
    private val requester by lazy(requesterFactory)

    fun request(request: InternalAdRequest): InternalAdRequestResult {
        val placement = request.surface.placement
            ?: return InternalAdRequestResult.Denied(InternalAdDenyCode.FORBIDDEN_SURFACE)
        if (!request.runtime.isRotationSettled) {
            return InternalAdRequestResult.Denied(InternalAdDenyCode.ROTATION_UNSETTLED)
        }
        if (request.runtime.isDexMode) return InternalAdRequestResult.Denied(InternalAdDenyCode.DEX_MODE)
        if (request.runtime.isLockscreen) return InternalAdRequestResult.Denied(InternalAdDenyCode.LOCKSCREEN)
        if (request.runtime.accountState != AdAccountState.VERIFIED) {
            return InternalAdRequestResult.Denied(InternalAdDenyCode.ACCOUNT_UNVERIFIED)
        }
        val eligibilityResult = eligibility.evaluate(
            AdEligibilityRequest(
                placement = placement,
                commercialDecision = request.policy.decide(CommercialSurface.ADS),
                consentStatus = request.consentStatus,
                isForeground = request.runtime.isForeground,
                organicItemsBefore = request.organicItemsBefore,
                userRequestedReward = request.userRequestedReward,
                hasAdFreeEntitlement = request.hasAdFreeEntitlement,
            ),
        )
        if (eligibilityResult is AdEligibilityResult.Denied) {
            return InternalAdRequestResult.Denied(eligibilityResult.code.toInternal())
        }
        return if (requester.request(placement)) {
            InternalAdRequestResult.Requested(placement)
        } else {
            InternalAdRequestResult.Denied(InternalAdDenyCode.REQUEST_REJECTED)
        }
    }
}

private fun AdIneligibilityCode.toInternal(): InternalAdDenyCode = when (this) {
    AdIneligibilityCode.PRODUCTION_DARK -> InternalAdDenyCode.PRODUCTION_DARK
    AdIneligibilityCode.COMMERCIAL_POLICY_DENIED -> InternalAdDenyCode.COMMERCIAL_POLICY_DENIED
    AdIneligibilityCode.CONSENT_NOT_GRANTED -> InternalAdDenyCode.CONSENT_NOT_GRANTED
    AdIneligibilityCode.BACKGROUND -> InternalAdDenyCode.BACKGROUND
    AdIneligibilityCode.TOO_FEW_ORGANIC_ITEMS -> InternalAdDenyCode.TOO_FEW_ORGANIC_ITEMS
    AdIneligibilityCode.REWARD_NOT_REQUESTED -> InternalAdDenyCode.REWARD_NOT_REQUESTED
    AdIneligibilityCode.AD_FREE_ENTITLEMENT -> InternalAdDenyCode.AD_FREE_ENTITLEMENT
}
