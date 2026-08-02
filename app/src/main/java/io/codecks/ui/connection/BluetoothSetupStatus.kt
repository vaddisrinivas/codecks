package io.codecks.ui.connection

import io.codecks.HidBluetoothPower
import io.codecks.HidLifecycle
import io.codecks.HidState

enum class BluetoothPermissionState {
    Granted,
    Denied,
    PermanentlyDenied,
}

object BluetoothPermissionPolicy {
    const val BluetoothConnect = "android.permission.BLUETOOTH_CONNECT"
    const val BluetoothScan = "android.permission.BLUETOOTH_SCAN"

    fun requiredRuntimePermissions(
        apiLevel: Int,
        usesPlatformDiscovery: Boolean = false,
    ): Set<String> = when {
        apiLevel < 31 -> emptySet()
        usesPlatformDiscovery -> setOf(BluetoothConnect, BluetoothScan)
        else -> setOf(BluetoothConnect)
    }
}

enum class BluetoothSetupCheck {
    Permission,
    BluetoothEnabled,
    HidRegistered,
    BondedHost,
    SelectedHost,
    Connected,
}

enum class BluetoothSetupRemediation {
    None,
    RequestPermission,
    OpenAppSettings,
    EnableBluetooth,
    RetryHidRegistration,
    OpenSystemPairing,
    ChooseBondedHost,
    RetryConnection,
}

data class BluetoothSetupObservation(
    val permissionState: BluetoothPermissionState,
    val bluetoothEnabled: Boolean,
    val hidRegistered: Boolean,
    val profileRegistrationFailed: Boolean,
    val bondedHostCount: Int,
    val selectedHostIsBonded: Boolean,
    val connectedToSelectedHost: Boolean,
) {
    init {
        require(bondedHostCount >= 0)
    }
}

data class BluetoothSetupStatus(
    val currentCheck: BluetoothSetupCheck,
    val confirmedChecks: Set<BluetoothSetupCheck>,
    val remediation: BluetoothSetupRemediation,
    val success: Boolean,
)

fun evaluateBluetoothSetup(observation: BluetoothSetupObservation): BluetoothSetupStatus {
    val confirmed = buildSet {
        if (observation.permissionState == BluetoothPermissionState.Granted) add(BluetoothSetupCheck.Permission)
        if (observation.bluetoothEnabled) add(BluetoothSetupCheck.BluetoothEnabled)
        if (observation.hidRegistered) add(BluetoothSetupCheck.HidRegistered)
        if (observation.bondedHostCount > 0) add(BluetoothSetupCheck.BondedHost)
        if (observation.selectedHostIsBonded) add(BluetoothSetupCheck.SelectedHost)
        if (observation.connectedToSelectedHost &&
            observation.selectedHostIsBonded &&
            observation.bondedHostCount > 0 &&
            observation.hidRegistered &&
            observation.bluetoothEnabled &&
            observation.permissionState == BluetoothPermissionState.Granted
        ) {
            add(BluetoothSetupCheck.Connected)
        }
    }
    fun blockedAt(
        check: BluetoothSetupCheck,
        remediation: BluetoothSetupRemediation,
    ) = BluetoothSetupStatus(check, confirmed, remediation, success = false)

    return when {
        observation.permissionState == BluetoothPermissionState.PermanentlyDenied ->
            blockedAt(BluetoothSetupCheck.Permission, BluetoothSetupRemediation.OpenAppSettings)
        observation.permissionState == BluetoothPermissionState.Denied ->
            blockedAt(BluetoothSetupCheck.Permission, BluetoothSetupRemediation.RequestPermission)
        !observation.bluetoothEnabled ->
            blockedAt(BluetoothSetupCheck.BluetoothEnabled, BluetoothSetupRemediation.EnableBluetooth)
        observation.profileRegistrationFailed ->
            blockedAt(BluetoothSetupCheck.HidRegistered, BluetoothSetupRemediation.RetryHidRegistration)
        !observation.hidRegistered ->
            blockedAt(BluetoothSetupCheck.HidRegistered, BluetoothSetupRemediation.RetryHidRegistration)
        observation.bondedHostCount == 0 ->
            blockedAt(BluetoothSetupCheck.BondedHost, BluetoothSetupRemediation.OpenSystemPairing)
        !observation.selectedHostIsBonded ->
            blockedAt(BluetoothSetupCheck.SelectedHost, BluetoothSetupRemediation.ChooseBondedHost)
        !observation.connectedToSelectedHost ->
            blockedAt(BluetoothSetupCheck.Connected, BluetoothSetupRemediation.RetryConnection)
        else -> BluetoothSetupStatus(
            currentCheck = BluetoothSetupCheck.Connected,
            confirmedChecks = BluetoothSetupCheck.entries.toSet(),
            remediation = BluetoothSetupRemediation.None,
            success = true,
        )
    }
}

fun HidState.bluetoothSetupStatus(
    permissionState: BluetoothPermissionState,
): BluetoothSetupStatus {
    val selectedHostIsBonded = selectedHostAddress != null &&
        hosts.any { it.address == selectedHostAddress }
    return evaluateBluetoothSetup(
        BluetoothSetupObservation(
            permissionState = permissionState,
            bluetoothEnabled = bluetoothPower == HidBluetoothPower.On,
            hidRegistered = isReady,
            profileRegistrationFailed = lifecycle == HidLifecycle.Failed,
            bondedHostCount = hosts.size,
            selectedHostIsBonded = selectedHostIsBonded,
            connectedToSelectedHost = isConnected &&
                lifecycle == HidLifecycle.Connected &&
                selectedHostIsBonded,
        ),
    )
}
