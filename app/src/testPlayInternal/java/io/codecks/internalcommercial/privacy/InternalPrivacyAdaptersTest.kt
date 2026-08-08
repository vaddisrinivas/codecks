package io.codecks.internalcommercial.privacy

import io.codecks.domain.commercial.CommercialExecutionPolicy
import io.codecks.domain.commercial.CommercialGateState
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.BuildCapability
import io.codecks.domain.commercial.Compatibility
import io.codecks.domain.commercial.ConsentState
import io.codecks.domain.commercial.EmergencyDeny
import io.codecks.domain.commercial.EntitlementProjection
import io.codecks.domain.commercial.OwnerActivation
import io.codecks.domain.commercial.PrivacyActionResult
import io.codecks.domain.commercial.PrivacyConsentStatus
import io.codecks.domain.commercial.RolloutAssignment
import io.codecks.domain.commercial.UserOptIn
import io.codecks.internalcommercial.CommercialTestOverrideMarker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalPrivacyAdaptersTest {
    @Test
    fun `provider is lazy and only approved discovery entry can construct it`() = runTest {
        var constructions = 0
        val factory = {
            constructions += 1
            ConsentSpy()
        }
        val allowed = testPolicy()
        val denied = testPolicy(ownerActivation = OwnerActivation.DISABLED)

        assertTrue(
            InternalPrivacyService.discover(
                PrivacyDiscoveryEntry.UNAPPROVED_ROUTE,
                allowed,
                factory,
            ) is PrivacyDiscoveryResult.Denied,
        )
        assertTrue(
            InternalPrivacyService.discover(
                PrivacyDiscoveryEntry.PRIVACY_CENTER,
                denied,
                factory,
            ) is PrivacyDiscoveryResult.Denied,
        )
        assertTrue(
            InternalPrivacyService.discover(
                PrivacyDiscoveryEntry.PRIVACY_CENTER,
                CommercialExecutionPolicy.PRODUCTION_DARK,
                factory,
            ) is PrivacyDiscoveryResult.Denied,
        )
        assertTrue(
            InternalPrivacyService.discover(
                PrivacyDiscoveryEntry.PRIVACY_CENTER,
                testPolicy(compatibility = Compatibility.UNKNOWN),
                factory,
            ) is PrivacyDiscoveryResult.Denied,
        )
        val ready = InternalPrivacyService.discover(
            PrivacyDiscoveryEntry.PRIVACY_CENTER,
            allowed,
            factory,
        ) as PrivacyDiscoveryResult.Ready
        assertEquals(0, constructions)

        assertEquals(
            PrivacyOptionsState(PrivacyConsentStatus.GRANTED, isPrivacyOptionsRequired = true),
            ready.service.currentOptionsState(),
        )
        assertEquals(1, constructions)
    }

    @Test
    fun `consent and privacy options execute only after approved discovery`() = runTest {
        val spy = ConsentSpy()
        val ready = InternalPrivacyService.discover(
            PrivacyDiscoveryEntry.PRIVACY_CENTER,
            testPolicy(),
        ) { spy } as PrivacyDiscoveryResult.Ready
        val operationId = CommercialOperationId.trusted("privacy-operation-01")

        assertTrue(ready.service.requestConsent(operationId) is PrivacyActionResult.Updated)
        assertTrue(ready.service.openPrivacyOptions(operationId) is PrivacyActionResult.OpenedOptions)
        assertEquals(1, spy.consentRequests)
        assertEquals(1, spy.optionsRequests)
    }

    private fun testPolicy(
        ownerActivation: OwnerActivation = OwnerActivation.APPROVED,
        compatibility: Compatibility = Compatibility.SUPPORTED,
    ): CommercialExecutionPolicy = CommercialTestOverrideMarker.policyFor(
        CommercialGateState(
            buildCapability = BuildCapability.INTERNAL_TEST_CAPABLE,
            ownerActivation = ownerActivation,
            compatibility = compatibility,
            emergencyDeny = EmergencyDeny.CLEAR,
            rolloutAssignment = RolloutAssignment.INCLUDED,
            entitlementProjection = EntitlementProjection.NOT_REQUIRED,
            consentState = ConsentState.GRANTED,
            userOptIn = UserOptIn.ACCEPTED,
        ),
    )

    private class ConsentSpy : ConsentProviderPort {
        var consentRequests = 0
        var optionsRequests = 0

        override suspend fun currentState(): PrivacyOptionsState =
            PrivacyOptionsState(PrivacyConsentStatus.GRANTED, isPrivacyOptionsRequired = true)

        override suspend fun requestConsent(operationId: CommercialOperationId): PrivacyActionResult {
            consentRequests += 1
            return PrivacyActionResult.Updated(PrivacyConsentStatus.GRANTED)
        }

        override suspend fun openPrivacyOptions(operationId: CommercialOperationId): PrivacyActionResult {
            optionsRequests += 1
            return PrivacyActionResult.OpenedOptions
        }
    }
}
