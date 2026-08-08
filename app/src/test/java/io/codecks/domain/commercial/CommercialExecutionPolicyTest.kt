package io.codecks.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialExecutionPolicyTest {
    private val allowedState = CommercialGateState(
        buildCapability = BuildCapability.PRODUCTION_CAPABLE,
        ownerActivation = OwnerActivation.APPROVED,
        compatibility = Compatibility.SUPPORTED,
        emergencyDeny = EmergencyDeny.CLEAR,
        rolloutAssignment = RolloutAssignment.INCLUDED,
        entitlementProjection = EntitlementProjection.NOT_REQUIRED,
        consentState = ConsentState.NOT_REQUIRED,
        userOptIn = UserOptIn.NOT_REQUIRED,
    )

    @Test
    fun productionDarkHardDeniesEveryCommercialSurface() {
        CommercialSurface.entries.forEach { surface ->
            assertEquals(
                CommercialDecision.Denied(
                    surface = surface,
                    source = CommercialDecisionSource.BUILD,
                    reason = CommercialDenyReason.BUILD_PRODUCTION_DARK,
                ),
                CommercialExecutionPolicy.PRODUCTION_DARK.decide(surface),
            )
        }
    }

    @Test
    fun everyDenyGateDominatesOtherwiseAllowedState() {
        val cases = listOf(
            allowedState.copy(buildCapability = BuildCapability.ABSENT) to CommercialDecisionSource.BUILD,
            allowedState.copy(buildCapability = BuildCapability.PRODUCTION_DARK) to CommercialDecisionSource.BUILD,
            allowedState.copy(ownerActivation = OwnerActivation.DISABLED) to CommercialDecisionSource.OWNER,
            allowedState.copy(compatibility = Compatibility.UNKNOWN) to CommercialDecisionSource.COMPATIBILITY,
            allowedState.copy(compatibility = Compatibility.UNSUPPORTED) to CommercialDecisionSource.COMPATIBILITY,
            allowedState.copy(emergencyDeny = EmergencyDeny.UNKNOWN) to CommercialDecisionSource.EMERGENCY_DENY,
            allowedState.copy(emergencyDeny = EmergencyDeny.ACTIVE) to CommercialDecisionSource.EMERGENCY_DENY,
            allowedState.copy(rolloutAssignment = RolloutAssignment.UNKNOWN) to CommercialDecisionSource.ROLLOUT,
            allowedState.copy(rolloutAssignment = RolloutAssignment.EXCLUDED) to CommercialDecisionSource.ROLLOUT,
            allowedState.copy(entitlementProjection = EntitlementProjection.UNKNOWN) to CommercialDecisionSource.ENTITLEMENT,
            allowedState.copy(entitlementProjection = EntitlementProjection.SERVER_VERIFIED_DENIED) to CommercialDecisionSource.ENTITLEMENT,
            allowedState.copy(consentState = ConsentState.UNKNOWN) to CommercialDecisionSource.CONSENT,
            allowedState.copy(consentState = ConsentState.DENIED) to CommercialDecisionSource.CONSENT,
            allowedState.copy(userOptIn = UserOptIn.UNKNOWN) to CommercialDecisionSource.USER_OPT_IN,
            allowedState.copy(userOptIn = UserOptIn.DECLINED) to CommercialDecisionSource.USER_OPT_IN,
        )

        cases.forEach { (state, expectedSource) ->
            val decision = CommercialPolicyResolver.decide(CommercialSurface.ACCOUNT, state)
            assertTrue(decision is CommercialDecision.Denied)
            assertEquals(expectedSource, decision.source)
        }
    }

    @Test
    fun resolverIsStrictMonotonicAndAcrossEveryGateCombination() {
        for (mask in 0 until 256) {
            val state = CommercialGateState(
                buildCapability = if (mask bit 0) BuildCapability.PRODUCTION_CAPABLE else BuildCapability.PRODUCTION_DARK,
                ownerActivation = if (mask bit 1) OwnerActivation.APPROVED else OwnerActivation.DISABLED,
                compatibility = if (mask bit 2) Compatibility.SUPPORTED else Compatibility.UNSUPPORTED,
                emergencyDeny = if (mask bit 3) EmergencyDeny.CLEAR else EmergencyDeny.ACTIVE,
                rolloutAssignment = if (mask bit 4) RolloutAssignment.INCLUDED else RolloutAssignment.EXCLUDED,
                entitlementProjection = if (mask bit 5) EntitlementProjection.NOT_REQUIRED else EntitlementProjection.SERVER_VERIFIED_DENIED,
                consentState = if (mask bit 6) ConsentState.NOT_REQUIRED else ConsentState.DENIED,
                userOptIn = if (mask bit 7) UserOptIn.NOT_REQUIRED else UserOptIn.DECLINED,
            )
            val allowed = CommercialPolicyResolver.decide(CommercialSurface.CLOUD_SYNC, state) is CommercialDecision.Allowed
            assertEquals("mask=$mask", mask == 255, allowed)
        }
    }

    @Test
    fun lowerTrustAllowsCannotOverrideBuildOrOwnerDeny() {
        val everyLowerGateAllows = allowedState.copy(
            entitlementProjection = EntitlementProjection.SERVER_VERIFIED_ALLOWED,
            consentState = ConsentState.GRANTED,
            userOptIn = UserOptIn.ACCEPTED,
        )
        assertDeniedBy(
            CommercialDecisionSource.BUILD,
            everyLowerGateAllows.copy(buildCapability = BuildCapability.PRODUCTION_DARK),
        )
        assertDeniedBy(
            CommercialDecisionSource.OWNER,
            everyLowerGateAllows.copy(ownerActivation = OwnerActivation.DISABLED),
        )
    }

    @Test
    fun premiumEnforcementRemainsOffInProductionDark() {
        assertDeniedBy(
            expectedSource = CommercialDecisionSource.BUILD,
            decision = CommercialExecutionPolicy.PRODUCTION_DARK.decide(CommercialSurface.PREMIUM_ENFORCEMENT),
        )
    }

    @Test
    fun malformedAndMissingAdapterInputsFailClosed() {
        assertEquals(Compatibility.SUPPORTED, Compatibility.fromExternal(" SUPPORTED "))
        assertEquals(Compatibility.UNKNOWN, Compatibility.fromExternal("surprise"))
        assertEquals(Compatibility.UNKNOWN, Compatibility.fromExternal(""))
        assertEquals(EmergencyDeny.UNKNOWN, EmergencyDeny.fromExternal(null))
        assertEquals(RolloutAssignment.UNKNOWN, RolloutAssignment.fromExternal(null))
        assertEquals(EntitlementProjection.UNKNOWN, EntitlementProjection.fromServerVerification(false, true))
        assertEquals(ConsentState.GRANTED, ConsentState.fromExternal(" Granted "))
        assertEquals(ConsentState.UNKNOWN, ConsentState.fromExternal("maybe"))
        assertEquals(ConsentState.UNKNOWN, ConsentState.fromExternal(""))
        assertEquals(UserOptIn.UNKNOWN, UserOptIn.fromExternal(null, required = true))
    }

    @Test
    fun futureInternalVariantUsesCompileTimeFactoryWithoutMutatingProductionDark() {
        val internalTestPolicy = CommercialExecutionPolicy.forCompiledVariant(
            allowedState.copy(buildCapability = BuildCapability.INTERNAL_TEST_CAPABLE),
        )

        assertTrue(internalTestPolicy.decide(CommercialSurface.ACCOUNT) is CommercialDecision.Allowed)
        assertDeniedBy(
            CommercialDecisionSource.BUILD,
            CommercialExecutionPolicy.PRODUCTION_DARK.decide(CommercialSurface.ACCOUNT),
        )
    }

    @Test
    fun localFreeProductSurfacesAreNotCommercialGates() {
        assertEquals(
            setOf("ACCOUNT", "CLOUD_SYNC", "PLAY_BILLING", "PREMIUM_ENFORCEMENT", "ADS"),
            CommercialSurface.entries.map { it.name }.toSet(),
        )
        assertFalse(CommercialSurface.entries.any { it.name in setOf("DECK", "TRACKPAD", "KEYBOARD", "CLIPBOARD", "SSH") })
    }

    @Test
    fun allAllowingGatesProduceTypedAllowedDecision() {
        assertEquals(
            CommercialDecision.Allowed(CommercialSurface.PLAY_BILLING),
            CommercialPolicyResolver.decide(CommercialSurface.PLAY_BILLING, allowedState),
        )
    }

    private fun assertDeniedBy(expectedSource: CommercialDecisionSource, state: CommercialGateState) {
        assertDeniedBy(expectedSource, CommercialPolicyResolver.decide(CommercialSurface.ACCOUNT, state))
    }

    private fun assertDeniedBy(expectedSource: CommercialDecisionSource, decision: CommercialDecision) {
        assertTrue(decision is CommercialDecision.Denied)
        assertEquals(expectedSource, decision.source)
    }

    private infix fun Int.bit(position: Int): Boolean = this and (1 shl position) != 0
}
