package io.codecks.domain.update

enum class ReleaseIncident {
    BAD_RELEASE,
    CORRUPT_DATA,
    FUTURE_DATA,
    INTERRUPTED_TRANSACTION,
    FAILED_UPGRADE,
    KEY_LOSS,
    COMPROMISED_TOKEN,
    SSH_HOST_KEY_CHANGED,
}

enum class RecoveryAction {
    WITHDRAW_RELEASE,
    FORWARD_FIX,
    PRESERVE_EVIDENCE,
    REFUSE_MUTATION,
    RESTORE_VERIFIED_BACKUP,
    REFUSE_SCHEMA_DOWNGRADE,
    REPAIR_CREDENTIALS_OR_PAIR_AGAIN,
    DISCARD_PARTIAL_ARTIFACT,
    REJECT_UNVERIFIED_CANDIDATE,
    REJECT_DOWNGRADE,
    ABANDON_INSTALL_SESSION,
    REVOKE_AND_ROTATE_TOKEN,
    DISCONNECT_SSH,
    REVERIFY_HOST_IDENTITY,
    ESCALATE_SUPPORT,
}

data class RecoveryDecision(
    val code: String,
    val actions: List<RecoveryAction>,
    val protectedAppDowngradeAllowed: Boolean = false,
)

data class UpdateCandidateState(
    val installedVersionCode: Long,
    val candidateVersionCode: Long,
    val downloadComplete: Boolean,
    val checksumVerified: Boolean,
    val signerVerified: Boolean,
    val sourceVerified: Boolean,
    val installSessionCommitted: Boolean,
)

/**
 * Closed release-incident policy. Rollback of app.codecks is withdrawal plus a
 * forward fix; this policy never authorizes installing an older APK.
 */
object ReleaseRecoveryPolicy {
    fun decide(incident: ReleaseIncident): RecoveryDecision = when (incident) {
        ReleaseIncident.BAD_RELEASE -> RecoveryDecision(
            code = "WITHDRAW_AND_FORWARD_FIX",
            actions = listOf(
                RecoveryAction.WITHDRAW_RELEASE,
                RecoveryAction.PRESERVE_EVIDENCE,
                RecoveryAction.FORWARD_FIX,
                RecoveryAction.ESCALATE_SUPPORT,
            ),
        )
        ReleaseIncident.CORRUPT_DATA -> RecoveryDecision(
            code = "CORRUPT_DATA_REFUSED",
            actions = listOf(
                RecoveryAction.PRESERVE_EVIDENCE,
                RecoveryAction.REFUSE_MUTATION,
                RecoveryAction.RESTORE_VERIFIED_BACKUP,
            ),
        )
        ReleaseIncident.FUTURE_DATA -> RecoveryDecision(
            code = "OLDER_READER_REFUSED",
            actions = listOf(
                RecoveryAction.PRESERVE_EVIDENCE,
                RecoveryAction.REFUSE_MUTATION,
                RecoveryAction.REFUSE_SCHEMA_DOWNGRADE,
            ),
        )
        ReleaseIncident.INTERRUPTED_TRANSACTION -> RecoveryDecision(
            code = "RECOVER_COHERENT_GENERATION",
            actions = listOf(RecoveryAction.PRESERVE_EVIDENCE, RecoveryAction.RESTORE_VERIFIED_BACKUP),
        )
        ReleaseIncident.FAILED_UPGRADE -> RecoveryDecision(
            code = "FAILED_UPGRADE_REFUSED",
            actions = listOf(
                RecoveryAction.PRESERVE_EVIDENCE,
                RecoveryAction.REFUSE_MUTATION,
                RecoveryAction.FORWARD_FIX,
            ),
        )
        ReleaseIncident.KEY_LOSS -> RecoveryDecision(
            code = "KEY_UNAVAILABLE_REPAIR_REQUIRED",
            actions = listOf(
                RecoveryAction.PRESERVE_EVIDENCE,
                RecoveryAction.REFUSE_MUTATION,
                RecoveryAction.REPAIR_CREDENTIALS_OR_PAIR_AGAIN,
            ),
        )
        ReleaseIncident.COMPROMISED_TOKEN -> RecoveryDecision(
            code = "TOKEN_REVOKE_ROTATE",
            actions = listOf(
                RecoveryAction.REVOKE_AND_ROTATE_TOKEN,
                RecoveryAction.PRESERVE_EVIDENCE,
                RecoveryAction.ESCALATE_SUPPORT,
            ),
        )
        ReleaseIncident.SSH_HOST_KEY_CHANGED -> RecoveryDecision(
            code = "SSH_IDENTITY_REVERIFY",
            actions = listOf(
                RecoveryAction.DISCONNECT_SSH,
                RecoveryAction.PRESERVE_EVIDENCE,
                RecoveryAction.REVERIFY_HOST_IDENTITY,
            ),
        )
    }

    fun assessUpdate(candidate: UpdateCandidateState): RecoveryDecision = when {
        candidate.candidateVersionCode <= candidate.installedVersionCode -> RecoveryDecision(
            code = "REJECT_DOWNGRADE",
            actions = listOf(RecoveryAction.PRESERVE_EVIDENCE, RecoveryAction.REJECT_DOWNGRADE),
        )
        !candidate.downloadComplete -> RecoveryDecision(
            code = "DISCARD_PARTIAL_DOWNLOAD",
            actions = listOf(RecoveryAction.PRESERVE_EVIDENCE, RecoveryAction.DISCARD_PARTIAL_ARTIFACT),
        )
        !candidate.checksumVerified || !candidate.signerVerified || !candidate.sourceVerified -> RecoveryDecision(
            code = "REJECT_UNVERIFIED_CANDIDATE",
            actions = listOf(
                RecoveryAction.PRESERVE_EVIDENCE,
                RecoveryAction.REJECT_UNVERIFIED_CANDIDATE,
                RecoveryAction.ESCALATE_SUPPORT,
            ),
        )
        !candidate.installSessionCommitted -> RecoveryDecision(
            code = "ABANDON_PARTIAL_INSTALL_SESSION",
            actions = listOf(RecoveryAction.PRESERVE_EVIDENCE, RecoveryAction.ABANDON_INSTALL_SESSION),
        )
        else -> RecoveryDecision(
            code = "VERIFIED_UPDATE_COMMITTED",
            actions = emptyList(),
        )
    }
}
