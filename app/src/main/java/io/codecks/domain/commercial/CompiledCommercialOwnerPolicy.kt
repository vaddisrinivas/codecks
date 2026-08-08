package io.codecks.domain.commercial

/**
 * Public builds keep commercial owner policy compiled dark. This is not a
 * FeatureFlag, has no preference key, and cannot be raised by remote config.
 */
object PublicReleaseCommercialOwnerPolicy {
    const val RELEASE_PREMIUM_ENFORCEMENT_KEY = "release.premium_enforcement"
    const val RELEASE_PREMIUM_ENFORCEMENT_ENABLED = false
}
