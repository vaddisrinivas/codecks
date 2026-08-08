package io.codecks.commercial.integrity

import io.codecks.domain.commercial.ProductionDarkIntegrityService
import io.codecks.domain.commercial.TransactionIntegrityRequest
import io.codecks.domain.commercial.TransactionIntegrityResult
import io.codecks.domain.commercial.TransactionIntegrityService

/** Public Play cannot collect integrity evidence or initialize an integrity client. */
object PlayReleaseIntegrityAdapter : TransactionIntegrityService by ProductionDarkIntegrityService {
    override suspend fun collect(request: TransactionIntegrityRequest): TransactionIntegrityResult =
        ProductionDarkIntegrityService.collect(request)
}
