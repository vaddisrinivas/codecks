package io.codecks.commercial.ads

import io.codecks.domain.commercial.CommercialAdEligibilityService
import io.codecks.domain.commercial.ProductionDarkAdEligibilityService

/**
 * Public Play builds expose only the already-compiled production-dark service.
 * This function constructs no SDK, provider, worker, configuration, or network client.
 */
internal fun productionAdsService(): CommercialAdEligibilityService =
    ProductionDarkAdEligibilityService
