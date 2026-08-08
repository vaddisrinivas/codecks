package io.codecks.commercial.privacy

import io.codecks.domain.commercial.CommercialPrivacyService
import io.codecks.domain.commercial.ProductionDarkPrivacyService

/**
 * Public Play builds expose only the already-compiled production-dark service.
 * No consent SDK or provider is constructed unless a future owner-approved build replaces this source set.
 */
internal fun productionPrivacyService(): CommercialPrivacyService =
    ProductionDarkPrivacyService
