package io.codecks.domain.features

enum class RemoteConfigRejection {
    MISSING,
    MALFORMED,
    STALE,
    EXPIRED,
    UNKNOWN_KEY,
    INELIGIBLE_KEY,
}

data class RawSubtractiveFeatureConfig(
    val revision: Long?,
    val issuedAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val minAppVersion: Int?,
    val disabledKeys: Set<String>?,
)

sealed interface SubtractiveFeatureConfigParseResult {
    data class Accepted(
        val revision: Long,
        val disabledKeys: Set<String>,
    ) : SubtractiveFeatureConfigParseResult

    data class Rejected(val reason: RemoteConfigRejection) : SubtractiveFeatureConfigParseResult
}

/**
 * Source seam only. The public build implementation is intentionally inert:
 * no SDK construction, startup work, cache, network, or preference reads.
 */
interface FeatureOperationalConfigSource {
    fun current(): SubtractiveFeatureConfigParseResult
}

object ProductionNoOpFeatureOperationalConfigSource : FeatureOperationalConfigSource {
    override fun current(): SubtractiveFeatureConfigParseResult =
        SubtractiveFeatureConfigParseResult.Rejected(RemoteConfigRejection.MISSING)
}

object SubtractiveFeatureOperationalConfigParser {
    fun parse(
        raw: RawSubtractiveFeatureConfig?,
        appVersion: Int,
        nowEpochMillis: Long,
        minimumRevision: Long = 0L,
    ): SubtractiveFeatureConfigParseResult {
        if (raw == null) return SubtractiveFeatureConfigParseResult.Rejected(RemoteConfigRejection.MISSING)
        val revision = raw.revision ?: return rejected(RemoteConfigRejection.MALFORMED)
        val issuedAt = raw.issuedAtEpochMillis ?: return rejected(RemoteConfigRejection.MALFORMED)
        val expiresAt = raw.expiresAtEpochMillis ?: return rejected(RemoteConfigRejection.MALFORMED)
        val minVersion = raw.minAppVersion ?: return rejected(RemoteConfigRejection.MALFORMED)
        val disabledKeys = raw.disabledKeys ?: return rejected(RemoteConfigRejection.MALFORMED)
        if (revision < 0L || minimumRevision < 0L || issuedAt < 0L || expiresAt < issuedAt || minVersion < 1) {
            return rejected(RemoteConfigRejection.MALFORMED)
        }
        if (revision < minimumRevision) return rejected(RemoteConfigRejection.STALE)
        if (issuedAt > nowEpochMillis) return rejected(RemoteConfigRejection.STALE)
        if (expiresAt <= nowEpochMillis || minVersion > appVersion) return rejected(RemoteConfigRejection.EXPIRED)
        disabledKeys.forEach { key ->
            val metadata = FeatureFlagRegistry.metadataForStableKey(key)
                ?: return rejected(RemoteConfigRejection.UNKNOWN_KEY)
            if (!metadata.remoteOperationalEligible) return rejected(RemoteConfigRejection.INELIGIBLE_KEY)
        }
        return SubtractiveFeatureConfigParseResult.Accepted(revision, disabledKeys.toSet())
    }

    private fun rejected(reason: RemoteConfigRejection) = SubtractiveFeatureConfigParseResult.Rejected(reason)
}

enum class FeatureDecisionReason {
    DEFAULT,
    LOCAL_PREFERENCE,
    REMOTE_DISABLED,
    REMOTE_FALLBACK,
}

data class FeatureDecision(
    val stableKey: String,
    val enabled: Boolean,
    val reason: FeatureDecisionReason,
    val remoteRejection: RemoteConfigRejection? = null,
)

/** Pure resolver: local state chooses the product baseline; remote config can only subtract. */
object FeatureFlagResolver {
    fun resolve(
        metadata: FeatureFlagMetadata,
        variant: FeatureVariant,
        localPreference: Boolean?,
        remote: SubtractiveFeatureConfigParseResult,
    ): FeatureDecision {
        val baseline = localPreference ?: metadata.defaultFor(variant)
        val baselineReason = if (localPreference == null) {
            FeatureDecisionReason.DEFAULT
        } else {
            FeatureDecisionReason.LOCAL_PREFERENCE
        }
        return when (remote) {
            is SubtractiveFeatureConfigParseResult.Accepted -> {
                if (metadata.remoteOperationalEligible && metadata.stableKey in remote.disabledKeys) {
                    FeatureDecision(metadata.stableKey, enabled = false, FeatureDecisionReason.REMOTE_DISABLED)
                } else {
                    FeatureDecision(metadata.stableKey, enabled = baseline, baselineReason)
                }
            }
            is SubtractiveFeatureConfigParseResult.Rejected -> FeatureDecision(
                stableKey = metadata.stableKey,
                enabled = baseline,
                reason = FeatureDecisionReason.REMOTE_FALLBACK,
                remoteRejection = remote.reason,
            )
        }
    }
}

object LocalFeaturePreferenceMigration {
    const val CURRENT_SCHEMA_VERSION = 6

    /** Ignores wrong-type/corrupt/unknown values instead of letting persisted data change behavior. */
    fun load(
        storedValues: Map<String, Any?>,
        defaults: Map<FeatureFlag, Boolean>,
    ): Map<FeatureFlag, Boolean> = FeatureFlagRegistry.userPreferenceFlags.associateWith { flag ->
        val metadata = FeatureFlagRegistry.metadata(flag)
        (storedValues[storedKey(flag)] as? Boolean) ?: (defaults[flag] == true)
    }

    fun storedKey(flag: FeatureFlag): String = "feature_flag.${flag.name}"

    /** Commercial authority is never read from or retained in user preferences. */
    fun isReservedCommercialStoredKey(key: String): Boolean = RESERVED_COMMERCIAL_PREFIXES.any(key::startsWith)

    private val RESERVED_COMMERCIAL_PREFIXES = listOf(
        "feature_flag.release.",
        "feature_flag.kill.",
        "feature_flag.entitlement.",
        "feature_flag.rollout.",
    )
}
