package io.codecks.ui.connection

import io.codecks.HidBluetoothPower
import io.codecks.HidHost
import io.codecks.HidLifecycle
import io.codecks.HidState
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothSetupStatusTest {
    @Test
    fun productionPermissionFlowRoutesPermanentDenialToAppSettings() {
        val source = File("src/main/java/io/codecks/MainActivity.kt").readText()

        assertTrue(source.contains("shouldShowRequestPermissionRationale"))
        assertTrue(source.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS"))
        assertTrue(source.contains("bluetoothPermissionPermanentlyDenied"))
        assertTrue(source.contains("Lifecycle.Event.ON_RESUME"))
        assertEquals(3, "LifecycleEventObserver".toRegex().findAll(source).count() - 1)
    }

    @Test
    fun permissionsAreApiAndCapabilityAware() {
        assertTrue(BluetoothPermissionPolicy.requiredRuntimePermissions(apiLevel = 30).isEmpty())
        assertEquals(
            setOf(BluetoothPermissionPolicy.BluetoothConnect),
            BluetoothPermissionPolicy.requiredRuntimePermissions(apiLevel = 31),
        )
        assertEquals(
            setOf(
                BluetoothPermissionPolicy.BluetoothConnect,
                BluetoothPermissionPolicy.BluetoothScan,
            ),
            BluetoothPermissionPolicy.requiredRuntimePermissions(apiLevel = 35, usesPlatformDiscovery = true),
        )
    }

    @Test
    fun everyFailureGetsOnlyItsScopedRemediation() {
        val cases = listOf(
            observation(permissionState = BluetoothPermissionState.Denied) to
                (BluetoothSetupCheck.Permission to BluetoothSetupRemediation.RequestPermission),
            observation(permissionState = BluetoothPermissionState.PermanentlyDenied) to
                (BluetoothSetupCheck.Permission to BluetoothSetupRemediation.OpenAppSettings),
            observation(bluetoothEnabled = false) to
                (BluetoothSetupCheck.BluetoothEnabled to BluetoothSetupRemediation.EnableBluetooth),
            observation(profileRegistrationFailed = true, hidRegistered = false) to
                (BluetoothSetupCheck.HidRegistered to BluetoothSetupRemediation.RetryHidRegistration),
            observation(bondedHostCount = 0, selectedHostIsBonded = false, connectedToSelectedHost = false) to
                (BluetoothSetupCheck.BondedHost to BluetoothSetupRemediation.OpenSystemPairing),
            observation(selectedHostIsBonded = false, connectedToSelectedHost = false) to
                (BluetoothSetupCheck.SelectedHost to BluetoothSetupRemediation.ChooseBondedHost),
            observation(connectedToSelectedHost = false) to
                (BluetoothSetupCheck.Connected to BluetoothSetupRemediation.RetryConnection),
        )

        cases.forEach { (input, expected) ->
            val status = evaluateBluetoothSetup(input)
            assertEquals(expected.first, status.currentCheck)
            assertEquals(expected.second, status.remediation)
            assertFalse(status.success)
        }
    }

    @Test
    fun connectedFlagCannotFakePairingOrRegistrationProof() {
        val fakeConnection = observation(
            hidRegistered = false,
            bondedHostCount = 0,
            selectedHostIsBonded = false,
            connectedToSelectedHost = true,
        )
        assertFalse(evaluateBluetoothSetup(fakeConnection).success)

        val host = HidHost("AA:BB", "Mac")
        val state = HidState(
            lifecycle = HidLifecycle.Connected,
            bluetoothPower = HidBluetoothPower.On,
            isReady = true,
            isConnected = true,
            hosts = listOf(host),
            selectedHostAddress = host.address,
        )
        val confirmed = state.bluetoothSetupStatus(BluetoothPermissionState.Granted)
        assertTrue(confirmed.success)
        assertEquals(BluetoothSetupCheck.entries.toSet(), confirmed.confirmedChecks)
        assertEquals(BluetoothSetupRemediation.None, confirmed.remediation)
    }

    @Test
    fun writesBluetoothSetupEvidence() {
        val rows = JSONArray()
        BluetoothPermissionState.entries.forEach { permission ->
            val status = evaluateBluetoothSetup(observation(permissionState = permission))
            rows.put(
                JSONObject()
                    .put("permission", permission.name)
                    .put("currentCheck", status.currentCheck.name)
                    .put("remediation", status.remediation.name)
                    .put("success", status.success),
            )
        }
        val success = evaluateBluetoothSetup(observation())
        rows.put(
            JSONObject()
                .put("permission", BluetoothPermissionState.Granted.name)
                .put("currentCheck", success.currentCheck.name)
                .put("remediation", success.remediation.name)
                .put("success", success.success),
        )
        val output = evidenceDirectory().resolve("bluetooth_setup_matrix.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(rows.toString(2))

        assertTrue(output.isFile)
        assertEquals(BluetoothPermissionState.entries.size + 1, rows.length())
    }

    private fun observation(
        permissionState: BluetoothPermissionState = BluetoothPermissionState.Granted,
        bluetoothEnabled: Boolean = true,
        hidRegistered: Boolean = true,
        profileRegistrationFailed: Boolean = false,
        bondedHostCount: Int = 1,
        selectedHostIsBonded: Boolean = true,
        connectedToSelectedHost: Boolean = true,
    ) = BluetoothSetupObservation(
        permissionState = permissionState,
        bluetoothEnabled = bluetoothEnabled,
        hidRegistered = hidRegistered,
        profileRegistrationFailed = profileRegistrationFailed,
        bondedHostCount = bondedHostCount,
        selectedHostIsBonded = selectedHostIsBonded,
        connectedToSelectedHost = connectedToSelectedHost,
    )

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/SET-02")
    }
}
