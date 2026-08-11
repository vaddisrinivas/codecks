package io.codecks.ui.connection

import io.codecks.platform.helper.ReactiveHelperSessionStatus
import io.codecks.ui.clipboard.ClipboardUiState

enum class ConnectionTransport(val supportPrefix: String) {
    Hid("HID"),
    Ssh("SSH"),
    Helper("HLP"),
    Clipboard("CLP"),
}

/** Canonical M06 support-code registry. Codes are created only by typed connection presentations. */
enum class ConnectionSupportCode(val value: String) {
    HidPermission("CX-HID-PERM"), HidBluetoothOff("CX-HID-BT-OFF"), HidStopped("CX-HID-STOP"),
    HidNoMac("CX-HID-NO-MAC"), HidConnecting("CX-HID-CONNECT"), HidRetrying("CX-HID-RETRY"),
    HidReady("CX-HID-READY"), HidConnected("CX-HID-OK"), HidFailed("CX-HID-FAIL"),
    SshToolMissing("CX-SSH-TOOL"), SshFailed("CX-SSH-FAIL"), SshSetup("CX-SSH-SETUP"),
    SshNoPin("CX-SSH-NO-PIN"), SshNoKey("CX-SSH-NO-KEY"), SshChecking("CX-SSH-CHECK"),
    SshReady("CX-SSH-OK"), SshAuthentication("CX-SSH-AUTH"), SshHostKey("CX-SSH-HOSTKEY"),
    SshRetrying("CX-SSH-RETRY"), SshSleeping("CX-SSH-SLEEP"),
    HelperSetup("CX-HLP-SETUP"), HelperIdle("CX-HLP-IDLE"), HelperNoEndpoint("CX-HLP-NO-ENDPOINT"),
    HelperConnecting("CX-HLP-CONNECT"), HelperConnected("CX-HLP-OK"), HelperAuthentication("CX-HLP-AUTH"),
    HelperIdentity("CX-HLP-IDENTITY"), HelperRetrying("CX-HLP-RETRY"), HelperFailed("CX-HLP-FAIL"),
    ClipboardChecking("CX-CLP-CHECK"), ClipboardConflict("CX-CLP-CONFLICT"),
    ClipboardSleeping("CX-CLP-SLEEP"), ClipboardSetup("CX-CLP-SETUP"),
    ClipboardFailed("CX-CLP-FAIL"), ClipboardReady("CX-CLP-OK"),
}

internal val M06_ACTIONABLE_SUPPORT_CODES: Set<String> =
    ConnectionSupportCode.entries.mapTo(linkedSetOf()) { it.value }

enum class ConnectionPresentationState {
    SetupRequired,
    PermissionRequired,
    Checking,
    Reconnecting,
    Ready,
    Offline,
    Sleeping,
    AuthenticationFailed,
    IdentityMismatch,
    Conflict,
    Failed,
}

enum class ConnectionRepair(val label: String) {
    RequestPermission("Allow Bluetooth"),
    OpenBluetoothSettings("Open Bluetooth settings"),
    PairMac("Pair a Mac"),
    OpenConnectionSetup("Open Mac setup"),
    RetryNow("Retry now"),
    ReenterCredentials("Re-enter credentials"),
    ReviewIdentity("Review Mac identity"),
    PairHelper("Pair Codecks helper"),
    OpenHelper("Open Codecks helper"),
    ResolveConflict("Resolve clipboard conflict"),
    ContactSupport("Open support"),
}

data class UnifiedConnectionPresentation(
    val transport: ConnectionTransport,
    val state: ConnectionPresentationState,
    val statusLabel: String,
    val title: String,
    val detail: String,
    val supportCode: ConnectionSupportCode,
    val repairs: List<ConnectionRepair> = emptyList(),
) {
    val isReady: Boolean get() = state == ConnectionPresentationState.Ready

    init {
        require(supportCode.value in M06_ACTIONABLE_SUPPORT_CODES)
        require(supportCode.value.startsWith("CX-${transport.supportPrefix}-"))
    }
}

fun HidHealth.toUnifiedConnectionPresentation(): UnifiedConnectionPresentation = when (kind) {
    HidHealthKind.PermissionMissing -> presentation(
        ConnectionTransport.Hid, ConnectionPresentationState.PermissionRequired, "Setup needed",
        "Bluetooth permission needed", "Allow Bluetooth so Codecks can send mouse and keyboard input.", ConnectionSupportCode.HidPermission,
        ConnectionRepair.RequestPermission,
    )
    HidHealthKind.Unavailable -> presentation(
        ConnectionTransport.Hid, ConnectionPresentationState.Offline, "Offline",
        "Bluetooth unavailable", "Turn on Bluetooth, then retry Trackpad input.", ConnectionSupportCode.HidBluetoothOff,
        ConnectionRepair.OpenBluetoothSettings, ConnectionRepair.RetryNow,
    )
    HidHealthKind.Stopped -> presentation(
        ConnectionTransport.Hid, ConnectionPresentationState.SetupRequired, "Setup needed",
        "Bluetooth input stopped", "Start Bluetooth input before using Trackpad or Keyboard.", ConnectionSupportCode.HidStopped,
        ConnectionRepair.RetryNow,
    )
    HidHealthKind.ReadyNoTarget -> presentation(
        ConnectionTransport.Hid, ConnectionPresentationState.SetupRequired, "Setup needed",
        "No Mac selected", "Pair or choose the Mac that should receive input.", ConnectionSupportCode.HidNoMac,
        ConnectionRepair.PairMac,
    )
    HidHealthKind.Starting, HidHealthKind.Connecting -> presentation(
        ConnectionTransport.Hid, ConnectionPresentationState.Checking, "Connecting…",
        "Connecting Bluetooth input", "Codecks is opening the Bluetooth input channel.", ConnectionSupportCode.HidConnecting,
    )
    HidHealthKind.Reconnecting -> presentation(
        ConnectionTransport.Hid, ConnectionPresentationState.Reconnecting, "Reconnecting…",
        "Bluetooth input reconnecting", "Codecks will keep retrying while its foreground session is active.", ConnectionSupportCode.HidRetrying,
        ConnectionRepair.RetryNow,
    )
    HidHealthKind.ReadyToConnect -> presentation(
        ConnectionTransport.Hid, ConnectionPresentationState.Offline, "Ready",
        "Mac is not connected", "Bluetooth input is ready. Reconnect the selected Mac.", ConnectionSupportCode.HidReady,
        ConnectionRepair.RetryNow,
    )
    HidHealthKind.Connected -> presentation(
        ConnectionTransport.Hid, ConnectionPresentationState.Ready, "Connected",
        "Trackpad connected", "Mouse and keyboard input can be sent to the selected Mac.", ConnectionSupportCode.HidConnected,
    )
    HidHealthKind.Failed -> presentation(
        ConnectionTransport.Hid, ConnectionPresentationState.Failed, "Failed",
        "Bluetooth input failed", "Restart Bluetooth input. Export diagnostics if it fails again.", ConnectionSupportCode.HidFailed,
        ConnectionRepair.RetryNow, ConnectionRepair.ContactSupport,
    )
}

fun ConnectionHealth.toUnifiedConnectionPresentation(): UnifiedConnectionPresentation {
    if (issueCode == io.codecks.domain.connection.ConnectionIssueCode.MAC_TOOL_MISSING) return presentation(
        ConnectionTransport.Ssh, ConnectionPresentationState.SetupRequired, "Tool needed",
        "Required Mac tool is missing", "Install the required Mac tool, then test again.", ConnectionSupportCode.SshToolMissing,
        ConnectionRepair.OpenConnectionSetup,
    )
    if (issueCode == io.codecks.domain.connection.ConnectionIssueCode.UNKNOWN) return presentation(
        ConnectionTransport.Ssh, ConnectionPresentationState.Failed, "Needs attention",
        "Mac connection needs attention", "Retry or export redacted diagnostics for support.", ConnectionSupportCode.SshFailed,
        ConnectionRepair.RetryNow, ConnectionRepair.ContactSupport,
    )
    return when (kind) {
    ConnectionHealthKind.NotConfigured -> sshSetup("Mac not configured", "Add your Mac before using Mac controls.", ConnectionSupportCode.SshSetup)
    ConnectionHealthKind.NeedsFingerprint -> presentation(
        ConnectionTransport.Ssh, ConnectionPresentationState.IdentityMismatch, "Trust needed",
        "Mac identity not confirmed", "Confirm the Mac fingerprint before saving credentials.", ConnectionSupportCode.SshNoPin,
        ConnectionRepair.ReviewIdentity,
    )
    ConnectionHealthKind.NeedsKey -> sshSetup("Control key missing", "Install the Codecks control key once.", ConnectionSupportCode.SshNoKey)
    ConnectionHealthKind.Scanning, ConnectionHealthKind.Verifying,
    ConnectionHealthKind.Connecting, ConnectionHealthKind.Testing -> presentation(
        ConnectionTransport.Ssh, ConnectionPresentationState.Checking, "Checking…",
        "Checking Mac controls", "Codecks is verifying the saved Mac connection.", ConnectionSupportCode.SshChecking,
    )
    ConnectionHealthKind.Ready -> presentation(
        ConnectionTransport.Ssh, ConnectionPresentationState.Ready, "Ready",
        "Mac controls ready", "The saved Mac identity and control key are active.", ConnectionSupportCode.SshReady,
    )
    ConnectionHealthKind.AuthFailed -> presentation(
        ConnectionTransport.Ssh, ConnectionPresentationState.AuthenticationFailed, "Authentication failed",
        "Mac login failed", "The saved username or control key was rejected.", ConnectionSupportCode.SshAuthentication,
        ConnectionRepair.ReenterCredentials,
    )
    ConnectionHealthKind.FingerprintMismatch -> presentation(
        ConnectionTransport.Ssh, ConnectionPresentationState.IdentityMismatch, "Identity changed",
        "Mac identity changed", "Do not reconnect until you confirm this is the same Mac.", ConnectionSupportCode.SshHostKey,
        ConnectionRepair.ReviewIdentity,
    )
    ConnectionHealthKind.Offline -> if (issueCode == io.codecks.domain.connection.ConnectionIssueCode.CONNECT_BACKOFF) {
        presentation(
            ConnectionTransport.Ssh, ConnectionPresentationState.Reconnecting, "Reconnecting…",
            "Waiting before retry", "Codecks will retry the saved Mac connection.", ConnectionSupportCode.SshRetrying,
            ConnectionRepair.RetryNow,
        )
    } else {
        presentation(
            ConnectionTransport.Ssh, ConnectionPresentationState.Sleeping, "Offline",
            "Mac may be asleep", "Wake the Mac and check Wi-Fi and Remote Login.", ConnectionSupportCode.SshSleeping,
            ConnectionRepair.RetryNow,
        )
    }
    }
}

fun ReactiveHelperSessionStatus.toUnifiedConnectionPresentation(
    paired: Boolean = true,
    endpointAvailable: Boolean = true,
): UnifiedConnectionPresentation {
    if (!paired) return presentation(
        ConnectionTransport.Helper, ConnectionPresentationState.SetupRequired, "Not paired",
        "Helper pairing needed", "Import pairing from the Codecks Mac helper.", ConnectionSupportCode.HelperSetup,
        ConnectionRepair.PairHelper,
    )
    return when (this) {
        ReactiveHelperSessionStatus.Idle -> presentation(
            ConnectionTransport.Helper,
            if (endpointAvailable) ConnectionPresentationState.Offline else ConnectionPresentationState.SetupRequired,
            if (endpointAvailable) "Ready to connect" else "Helper unavailable",
            if (endpointAvailable) "Helper is disconnected" else "Open Codecks helper",
            if (endpointAvailable) "Connect the authenticated helper session." else "Open Codecks helper on the Mac.",
            if (endpointAvailable) ConnectionSupportCode.HelperIdle else ConnectionSupportCode.HelperNoEndpoint,
            if (endpointAvailable) ConnectionRepair.RetryNow else ConnectionRepair.OpenHelper,
        )
        is ReactiveHelperSessionStatus.Connecting -> presentation(
            ConnectionTransport.Helper, ConnectionPresentationState.Checking, "Connecting…",
            "Helper connecting", "Codecks is authenticating the pinned helper identity.", ConnectionSupportCode.HelperConnecting,
        )
        is ReactiveHelperSessionStatus.Connected -> presentation(
            ConnectionTransport.Helper, ConnectionPresentationState.Ready, "Connected",
            "Helper connected", "Authenticated helper actions are available.", ConnectionSupportCode.HelperConnected,
        )
        is ReactiveHelperSessionStatus.Failed -> helperFailurePresentation(code)
    }
}

fun ClipboardUiState.toUnifiedConnectionPresentation(): UnifiedConnectionPresentation = when {
    isRunning -> presentation(
        ConnectionTransport.Clipboard, ConnectionPresentationState.Checking, "Checking…",
        "Checking clipboards", "Codecks is checking the phone and Mac clipboards.", ConnectionSupportCode.ClipboardChecking,
    )
    hasConflict -> presentation(
        ConnectionTransport.Clipboard, ConnectionPresentationState.Conflict, "Conflict",
        "Both clipboards changed", "Choose which copy to keep before syncing again.", ConnectionSupportCode.ClipboardConflict,
        ConnectionRepair.ResolveConflict,
    )
    (connectionConfigured || connectionReady) && isRemoteOffline -> presentation(
        ConnectionTransport.Clipboard, ConnectionPresentationState.Sleeping, "Offline",
        "Mac may be asleep", "Wake the Mac, then retry clipboard sync.", ConnectionSupportCode.ClipboardSleeping,
        ConnectionRepair.RetryNow,
    )
    !connectionReady -> presentation(
        ConnectionTransport.Clipboard, ConnectionPresentationState.SetupRequired, "Setup needed",
        "Mac connection needed", "Finish Mac setup before transferring clipboard text.", ConnectionSupportCode.ClipboardSetup,
        ConnectionRepair.OpenConnectionSetup,
    )
    lastFailureClass != null -> presentation(
        ConnectionTransport.Clipboard, ConnectionPresentationState.Failed, "Failed",
        "Last transfer failed", "Retry the transfer or review Mac setup.", ConnectionSupportCode.ClipboardFailed,
        ConnectionRepair.RetryNow, ConnectionRepair.OpenConnectionSetup,
    )
    else -> presentation(
        ConnectionTransport.Clipboard, ConnectionPresentationState.Ready, "Ready",
        "Clipboard ready",
        if (mode == io.codecks.domain.clipboard.ClipboardSyncMode.Off) {
            "Manual transfer is available. Automatic sync is off."
        } else if (batterySaverActive) {
            "Battery Saver paused automatic sync. Manual refresh remains available."
        } else if (liveSyncVisible) {
            "Automatic sync checks while Clipboard is open."
        } else if (staleEndpoints.isNotEmpty()) {
            "Clipboard information needs another check."
        } else {
            "Automatic sync resumes when Clipboard is open."
        },
        ConnectionSupportCode.ClipboardReady,
    )
}

private fun helperFailurePresentation(code: String): UnifiedConnectionPresentation = when (code) {
    "helper_authentication_failed", "helper_secret_missing" -> presentation(
        ConnectionTransport.Helper, ConnectionPresentationState.AuthenticationFailed, "Authentication failed",
        "Helper authentication failed", "Pair the helper again before reconnecting.", ConnectionSupportCode.HelperAuthentication,
        ConnectionRepair.PairHelper,
    )
    "helper_identity_mismatch" -> presentation(
        ConnectionTransport.Helper, ConnectionPresentationState.IdentityMismatch, "Identity changed",
        "Helper identity changed", "Do not reconnect until you verify the Mac helper identity.", ConnectionSupportCode.HelperIdentity,
        ConnectionRepair.ReviewIdentity,
    )
    "helper_connection_failed", "helper_session_expired" -> presentation(
        ConnectionTransport.Helper, ConnectionPresentationState.Reconnecting, "Reconnecting…",
        "Helper disconnected", "Open the Mac helper, then retry the authenticated session.", ConnectionSupportCode.HelperRetrying,
        ConnectionRepair.OpenHelper, ConnectionRepair.RetryNow,
    )
    "helper_identity_missing" -> presentation(
        ConnectionTransport.Helper, ConnectionPresentationState.SetupRequired, "Not paired",
        "Helper pairing missing", "Import pairing from the Codecks Mac helper.", ConnectionSupportCode.HelperSetup,
        ConnectionRepair.PairHelper,
    )
    else -> presentation(
        ConnectionTransport.Helper, ConnectionPresentationState.Failed, "Failed",
        "Helper needs attention", "Retry or export redacted diagnostics for support.", ConnectionSupportCode.HelperFailed,
        ConnectionRepair.RetryNow, ConnectionRepair.ContactSupport,
    )
}

private fun sshSetup(title: String, detail: String, supportCode: ConnectionSupportCode) = presentation(
    ConnectionTransport.Ssh, ConnectionPresentationState.SetupRequired, "Setup needed",
    title, detail, supportCode, ConnectionRepair.OpenConnectionSetup,
)

private fun presentation(
    transport: ConnectionTransport,
    state: ConnectionPresentationState,
    status: String,
    title: String,
    detail: String,
    supportCode: ConnectionSupportCode,
    vararg repairs: ConnectionRepair,
) = UnifiedConnectionPresentation(
    transport = transport,
    state = state,
    statusLabel = status,
    title = title,
    detail = detail,
    supportCode = supportCode,
    repairs = repairs.toList(),
)
