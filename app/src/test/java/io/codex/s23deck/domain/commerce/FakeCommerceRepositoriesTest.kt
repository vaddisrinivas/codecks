package io.codex.s23deck.domain.commerce

import io.codex.s23deck.domain.ai.FeatureGate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCommerceRepositoriesTest {
    @Test
    fun entitlementStates_coverPaywallCases() = runTest {
        val cases = listOf(
            Entitlement(EntitlementTier.Free, EntitlementStatus.SignedOut) to false,
            Entitlement(EntitlementTier.Free, EntitlementStatus.Free) to false,
            Entitlement(EntitlementTier.Premium, EntitlementStatus.Trialing) to true,
            Entitlement(EntitlementTier.Premium, EntitlementStatus.Active) to true,
            Entitlement(EntitlementTier.Premium, EntitlementStatus.Expired) to false,
            Entitlement(EntitlementTier.Premium, EntitlementStatus.Refunded) to false,
            Entitlement(EntitlementTier.Premium, EntitlementStatus.StaleServer) to false,
            Entitlement(EntitlementTier.Premium, EntitlementStatus.OfflineGrace, OfflineGrace.Active(1_000)) to false,
        )

        cases.forEach { (entitlement, expectedAllowed) ->
            val repository = FakeEntitlementRepository(entitlement)
            assertTrue(repository.currentEntitlement().allows(FeatureGate.AiBuilder) == expectedAllowed)
        }
    }

    @Test
    fun billingUnavailable_returnsFailure() = runTest {
        val repository = FakeBillingRepository(BillingState.Unavailable("Play Billing unavailable"))

        assertTrue(repository.availableProducts().isFailure)
        assertTrue(repository.purchase("premium").isFailure)
    }

    @Test
    fun featureFlags_doNotGrantPurchases() = runTest {
        val flags = FakeFeatureFlagRepository(mapOf(FeatureFlag.AiBuilder to true))
        val entitlement = Entitlement(EntitlementTier.Free, EntitlementStatus.Free)

        assertTrue(flags.isEnabled(FeatureFlag.AiBuilder))
        assertFalse(entitlement.allows(FeatureGate.AiBuilder))
    }

    @Test
    fun paywallFlagOff_doesNotGrantPremiumEntitlement() = runTest {
        val delegate = FakeEntitlementRepository(
            Entitlement(EntitlementTier.Free, EntitlementStatus.Free),
        )
        val flags = FakeFeatureFlagRepository(mapOf(FeatureFlag.Paywall to false))
        val repository = FeatureFlaggedEntitlementRepository(delegate, flags)

        assertEquals(Entitlement(EntitlementTier.Free, EntitlementStatus.Free), repository.currentEntitlement())
        assertEquals(Entitlement(EntitlementTier.Free, EntitlementStatus.Free), repository.entitlement.first())
        assertFalse(repository.currentEntitlement().allows(FeatureGate.AiBuilder))
    }

    @Test
    fun paywallFlagOn_stillDelegatesEntitlement() = runTest {
        val premium = Entitlement(EntitlementTier.Premium, EntitlementStatus.Active)
        val delegate = FakeEntitlementRepository(premium)
        val flags = FakeFeatureFlagRepository(mapOf(FeatureFlag.Paywall to true))
        val repository = FeatureFlaggedEntitlementRepository(delegate, flags)

        assertEquals(premium, repository.currentEntitlement())
        assertEquals(premium, repository.entitlement.first())
        assertTrue(repository.currentEntitlement().allows(FeatureGate.AiBuilder))
    }

    @Test
    fun backendEntitlementDecision_mapsServerGrantToPremium() {
        val decision = BackendEntitlementDecision(granted = true, reason = "active_subscription")

        assertEquals(Entitlement(EntitlementTier.Premium, EntitlementStatus.Active), decision.toEntitlement())
    }

    @Test
    fun backendEntitlementDecision_mapsVerificationFailureToStaleServer() {
        val decision = BackendEntitlementDecision(granted = false, reason = "verification_failed")

        assertEquals(Entitlement(EntitlementTier.Free, EntitlementStatus.StaleServer), decision.toEntitlement())
    }

    @Test
    fun fakeBackendExchange_returnsSessionTokens() = runTest {
        val backend = FakeCommerceBackendClient()

        val session = backend.exchangeGoogleIdToken(
            idToken = "id-token",
            appId = "io.codex.s23deck",
            installId = "install-id",
        ).getOrThrow()

        assertEquals("test-subject", session.subject)
        assertEquals("access-token", session.accessToken)
        assertEquals("refresh-token", session.refreshToken)
    }
}
