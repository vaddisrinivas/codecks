package io.codecks.domain.connection

enum class HostTrustState {
    FirstSeenConfirmationRequired,
    Trusted,
    ChangedHostKeyBlocked,
}

fun evaluateHostTrust(
    savedHostKey: String?,
    observedHostKey: String,
): HostTrustState {
    require(observedHostKey.isNotBlank())
    return when {
        savedHostKey.isNullOrBlank() -> HostTrustState.FirstSeenConfirmationRequired
        savedHostKey == observedHostKey -> HostTrustState.Trusted
        else -> HostTrustState.ChangedHostKeyBlocked
    }
}

class ChangedHostKeyException :
    SecurityException("Mac identity changed. Review the saved Mac trust before reconnecting.")

enum class SshSetupFailureCode(val persistedCode: String) {
    Reachability("ssh_reachability_failed"),
    Authentication("ssh_authentication_failed"),
    KeyMissing("ssh_key_missing"),
    HostKeyMismatch("ssh_host_key_mismatch"),
    ToolMissing("mac_tool_missing"),
    Unknown("unknown"),
}

fun classifySshSetupFailure(message: String?): SshSetupFailureCode {
    val normalized = message.orEmpty().lowercase()
    return when {
        "host key" in normalized ||
            "fingerprint" in normalized ||
            "mac identity changed" in normalized ||
            "remote host identification" in normalized -> SshSetupFailureCode.HostKeyMismatch
        "generate or install" in normalized ||
            "private key" in normalized ||
            "control key" in normalized -> SshSetupFailureCode.KeyMissing
        "permission denied" in normalized ||
            "authentication" in normalized ||
            "auth fail" in normalized ||
            "password" in normalized -> SshSetupFailureCode.Authentication
        "command not found" in normalized ||
            "required tool" in normalized ||
            "missing tool" in normalized -> SshSetupFailureCode.ToolMissing
        "timeout" in normalized ||
            "timed out" in normalized ||
            "no route" in normalized ||
            "unreachable" in normalized ||
            "connection refused" in normalized ||
            "offline" in normalized -> SshSetupFailureCode.Reachability
        else -> SshSetupFailureCode.Unknown
    }
}

enum class MacSetupCapability(val persistedCode: String) {
    Reachability("mac.reachability"),
    HostIdentity("mac.host_identity"),
    Authentication("mac.authentication"),
    RemoteShell("mac.remote_shell"),
    Clipboard("mac.tool.clipboard"),
    Shortcuts("mac.tool.shortcuts"),
    Spotlight("mac.tool.spotlight"),
    Sftp("mac.sftp"),
    Brightness("mac.tool.brightness"),
    Accessibility("mac.accessibility"),
}

enum class MacCapabilityCheckResult(val persistedCode: String) {
    Passed("passed"),
    Failed("failed"),
}

data class MacCapabilityCheckReceipt(
    val capability: MacSetupCapability,
    val result: MacCapabilityCheckResult,
    val failureCode: SshSetupFailureCode?,
    val checkedAtEpochMs: Long,
) {
    init {
        require(checkedAtEpochMs >= 0L)
        require(
            (result == MacCapabilityCheckResult.Passed && failureCode == null) ||
                (result == MacCapabilityCheckResult.Failed && failureCode != null),
        )
    }
}

data class MacCapabilityProbe(
    val capability: MacSetupCapability,
    val stableCommandCode: String,
)

fun MacCapabilityProbe.safeShellCommand(): String = when (capability) {
    MacSetupCapability.Reachability -> "printf codecks-reachable"
    MacSetupCapability.HostIdentity -> "printf codecks-host-pinned"
    MacSetupCapability.Authentication -> "printf codecks-authenticated"
    MacSetupCapability.RemoteShell -> "printf codecks-shell"
    MacSetupCapability.Clipboard -> "command -v pbcopy >/dev/null"
    MacSetupCapability.Shortcuts -> "command -v shortcuts >/dev/null"
    MacSetupCapability.Spotlight -> "command -v mdfind >/dev/null"
    MacSetupCapability.Sftp -> "test -x /usr/libexec/sftp-server"
    MacSetupCapability.Brightness -> "command -v brightness >/dev/null || command -v betterdisplaycli >/dev/null"
    MacSetupCapability.Accessibility ->
        "osascript -e 'tell application \"System Events\" to UI elements enabled' | grep -q true"
}

fun requiredMacCapabilityProbes(
    requiredCapabilities: Set<MacSetupCapability>,
): List<MacCapabilityProbe> = requiredCapabilities
    .sortedBy(MacSetupCapability::persistedCode)
    .map { capability ->
        MacCapabilityProbe(
            capability = capability,
            stableCommandCode = when (capability) {
                MacSetupCapability.Reachability -> "ssh_connect"
                MacSetupCapability.HostIdentity -> "strict_host_key"
                MacSetupCapability.Authentication -> "public_key_auth"
                MacSetupCapability.RemoteShell -> "shell_round_trip"
                MacSetupCapability.Clipboard -> "tool_pbcopy"
                MacSetupCapability.Shortcuts -> "tool_shortcuts"
                MacSetupCapability.Spotlight -> "tool_mdfind"
                MacSetupCapability.Sftp -> "sftp_subsystem"
                MacSetupCapability.Brightness -> "tool_brightness"
                MacSetupCapability.Accessibility -> "accessibility_permission"
            },
        )
    }
