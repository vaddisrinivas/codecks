package io.codecks.domain.targets

import kotlinx.serialization.Serializable

@Serializable
enum class TargetCapability {
    SSH_APP_CONTROL,
    SSH_SYSTEM_CONTROL,
    HID_POINTER,
    HID_KEYBOARD,
    HID_CONSUMER_MEDIA,
    OPEN_URL,
}

@Serializable
enum class TrustState {
    UNTRUSTED,
    TRUSTED,
    HOST_KEY_CHANGED,
    REJECTED,
}

@Serializable
enum class LiveState {
    CONFIGURED,
    CONNECTING,
    ONLINE,
    DEGRADED,
    OFFLINE,
    AUTH_FAILED,
    HOST_KEY_CHANGED,
}

@Serializable
enum class TargetSetupStepId {
    NAME_TARGET,
    ENTER_ADDRESS,
    TRUST_FINGERPRINT,
    INSTALL_KEY,
    TEST_FINDER,
}

@Serializable
enum class TargetSetupStepState {
    TODO,
    ACTIVE,
    DONE,
    BLOCKED,
}

@Serializable
data class TargetSetupStep(
    val id: TargetSetupStepId,
    val title: String,
    val detail: String,
    val state: TargetSetupStepState,
)

@Serializable
data class SshIdentity(
    val host: String,
    val port: Int = 22,
    val username: String,
    val hostFingerprintSha256: String? = null,
    val phonePublicKey: String? = null,
)

@Serializable
data class TargetSetupDraft(
    val targetName: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val fingerprintSha256: String? = null,
    val fingerprintTrusted: Boolean = false,
    val phonePublicKey: String? = null,
    val keyInstalled: Boolean = false,
    val finderProofSucceeded: Boolean = false,
) {
    val hasName: Boolean get() = targetName.isNotBlank()
    val hasAddress: Boolean get() = host.isNotBlank() && username.isNotBlank() && port in 1..65_535
    val hasFingerprint: Boolean get() = !fingerprintSha256.isNullOrBlank()

    fun trustFingerprint(): TargetSetupDraft {
        require(hasAddress) { "Address and username required before host trust" }
        require(hasFingerprint) { "Host fingerprint required before trust" }
        return copy(fingerprintTrusted = true)
    }

    fun installPhoneKey(publicKey: String): TargetSetupDraft {
        require(fingerprintTrusted) { "Host fingerprint must be trusted before key install" }
        require(publicKey.isNotBlank()) { "Phone public key required" }
        return copy(phonePublicKey = publicKey, keyInstalled = true)
    }

    fun markFinderProofSucceeded(): TargetSetupDraft {
        require(keyInstalled) { "Phone key must be installed before Finder proof" }
        return copy(finderProofSucceeded = true)
    }

    fun toMacTarget(
        logicalId: String = "current-mac",
        liveState: LiveState = if (finderProofSucceeded) LiveState.ONLINE else LiveState.CONFIGURED,
    ): MacTarget {
        require(keyInstalled) { "Phone key must be installed" }
        return toTrustedSshTarget(logicalId = logicalId, liveState = liveState)
    }

    fun toTrustedSshTarget(
        logicalId: String = "current-mac",
        liveState: LiveState = LiveState.CONFIGURED,
    ): MacTarget {
        require(hasName) { "Target name required" }
        require(hasAddress) { "Host, port, and username required" }
        require(fingerprintTrusted) { "Host fingerprint must be trusted" }
        return MacTarget(
            logicalId = logicalId,
            displayName = targetName,
            sshIdentity = SshIdentity(
                host = host,
                port = port,
                username = username,
                hostFingerprintSha256 = fingerprintSha256,
                phonePublicKey = phonePublicKey,
            ),
            capabilities = setOf(TargetCapability.SSH_APP_CONTROL, TargetCapability.SSH_SYSTEM_CONTROL),
            trustState = TrustState.TRUSTED,
            liveState = liveState,
        )
    }
}

object TargetSetupPolicy {
    fun checklist(draft: TargetSetupDraft): List<TargetSetupStep> = listOf(
        TargetSetupStep(
            id = TargetSetupStepId.NAME_TARGET,
            title = "Name target",
            detail = "Give this Mac a name you will recognize.",
            state = when {
                draft.hasName -> TargetSetupStepState.DONE
                else -> TargetSetupStepState.ACTIVE
            },
        ),
        TargetSetupStep(
            id = TargetSetupStepId.ENTER_ADDRESS,
            title = "Enter or find address",
            detail = "Use local IP, hostname, or discovery.",
            state = when {
                draft.hasAddress -> TargetSetupStepState.DONE
                draft.hasName -> TargetSetupStepState.ACTIVE
                else -> TargetSetupStepState.TODO
            },
        ),
        TargetSetupStep(
            id = TargetSetupStepId.TRUST_FINGERPRINT,
            title = "Trust fingerprint",
            detail = "Compare the SHA-256 host fingerprint shown on the Mac.",
            state = when {
                draft.fingerprintTrusted -> TargetSetupStepState.DONE
                draft.hasAddress && draft.hasFingerprint -> TargetSetupStepState.ACTIVE
                draft.hasAddress -> TargetSetupStepState.BLOCKED
                else -> TargetSetupStepState.TODO
            },
        ),
        TargetSetupStep(
            id = TargetSetupStepId.INSTALL_KEY,
            title = "Install phone key",
            detail = "Password is only used for one-time public-key install.",
            state = when {
                draft.keyInstalled -> TargetSetupStepState.DONE
                draft.fingerprintTrusted -> TargetSetupStepState.ACTIVE
                else -> TargetSetupStepState.TODO
            },
        ),
        TargetSetupStep(
            id = TargetSetupStepId.TEST_FINDER,
            title = "Test Finder",
            detail = "Open Finder as a harmless proof action.",
            state = when {
                draft.finderProofSucceeded -> TargetSetupStepState.DONE
                draft.keyInstalled -> TargetSetupStepState.ACTIVE
                else -> TargetSetupStepState.TODO
            },
        ),
    )
}

@Serializable
data class HidIdentity(
    val hostName: String,
    val bluetoothAddress: String? = null,
    val lastSelected: Boolean = false,
)

@Serializable
data class MacTarget(
    val logicalId: String,
    val displayName: String,
    val sshIdentity: SshIdentity?,
    val hidIdentity: HidIdentity? = null,
    val capabilities: Set<TargetCapability>,
    val trustState: TrustState,
    val liveState: LiveState,
) {
    fun supports(required: Set<TargetCapability>): Boolean = capabilities.containsAll(required)
}

@Serializable
sealed interface TargetSelection {
    @Serializable
    data object Current : TargetSelection

    @Serializable
    data class Specific(val logicalId: String) : TargetSelection

    @Serializable
    data class Group(val groupId: String) : TargetSelection

    @Serializable
    data object All : TargetSelection

    @Serializable
    data object Ask : TargetSelection
}
