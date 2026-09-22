package io.codecks.ui.connection

import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.platform.helper.ReactiveHelperSessionStatus
import io.codecks.ui.clipboard.ClipboardUiState

/** Deterministic test scenarios. Never accepts raw exception or endpoint text. */
enum class SupportFailureScenario {
    BluetoothPermission,
    BluetoothDisabled,
    HidRegistration,
    MacNotSelected,
    MacSleeping,
    MacAuthentication,
    MacIdentityChanged,
    HelperAuthentication,
    HelperIdentityChanged,
    ClipboardConflict,
    ClipboardOffline,
    UnknownConnection,
}

fun diagnoseInjectedSupportFailure(scenario: SupportFailureScenario): UnifiedConnectionPresentation = when (scenario) {
    SupportFailureScenario.BluetoothPermission ->
        HidHealth(HidHealthKind.PermissionMissing, "", "").toUnifiedConnectionPresentation()
    SupportFailureScenario.BluetoothDisabled ->
        HidHealth(HidHealthKind.Unavailable, "", "").toUnifiedConnectionPresentation()
    SupportFailureScenario.HidRegistration ->
        HidHealth(HidHealthKind.Failed, "", "").toUnifiedConnectionPresentation()
    SupportFailureScenario.MacNotSelected ->
        HidHealth(HidHealthKind.ReadyNoTarget, "", "").toUnifiedConnectionPresentation()
    SupportFailureScenario.MacSleeping ->
        ConnectionHealth(ConnectionHealthKind.Offline, "", "").toUnifiedConnectionPresentation()
    SupportFailureScenario.MacAuthentication ->
        ConnectionHealth(ConnectionHealthKind.AuthFailed, "", "").toUnifiedConnectionPresentation()
    SupportFailureScenario.MacIdentityChanged ->
        ConnectionHealth(ConnectionHealthKind.FingerprintMismatch, "", "").toUnifiedConnectionPresentation()
    SupportFailureScenario.HelperAuthentication ->
        ReactiveHelperSessionStatus.Failed("helper_authentication_failed").toUnifiedConnectionPresentation()
    SupportFailureScenario.HelperIdentityChanged ->
        ReactiveHelperSessionStatus.Failed("helper_identity_mismatch").toUnifiedConnectionPresentation()
    SupportFailureScenario.ClipboardConflict ->
        ClipboardUiState(connectionReady = true, hasConflict = true).toUnifiedConnectionPresentation()
    SupportFailureScenario.ClipboardOffline ->
        ClipboardUiState(connectionConfigured = true, isRemoteOffline = true).toUnifiedConnectionPresentation()
    SupportFailureScenario.UnknownConnection -> ConnectionHealth(
        kind = ConnectionHealthKind.Offline,
        title = "",
        detail = "",
        issueOverride = ConnectionIssueCode.UNKNOWN,
    ).toUnifiedConnectionPresentation()
}
