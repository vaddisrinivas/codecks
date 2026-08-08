package io.codecks.internalcommercial.ads

import io.codecks.domain.commercial.ApprovedAdPlacement
import io.codecks.domain.commercial.CommercialExecutionPolicy
import io.codecks.domain.commercial.CommercialGateState
import io.codecks.domain.commercial.BuildCapability
import io.codecks.domain.commercial.Compatibility
import io.codecks.domain.commercial.ConsentState
import io.codecks.domain.commercial.EmergencyDeny
import io.codecks.domain.commercial.EntitlementProjection
import io.codecks.domain.commercial.OwnerActivation
import io.codecks.domain.commercial.PrivacyConsentStatus
import io.codecks.domain.commercial.RolloutAssignment
import io.codecks.domain.commercial.UserOptIn
import io.codecks.internalcommercial.CommercialTestOverrideMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalAdsAdaptersTest {
    @Test
    fun `every forbidden surface produces zero provider requests`() {
        val spy = RequestSpy()
        val coordinator = InternalAdRequestCoordinator(requesterFactory = { spy })

        AdHostSurface.entries.filter { it.placement == null }.forEach { surface ->
            assertDenied(coordinator.request(validRequest(surface)), InternalAdDenyCode.FORBIDDEN_SURFACE)
        }

        assertEquals(0, spy.requests)
        assertEquals(20, AdHostSurface.entries.count { it.placement == null })
    }

    @Test
    fun `dark corrupt consent entitlement background account rotation DeX and lockscreen never request`() {
        val spy = RequestSpy()
        var constructions = 0
        val coordinator = InternalAdRequestCoordinator(requesterFactory = {
            constructions += 1
            spy
        })
        val valid = validRequest(AdHostSurface.ROUTINE_BANK)
        val denied = listOf(
            valid.copy(policy = CommercialExecutionPolicy.PRODUCTION_DARK),
            valid.copy(policy = testPolicy(ownerActivation = OwnerActivation.DISABLED)),
            valid.copy(policy = testPolicy(compatibility = Compatibility.UNKNOWN)),
            valid.copy(policy = testPolicy(emergencyDeny = EmergencyDeny.UNKNOWN)),
            valid.copy(policy = testPolicy(emergencyDeny = EmergencyDeny.ACTIVE)),
            valid.copy(policy = testPolicy(rolloutAssignment = RolloutAssignment.UNKNOWN)),
            valid.copy(policy = testPolicy(consentState = ConsentState.UNKNOWN)),
            valid.copy(policy = testPolicy(userOptIn = UserOptIn.UNKNOWN)),
            valid.copy(consentStatus = PrivacyConsentStatus.UNKNOWN),
            valid.copy(consentStatus = PrivacyConsentStatus.DENIED),
            valid.copy(hasAdFreeEntitlement = true),
            valid.copy(runtime = valid.runtime.copy(isForeground = false)),
            valid.copy(runtime = valid.runtime.copy(isRotationSettled = false)),
            valid.copy(runtime = valid.runtime.copy(isDexMode = true)),
            valid.copy(runtime = valid.runtime.copy(isLockscreen = true)),
            valid.copy(runtime = valid.runtime.copy(accountState = AdAccountState.GUEST)),
            valid.copy(runtime = valid.runtime.copy(accountState = AdAccountState.UNKNOWN)),
            valid.copy(runtime = valid.runtime.copy(accountState = AdAccountState.CORRUPT)),
            valid.copy(runtime = valid.runtime.copy(accountState = AdAccountState.SWITCHING)),
        )

        denied.forEach { assertTrue(coordinator.request(it) is InternalAdRequestResult.Denied) }
        assertEquals(0, constructions)
        assertEquals(0, spy.requests)
    }

    @Test
    fun `organic placements require six prior items and never shift or replace content`() {
        val spy = RequestSpy()
        val coordinator = InternalAdRequestCoordinator(requesterFactory = { spy })

        listOf(AdHostSurface.ROUTINE_BANK, AdHostSurface.THEME_GALLERY).forEach { surface ->
            repeat(MIN_ORGANIC_ITEMS_BEFORE_AD) { count ->
                assertDenied(
                    coordinator.request(validRequest(surface).copy(organicItemsBefore = count)),
                    InternalAdDenyCode.TOO_FEW_ORGANIC_ITEMS,
                )
            }
            assertTrue(
                coordinator.request(
                    validRequest(surface).copy(organicItemsBefore = MIN_ORGANIC_ITEMS_BEFORE_AD),
                ) is InternalAdRequestResult.Requested,
            )
        }

        assertEquals(2, spy.requests)
        val layout = AdLayoutContract
        assertTrue(layout.reservedSlot)
        assertFalse(layout.replacesContent)
        assertFalse(layout.replacesControl)
        assertFalse(layout.shiftsVisibleContentAfterLoad)
    }

    @Test
    fun `rewarded preview is explicit and no interstitial or app-open placement exists`() {
        val spy = RequestSpy()
        val coordinator = InternalAdRequestCoordinator(requesterFactory = { spy })
        val request = validRequest(AdHostSurface.REWARDED_PREVIEW).copy(organicItemsBefore = 0)

        assertDenied(coordinator.request(request), InternalAdDenyCode.REWARD_NOT_REQUESTED)
        assertTrue(
            coordinator.request(request.copy(userRequestedReward = true)) is InternalAdRequestResult.Requested,
        )
        assertEquals(1, spy.requests)
        assertEquals(
            setOf("ROUTINE_BANK_CARD", "THEME_GALLERY_CARD", "REWARDED_PREVIEW"),
            ApprovedAdPlacement.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `ad and assignment identifiers redact diagnostics`() {
        val adId = "provider-ad-id-123"
        val assignmentId = "assignment-secret-456"

        assertFalse(RedactedAdId.fromProvider(adId).toString().contains(adId))
        assertFalse(RedactedAssignmentId.fromTrustedConfig(assignmentId).toString().contains(assignmentId))
    }

    @Test
    fun `provider rejection is not reported as a successful request`() {
        val coordinator = InternalAdRequestCoordinator(requesterFactory = {
            object : AdRequestPort {
                override fun request(placement: ApprovedAdPlacement): Boolean = false
            }
        })

        assertDenied(
            coordinator.request(validRequest(AdHostSurface.ROUTINE_BANK)),
            InternalAdDenyCode.REQUEST_REJECTED,
        )
    }

    private fun validRequest(surface: AdHostSurface) = InternalAdRequest(
        surface = surface,
        policy = testPolicy(),
        consentStatus = PrivacyConsentStatus.GRANTED,
        runtime = AdRuntimeState(
            isForeground = true,
            isRotationSettled = true,
            isDexMode = false,
            isLockscreen = false,
            accountState = AdAccountState.VERIFIED,
        ),
        organicItemsBefore = MIN_ORGANIC_ITEMS_BEFORE_AD,
        userRequestedReward = false,
        hasAdFreeEntitlement = false,
    )

    private fun testPolicy(
        ownerActivation: OwnerActivation = OwnerActivation.APPROVED,
        compatibility: Compatibility = Compatibility.SUPPORTED,
        emergencyDeny: EmergencyDeny = EmergencyDeny.CLEAR,
        rolloutAssignment: RolloutAssignment = RolloutAssignment.INCLUDED,
        consentState: ConsentState = ConsentState.GRANTED,
        userOptIn: UserOptIn = UserOptIn.ACCEPTED,
    ): CommercialExecutionPolicy = CommercialTestOverrideMarker.policyFor(
        CommercialGateState(
            buildCapability = BuildCapability.INTERNAL_TEST_CAPABLE,
            ownerActivation = ownerActivation,
            compatibility = compatibility,
            emergencyDeny = emergencyDeny,
            rolloutAssignment = rolloutAssignment,
            entitlementProjection = EntitlementProjection.NOT_REQUIRED,
            consentState = consentState,
            userOptIn = userOptIn,
        ),
    )

    private fun assertDenied(result: InternalAdRequestResult, expected: InternalAdDenyCode) {
        assertEquals(expected, (result as InternalAdRequestResult.Denied).code)
    }

    private class RequestSpy : AdRequestPort {
        var requests = 0
        override fun request(placement: ApprovedAdPlacement): Boolean {
            requests += 1
            return true
        }
    }
}
