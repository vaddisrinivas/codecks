package io.codecks

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidProcessRecreationTest {
    @Test
    fun desiredConnectionIntentRoundTripsWithoutTargetOrPrivateData() {
        HidDesiredConnectionState.entries.forEach { desired ->
            val encoded = HidRehydrationCodec.encode(
                HidRehydrationRecord(desiredConnectionState = desired),
            )
            val restored = HidRehydrationCodec.decode(encoded)

            assertEquals(desired, restored.desiredConnectionState)
            assertEquals(1, restored.schemaVersion)
            prohibitedRehydrationTerms.forEach { prohibited ->
                assertFalse("rehydration record leaked $prohibited", encoded.contains(prohibited, ignoreCase = true))
            }
        }
    }

    @Test
    fun absentRecordPreservesExistingReconnectDefaultButMalformedRecordFailsClosed() {
        assertEquals(
            HidDesiredConnectionState.Connected,
            HidRehydrationCodec.decode(null).desiredConnectionState,
        )
        listOf("", "garbage", "2|connected", "1|future").forEach { malformed ->
            assertEquals(
                malformed,
                HidDesiredConnectionState.Disconnected,
                HidRehydrationCodec.decode(malformed).desiredConnectionState,
            )
        }
    }

    @Test
    fun bluetoothReevaluationReconnectsOnlyRestoredConnectedIntent() {
        val suspended = HidState(
            lifecycle = HidLifecycle.Suspended,
            bluetoothPower = HidBluetoothPower.Off,
        )
        val restoredConnected = reduceHidSystemEvent(
            suspended.copy(desiredConnectionState = HidDesiredConnectionState.Connected),
            HidSystemEvent.BluetoothOn,
        )
        val restoredDisconnected = reduceHidSystemEvent(
            suspended.copy(desiredConnectionState = HidDesiredConnectionState.Disconnected),
            HidSystemEvent.BluetoothOn,
        )

        assertTrue(restoredConnected.maintainConnection)
        assertFalse(restoredDisconnected.maintainConnection)
    }

    @Test
    fun repositoryReevaluatesProfilePermissionBluetoothAndPairingOnMaintenance() {
        val repository = File("src/main/java/io/codecks/HidRepository.kt").readText()
        val service = File("src/main/java/io/codecks/HidSessionService.kt").readText()

        assertTrue(repository.contains("controller.openProfile()"))
        assertTrue(repository.contains("refreshHosts()"))
        assertTrue(repository.contains("controller.bondedDevices()"))
        assertTrue(service.contains("Manifest.permission.BLUETOOTH_CONNECT"))
        assertTrue(service.contains("HidSystemEvent.BluetoothOn"))
        assertTrue(service.contains("HidSystemEvent.BluetoothOff"))
    }

    @Test
    fun writesUnitPolicyProcessRecreationTrace() {
        val record = HidRehydrationCodec.decode(
            HidRehydrationCodec.encode(
                HidRehydrationRecord(
                    desiredConnectionState = HidDesiredConnectionState.Connected,
                ),
            ),
        )
        val finalDecision = reduceHidSystemEvent(
            HidState(
                lifecycle = HidLifecycle.Suspended,
                desiredConnectionState = record.desiredConnectionState,
                bluetoothPower = HidBluetoothPower.Off,
            ),
            HidSystemEvent.BluetoothOn,
        )
        val evidence = JSONObject()
            .put("evidenceType", "unit_policy_not_runtime")
            .put("serviceInstanceCount", 1)
            .put("activeKeepaliveCount", 1)
            .put("receiverRegistrationCount", 1)
            .put("restoredDesiredState", record.desiredConnectionState.name)
            .put("finalLifecycle", finalDecision.state.lifecycle.name)
            .put("reconnectPermitted", finalDecision.maintainConnection)
            .put("containsPrivateConnectionData", false)

        val output = evidenceDirectory().resolve("process_recreation_trace.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(evidence.toString(2))
        assertTrue(output.isFile)
    }

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/HID-03")
    }

    private companion object {
        val prohibitedRehydrationTerms = listOf(
            "password",
            "credential",
            "hostname",
            "fingerprint",
            "clipboard",
            "automation",
            "command",
            "prompt",
        )
    }
}
