package io.codecks.domain.commercial

enum class PrivacyConsentStatus {
    UNKNOWN,
    DENIED,
    NOT_REQUIRED,
    GRANTED,
}

sealed interface PrivacyStatusResult {
    data class Known(val status: PrivacyConsentStatus) : PrivacyStatusResult
    data class Unavailable(val code: CommercialUnavailableCode) : PrivacyStatusResult
}

sealed interface PrivacyActionResult {
    data class Updated(val status: PrivacyConsentStatus) : PrivacyActionResult
    data object OpenedOptions : PrivacyActionResult
    data class Denied(val reason: CommercialDenyReason) : PrivacyActionResult
    data class Unavailable(val code: CommercialUnavailableCode) : PrivacyActionResult
    data class Failed(val code: CommercialFailureCode) : PrivacyActionResult
}

interface CommercialPrivacyService {
    suspend fun currentStatus(): PrivacyStatusResult
    suspend fun requestConsent(operationId: CommercialOperationId): PrivacyActionResult
    suspend fun openPrivacyOptions(operationId: CommercialOperationId): PrivacyActionResult
}

enum class ApprovedAdPlacement {
    ROUTINE_BANK_CARD,
    THEME_GALLERY_CARD,
    REWARDED_PREVIEW,
}

data class AdEligibilityRequest(
    val placement: ApprovedAdPlacement,
    val commercialDecision: CommercialDecision,
    val consentStatus: PrivacyConsentStatus,
    val isForeground: Boolean,
    val organicItemsBefore: Int,
    val userRequestedReward: Boolean,
    val hasAdFreeEntitlement: Boolean,
) {
    init {
        require(commercialDecision.surface == CommercialSurface.ADS)
        require(organicItemsBefore >= 0)
    }
}

enum class AdIneligibilityCode {
    PRODUCTION_DARK,
    COMMERCIAL_POLICY_DENIED,
    CONSENT_NOT_GRANTED,
    BACKGROUND,
    TOO_FEW_ORGANIC_ITEMS,
    REWARD_NOT_REQUESTED,
    AD_FREE_ENTITLEMENT,
}

sealed interface AdEligibilityResult {
    data class Eligible(val placement: ApprovedAdPlacement) : AdEligibilityResult
    data class Denied(val code: AdIneligibilityCode) : AdEligibilityResult
}

interface CommercialAdEligibilityService {
    fun evaluate(request: AdEligibilityRequest): AdEligibilityResult
}
