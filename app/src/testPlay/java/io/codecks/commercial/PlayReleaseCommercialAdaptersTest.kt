package io.codecks.commercial

import io.codecks.commercial.billing.PlayReleaseBillingAdapter
import io.codecks.commercial.entitlement.PlayReleaseEntitlementAdapter
import io.codecks.commercial.integrity.PlayReleaseIntegrityAdapter
import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.CanonicalRequestDigest
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.EntitlementStateResult
import io.codecks.domain.commercial.PurchaseLaunchRequest
import io.codecks.domain.commercial.PurchaseLaunchResult
import io.codecks.domain.commercial.ProductCatalogId
import io.codecks.domain.commercial.TransactionIntegrityRequest
import io.codecks.domain.commercial.TransactionIntegrityResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayReleaseCommercialAdaptersTest {
    @Test
    fun publicPlayAdaptersStayDarkAtRuntime() = runBlocking {
        val operationId = CommercialOperationId.trusted("operation-runtime-dark")
        val accountId = BackendAccountId.verified("account-runtime-dark")

        val launch = PlayReleaseBillingAdapter.launch(
            PurchaseLaunchRequest(operationId, accountId, ProductCatalogId("codecks.pro")),
        )
        val cached = PlayReleaseEntitlementAdapter.cached(accountId)
        val integrity = PlayReleaseIntegrityAdapter.collect(
            TransactionIntegrityRequest(operationId, CanonicalRequestDigest("a".repeat(64))),
        )

        assertTrue(launch is PurchaseLaunchResult.Denied)
        assertTrue(cached is EntitlementStateResult.Unavailable)
        assertTrue(integrity is TransactionIntegrityResult.Denied)
    }
}
