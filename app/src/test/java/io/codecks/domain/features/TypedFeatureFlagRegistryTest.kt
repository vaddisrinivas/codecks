package io.codecks.domain.features

import io.codecks.domain.commercial.CommercialDecision
import io.codecks.domain.commercial.CommercialDenyReason
import io.codecks.domain.commercial.CommercialExecutionPolicy
import io.codecks.domain.commercial.CommercialSurface
import io.codecks.domain.commercial.PublicReleaseCommercialOwnerPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedFeatureFlagRegistryTest {
    private val now = 1_700_000_000_000L

    @Test
    fun registryHasStableTypedMetadataForEveryExistingProductAndLabFlag() {
        assertEquals(FeatureFlag.entries.toSet(), FeatureFlagRegistry.entries.map { it.flag }.toSet())
        assertEquals(
            DEFAULT_FEATURE_FLAGS,
            FeatureFlagRegistry.defaultFlags(FeatureVariant.PUBLIC_RELEASE),
        )
        FeatureFlagRegistry.entries.forEach { metadata ->
            assertEquals(FeatureValueType.BOOLEAN, metadata.valueType)
            assertEquals(FeatureVariant.entries.toSet(), metadata.defaults.keys)
            assertTrue(metadata.stableKey.isNotBlank())
            assertTrue(metadata.purpose.isNotBlank())
            assertTrue(metadata.minAppVersion >= 1)
            assertEquals(FeatureExposure.USER_PREFERENCE, metadata.exposure)
            assertEquals(
                if (metadata.featureClass == FeatureClass.LAB) FeatureFlagOwner.LABS else FeatureFlagOwner.PRODUCT,
                metadata.owner,
            )
            assertEquals(metadata.featureClass == FeatureClass.CORE_PRODUCT, metadata.reviewExpiresAtEpochMillis == null)
            assertEquals(
                metadata.featureClass != FeatureClass.CORE_PRODUCT,
                metadata.safetyBehavior == FeatureSafetyBehavior.REMOTE_MAY_DISABLE,
            )
        }
        assertTrue(FeatureFlagRegistry.expiredTemporaryFlags(System.currentTimeMillis()).isEmpty())
        assertTrue(FeatureFlagRegistry.expiredTemporaryFlags(1_830_297_600_000L).isNotEmpty())
    }

    @Test
    fun legacyMigrationPreservesProductValuesAndCorruptPreferencesFallBack() {
        val loaded = LocalFeaturePreferenceMigration.load(
            storedValues = mapOf(
                LocalFeaturePreferenceMigration.storedKey(FeatureFlag.Deck) to false,
                LocalFeaturePreferenceMigration.storedKey(FeatureFlag.LabAirMouse) to true,
                LocalFeaturePreferenceMigration.storedKey(FeatureFlag.Keyboard) to "not-a-boolean",
                "feature_flag.release.premium_enforcement" to true,
            ),
            defaults = DEFAULT_FEATURE_FLAGS,
        )

        assertFalse(loaded.getValue(FeatureFlag.Deck))
        assertTrue(loaded.getValue(FeatureFlag.LabAirMouse))
        assertTrue(loaded.getValue(FeatureFlag.Keyboard))
        assertEquals(FeatureFlag.entries.toSet(), loaded.keys)
        assertTrue(LocalFeaturePreferenceMigration.isReservedCommercialStoredKey("feature_flag.release.account"))
        assertTrue(LocalFeaturePreferenceMigration.isReservedCommercialStoredKey("feature_flag.kill.ads_all"))
        assertTrue(LocalFeaturePreferenceMigration.isReservedCommercialStoredKey("feature_flag.rollout.account_entry"))
        assertTrue(LocalFeaturePreferenceMigration.isReservedCommercialStoredKey("feature_flag.entitlement.pro"))
        assertFalse(LocalFeaturePreferenceMigration.isReservedCommercialStoredKey("feature_flag.Deck"))
    }

    @Test
    fun compiledCommercialOwnerPolicyIsNotAStoredOrRemoteFeatureFlag() {
        assertFalse(PublicReleaseCommercialOwnerPolicy.RELEASE_PREMIUM_ENFORCEMENT_ENABLED)
        assertNull(FeatureFlagRegistry.metadataForStableKey(PublicReleaseCommercialOwnerPolicy.RELEASE_PREMIUM_ENFORCEMENT_KEY))
        assertFalse(
            FeatureFlagRegistry.userPreferenceFlags.any {
                LocalFeaturePreferenceMigration.storedKey(it)
                    .contains(PublicReleaseCommercialOwnerPolicy.RELEASE_PREMIUM_ENFORCEMENT_KEY)
            },
        )
        FeatureFlagRegistry.entries.forEach { metadata ->
            assertFalse(metadata.stableKey.startsWith("release."))
            assertFalse(metadata.stableKey.startsWith("kill."))
            assertFalse(metadata.stableKey.startsWith("entitlement."))
        }
        CommercialSurface.entries.forEach { surface ->
            val decision = CommercialExecutionPolicy.PRODUCTION_DARK.decide(surface)
            assertTrue(decision is CommercialDecision.Denied)
            assertEquals(CommercialDenyReason.BUILD_PRODUCTION_DARK, (decision as CommercialDecision.Denied).reason)
        }
    }

    @Test
    fun remoteConfigCanOnlySubtractAndRejectedValuesFallBackSafely() {
        val metadata = FeatureFlagRegistry.metadata(FeatureFlag.SmartDeck)
        val accepted = SubtractiveFeatureOperationalConfigParser.parse(
            RawSubtractiveFeatureConfig(
                revision = 1L,
                issuedAtEpochMillis = now - 1L,
                expiresAtEpochMillis = now + 1L,
                minAppVersion = 1,
                disabledKeys = setOf(metadata.stableKey),
            ),
            appVersion = 1,
            nowEpochMillis = now,
        )
        assertEquals(
            FeatureDecisionReason.REMOTE_DISABLED,
            FeatureFlagResolver.resolve(metadata, FeatureVariant.PUBLIC_RELEASE, true, accepted).reason,
        )
        assertFalse(FeatureFlagResolver.resolve(metadata, FeatureVariant.PUBLIC_RELEASE, true, accepted).enabled)

        listOf(
            RawSubtractiveFeatureConfig(1L, now - 1L, now + 1L, 1, setOf("unknown.key")) to
                RemoteConfigRejection.UNKNOWN_KEY,
            RawSubtractiveFeatureConfig(1L, now + 1L, now + 2L, 1, emptySet()) to
                RemoteConfigRejection.STALE,
            RawSubtractiveFeatureConfig(1L, now - 2L, now, 1, emptySet()) to
                RemoteConfigRejection.EXPIRED,
            RawSubtractiveFeatureConfig(null, now - 1L, now + 1L, 1, emptySet()) to
                RemoteConfigRejection.MALFORMED,
        ).forEach { (raw, expectedRejection) ->
            val rejected = SubtractiveFeatureOperationalConfigParser.parse(raw, 1, now)
            assertEquals(SubtractiveFeatureConfigParseResult.Rejected(expectedRejection), rejected)
            val decision = FeatureFlagResolver.resolve(metadata, FeatureVariant.PUBLIC_RELEASE, true, rejected)
            assertTrue(decision.enabled)
            assertEquals(FeatureDecisionReason.REMOTE_FALLBACK, decision.reason)
        }

        val staleRevision = SubtractiveFeatureOperationalConfigParser.parse(
            RawSubtractiveFeatureConfig(4L, now - 1L, now + 1L, 1, emptySet()),
            appVersion = 1,
            nowEpochMillis = now,
            minimumRevision = 5L,
        )
        assertEquals(
            SubtractiveFeatureConfigParseResult.Rejected(RemoteConfigRejection.STALE),
            staleRevision,
        )
    }

    @Test
    fun resolverIsMonotonicAcrossEveryOptionalFlagState() {
        val remoteEligible = FeatureFlagRegistry.entries.filter { it.remoteOperationalEligible }
        remoteEligible.forEach { metadata ->
            val disable = SubtractiveFeatureOperationalConfigParser.parse(
                RawSubtractiveFeatureConfig(1L, now - 1L, now + 1L, 1, setOf(metadata.stableKey)),
                appVersion = 1,
                nowEpochMillis = now,
            )
            listOf(false, true).forEach { local ->
                val decision = FeatureFlagResolver.resolve(metadata, FeatureVariant.PUBLIC_RELEASE, local, disable)
                assertFalse("${metadata.stableKey} remote disable must dominate", decision.enabled)
            }
            val noDisable = SubtractiveFeatureOperationalConfigParser.parse(
                RawSubtractiveFeatureConfig(1L, now - 1L, now + 1L, 1, emptySet()),
                appVersion = 1,
                nowEpochMillis = now,
            )
            assertFalse(FeatureFlagResolver.resolve(metadata, FeatureVariant.PUBLIC_RELEASE, false, noDisable).enabled)
        }
    }

    @Test
    fun securityHasNoRemoteSwitchAndDecisionsExposeNoRawRemoteData() {
        assertTrue(FeatureFlagRegistry.entries.none { it.featureClass == FeatureClass.MANDATORY_SECURITY })
        val decision = FeatureFlagResolver.resolve(
            metadata = FeatureFlagRegistry.metadata(FeatureFlag.SmartDeck),
            variant = FeatureVariant.PUBLIC_RELEASE,
            localPreference = null,
            remote = SubtractiveFeatureConfigParseResult.Rejected(RemoteConfigRejection.MALFORMED),
        )
        assertEquals(RemoteConfigRejection.MALFORMED, decision.remoteRejection)
        assertFalse(decision.toString().contains("RawSubtractiveFeatureConfig"))
        assertFalse(decision.toString().contains("disabledKeys"))
    }

    @Test
    fun productionOperationalConfigSourceIsAnInertFailClosedNoOp() {
        assertEquals(
            SubtractiveFeatureConfigParseResult.Rejected(RemoteConfigRejection.MISSING),
            ProductionNoOpFeatureOperationalConfigSource.current(),
        )
    }
}
