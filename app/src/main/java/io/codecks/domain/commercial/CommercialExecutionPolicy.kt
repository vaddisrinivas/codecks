package io.codecks.domain.commercial

/** Commercial capability only. Local Deck, Trackpad, Keyboard, Clipboard, and SSH are out of scope. */
enum class CommercialSurface {
    ACCOUNT,
    CLOUD_SYNC,
    PLAY_BILLING,
    PREMIUM_ENFORCEMENT,
    ADS,
}

/** Compile-time capability. External configuration must never create this value. */
internal enum class BuildCapability(internal val allowsExecution: Boolean) {
    ABSENT(false),
    PRODUCTION_DARK(false),
    INTERNAL_TEST_CAPABLE(true),
    PRODUCTION_CAPABLE(true),
}

internal enum class OwnerActivation(internal val allowsExecution: Boolean) {
    DISABLED(false),
    APPROVED(true),
}

internal enum class Compatibility(internal val allowsExecution: Boolean) {
    UNKNOWN(false),
    UNSUPPORTED(false),
    SUPPORTED(true),
    ;

    companion object {
        fun fromExternal(value: String?): Compatibility = when (value?.trim()?.lowercase()) {
            "supported" -> SUPPORTED
            "unsupported" -> UNSUPPORTED
            else -> UNKNOWN
        }
    }
}

internal enum class EmergencyDeny(internal val allowsExecution: Boolean) {
    UNKNOWN(false),
    ACTIVE(false),
    CLEAR(true),
    ;

    companion object {
        fun fromExternal(denyActive: Boolean?): EmergencyDeny = when (denyActive) {
            false -> CLEAR
            true -> ACTIVE
            null -> UNKNOWN
        }
    }
}

internal enum class RolloutAssignment(internal val allowsExecution: Boolean) {
    UNKNOWN(false),
    EXCLUDED(false),
    INCLUDED(true),
    ;

    companion object {
        fun fromExternal(included: Boolean?): RolloutAssignment = when (included) {
            true -> INCLUDED
            false -> EXCLUDED
            null -> UNKNOWN
        }
    }
}

/** Only a trusted entitlement adapter may produce SERVER_VERIFIED_ALLOWED. */
internal enum class EntitlementProjection(internal val allowsExecution: Boolean) {
    UNKNOWN(false),
    SERVER_VERIFIED_DENIED(false),
    NOT_REQUIRED(true),
    SERVER_VERIFIED_ALLOWED(true),
    ;

    companion object {
        fun fromServerVerification(verified: Boolean, allowed: Boolean): EntitlementProjection = when {
            !verified -> UNKNOWN
            allowed -> SERVER_VERIFIED_ALLOWED
            else -> SERVER_VERIFIED_DENIED
        }
    }
}

internal enum class ConsentState(internal val allowsExecution: Boolean) {
    UNKNOWN(false),
    DENIED(false),
    NOT_REQUIRED(true),
    GRANTED(true),
    ;

    companion object {
        fun fromExternal(value: String?): ConsentState = when (value?.trim()?.lowercase()) {
            "granted" -> GRANTED
            "denied" -> DENIED
            "not_required" -> NOT_REQUIRED
            else -> UNKNOWN
        }
    }
}

internal enum class UserOptIn(internal val allowsExecution: Boolean) {
    UNKNOWN(false),
    DECLINED(false),
    NOT_REQUIRED(true),
    ACCEPTED(true),
    ;

    companion object {
        fun fromExternal(value: Boolean?, required: Boolean): UserOptIn = when {
            !required -> NOT_REQUIRED
            value == true -> ACCEPTED
            value == false -> DECLINED
            else -> UNKNOWN
        }
    }
}

enum class CommercialDecisionSource {
    BUILD,
    OWNER,
    COMPATIBILITY,
    EMERGENCY_DENY,
    ROLLOUT,
    ENTITLEMENT,
    CONSENT,
    USER_OPT_IN,
    ALL_GATES,
}

enum class CommercialDenyReason {
    BUILD_ABSENT,
    BUILD_PRODUCTION_DARK,
    OWNER_DISABLED,
    COMPATIBILITY_UNKNOWN,
    COMPATIBILITY_UNSUPPORTED,
    EMERGENCY_STATE_UNKNOWN,
    EMERGENCY_DENY_ACTIVE,
    ROLLOUT_UNKNOWN,
    ROLLOUT_EXCLUDED,
    ENTITLEMENT_UNKNOWN,
    ENTITLEMENT_DENIED,
    CONSENT_UNKNOWN,
    CONSENT_DENIED,
    USER_OPT_IN_UNKNOWN,
    USER_DECLINED,
}

sealed interface CommercialDecision {
    val surface: CommercialSurface
    val source: CommercialDecisionSource

    data class Allowed(
        override val surface: CommercialSurface,
        override val source: CommercialDecisionSource = CommercialDecisionSource.ALL_GATES,
    ) : CommercialDecision

    data class Denied(
        override val surface: CommercialSurface,
        override val source: CommercialDecisionSource,
        val reason: CommercialDenyReason,
    ) : CommercialDecision
}

internal data class CommercialGateState(
    val buildCapability: BuildCapability,
    val ownerActivation: OwnerActivation,
    val compatibility: Compatibility,
    val emergencyDeny: EmergencyDeny,
    val rolloutAssignment: RolloutAssignment,
    val entitlementProjection: EntitlementProjection,
    val consentState: ConsentState,
    val userOptIn: UserOptIn,
)

/**
 * Monotonic resolver: every gate must allow. No lower-trust allow can override an earlier deny.
 */
internal object CommercialPolicyResolver {
    fun decide(surface: CommercialSurface, state: CommercialGateState): CommercialDecision {
        denyForBuild(surface, state.buildCapability)?.let { return it }
        if (!state.ownerActivation.allowsExecution) {
            return denied(surface, CommercialDecisionSource.OWNER, CommercialDenyReason.OWNER_DISABLED)
        }
        when (state.compatibility) {
            Compatibility.UNKNOWN -> return denied(
                surface,
                CommercialDecisionSource.COMPATIBILITY,
                CommercialDenyReason.COMPATIBILITY_UNKNOWN,
            )
            Compatibility.UNSUPPORTED -> return denied(
                surface,
                CommercialDecisionSource.COMPATIBILITY,
                CommercialDenyReason.COMPATIBILITY_UNSUPPORTED,
            )
            Compatibility.SUPPORTED -> Unit
        }
        when (state.emergencyDeny) {
            EmergencyDeny.UNKNOWN -> return denied(
                surface,
                CommercialDecisionSource.EMERGENCY_DENY,
                CommercialDenyReason.EMERGENCY_STATE_UNKNOWN,
            )
            EmergencyDeny.ACTIVE -> return denied(
                surface,
                CommercialDecisionSource.EMERGENCY_DENY,
                CommercialDenyReason.EMERGENCY_DENY_ACTIVE,
            )
            EmergencyDeny.CLEAR -> Unit
        }
        when (state.rolloutAssignment) {
            RolloutAssignment.UNKNOWN -> return denied(
                surface,
                CommercialDecisionSource.ROLLOUT,
                CommercialDenyReason.ROLLOUT_UNKNOWN,
            )
            RolloutAssignment.EXCLUDED -> return denied(
                surface,
                CommercialDecisionSource.ROLLOUT,
                CommercialDenyReason.ROLLOUT_EXCLUDED,
            )
            RolloutAssignment.INCLUDED -> Unit
        }
        when (state.entitlementProjection) {
            EntitlementProjection.UNKNOWN -> return denied(
                surface,
                CommercialDecisionSource.ENTITLEMENT,
                CommercialDenyReason.ENTITLEMENT_UNKNOWN,
            )
            EntitlementProjection.SERVER_VERIFIED_DENIED -> return denied(
                surface,
                CommercialDecisionSource.ENTITLEMENT,
                CommercialDenyReason.ENTITLEMENT_DENIED,
            )
            EntitlementProjection.NOT_REQUIRED,
            EntitlementProjection.SERVER_VERIFIED_ALLOWED,
            -> Unit
        }
        when (state.consentState) {
            ConsentState.UNKNOWN -> return denied(
                surface,
                CommercialDecisionSource.CONSENT,
                CommercialDenyReason.CONSENT_UNKNOWN,
            )
            ConsentState.DENIED -> return denied(
                surface,
                CommercialDecisionSource.CONSENT,
                CommercialDenyReason.CONSENT_DENIED,
            )
            ConsentState.NOT_REQUIRED,
            ConsentState.GRANTED,
            -> Unit
        }
        when (state.userOptIn) {
            UserOptIn.UNKNOWN -> return denied(
                surface,
                CommercialDecisionSource.USER_OPT_IN,
                CommercialDenyReason.USER_OPT_IN_UNKNOWN,
            )
            UserOptIn.DECLINED -> return denied(
                surface,
                CommercialDecisionSource.USER_OPT_IN,
                CommercialDenyReason.USER_DECLINED,
            )
            UserOptIn.NOT_REQUIRED,
            UserOptIn.ACCEPTED,
            -> Unit
        }
        return CommercialDecision.Allowed(surface)
    }

    private fun denyForBuild(
        surface: CommercialSurface,
        capability: BuildCapability,
    ): CommercialDecision.Denied? = when (capability) {
        BuildCapability.ABSENT -> denied(
            surface,
            CommercialDecisionSource.BUILD,
            CommercialDenyReason.BUILD_ABSENT,
        )
        BuildCapability.PRODUCTION_DARK -> denied(
            surface,
            CommercialDecisionSource.BUILD,
            CommercialDenyReason.BUILD_PRODUCTION_DARK,
        )
        BuildCapability.INTERNAL_TEST_CAPABLE,
        BuildCapability.PRODUCTION_CAPABLE,
        -> null
    }

    private fun denied(
        surface: CommercialSurface,
        source: CommercialDecisionSource,
        reason: CommercialDenyReason,
    ) = CommercialDecision.Denied(surface, source, reason)
}

/** Immutable public-release policy. Callers cannot supply an alternate gate state. */
class CommercialExecutionPolicy private constructor(
    private val state: CommercialGateState,
) {
    fun decide(surface: CommercialSurface): CommercialDecision = CommercialPolicyResolver.decide(surface, state)

    companion object {
        val PRODUCTION_DARK = CommercialExecutionPolicy(
            CommercialGateState(
                buildCapability = BuildCapability.PRODUCTION_DARK,
                ownerActivation = OwnerActivation.DISABLED,
                compatibility = Compatibility.UNKNOWN,
                emergencyDeny = EmergencyDeny.UNKNOWN,
                rolloutAssignment = RolloutAssignment.UNKNOWN,
                entitlementProjection = EntitlementProjection.UNKNOWN,
                consentState = ConsentState.UNKNOWN,
                userOptIn = UserOptIn.UNKNOWN,
            ),
        )

        /**
         * Compile-time source-set seam for a future playInternal variant.
         * Runtime adapters cannot call this from outside the application module.
         */
        internal fun forCompiledVariant(state: CommercialGateState): CommercialExecutionPolicy =
            CommercialExecutionPolicy(state)
    }
}
