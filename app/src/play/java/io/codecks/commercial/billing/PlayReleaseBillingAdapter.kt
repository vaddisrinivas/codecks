package io.codecks.commercial.billing

import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.CommercialPurchaseService
import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.PurchaseLaunchRequest
import io.codecks.domain.commercial.PurchaseLaunchResult
import io.codecks.domain.commercial.PurchaseVerificationRequest
import io.codecks.domain.commercial.PurchaseVerificationResult
import io.codecks.domain.commercial.ProductionDarkPurchaseService

/**
 * Production Play source-set adapter. It has no SDK dependency, constructor,
 * connection, or startup hook: public Play is compiled permanently dark.
 */
object PlayReleaseBillingAdapter : CommercialPurchaseService by ProductionDarkPurchaseService {
    override suspend fun launch(request: PurchaseLaunchRequest): PurchaseLaunchResult =
        ProductionDarkPurchaseService.launch(request)

    override suspend fun verify(request: PurchaseVerificationRequest): PurchaseVerificationResult =
        ProductionDarkPurchaseService.verify(request)

    override suspend fun reconcile(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): PurchaseVerificationResult = ProductionDarkPurchaseService.reconcile(operationId, accountId)
}
