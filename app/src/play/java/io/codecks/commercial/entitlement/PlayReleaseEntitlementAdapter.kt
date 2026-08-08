package io.codecks.commercial.entitlement

import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.CommercialEntitlementService
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.EntitlementRefreshResult
import io.codecks.domain.commercial.EntitlementStateResult
import io.codecks.domain.commercial.ProductionDarkEntitlementService

/** Public Play has no entitlement cache, network client, or activation path. */
object PlayReleaseEntitlementAdapter : CommercialEntitlementService by ProductionDarkEntitlementService {
    override suspend fun cached(accountId: BackendAccountId): EntitlementStateResult =
        ProductionDarkEntitlementService.cached(accountId)

    override suspend fun refresh(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): EntitlementRefreshResult = ProductionDarkEntitlementService.refresh(operationId, accountId)
}
