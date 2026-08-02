package io.codecks

import io.codecks.domain.connection.ConnectionIssueCode
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidSystemEventTest {
    @Test
    fun everyRequiredAndroidInputHasTypedEventAndServiceIngress() {
        assertEquals(
            setOf(
                "AppForegrounded",
                "AppBackgrounded",
                "ScreenOn",
                "ScreenOff",
                "UserLocked",
                "UserUnlocked",
                "BluetoothOn",
                "BluetoothOff",
                "ManualRetry",
            ),
            requiredEvents.map(::eventName).toSet(),
        )

        val service = File("src/main/java/io/codecks/HidSessionService.kt").readText()
        requiredEvents.forEach { event ->
            assertTrue(service.contains("HidSystemEvent.${eventName(event)}"))
        }
        assertTrue(service.contains("hidRepository.maintain()"))
        assertFalse(service.contains("HID_KEEP_ALIVE_MS"))
    }

    @Test
    fun backgroundingHealthyConnectionOnlyUpdatesVisibility() {
        val connected = healthyConnectedState()
        val decision = reduceHidSystemEvent(connected, HidSystemEvent.AppBackgrounded)

        assertEquals(HidAppVisibility.Background, decision.state.appVisibility)
        assertEquals(HidLifecycle.Connected, decision.state.lifecycle)
        assertTrue(decision.state.isConnected)
        assertFalse(decision.cancelConnectionAttempt)
        assertFalse(decision.maintainConnection)
    }

    @Test
    fun bluetoothOffSuspendsAndCancelsThenOnReconnectsOnlyWhenDesired() {
        val suspended = reduceHidSystemEvent(
            healthyConnectedState(),
            HidSystemEvent.BluetoothOff,
        )

        assertTrue(suspended.cancelConnectionAttempt)
        assertEquals(HidLifecycle.Suspended, suspended.state.lifecycle)
        assertEquals(HidBluetoothPower.Off, suspended.state.bluetoothPower)
        assertEquals(HidRetryDisposition.Suspended, suspended.state.retry.disposition)
        assertFalse(suspended.state.isConnected)

        val reconnect = reduceHidSystemEvent(suspended.state, HidSystemEvent.BluetoothOn)
        assertEquals(HidBluetoothPower.On, reconnect.state.bluetoothPower)
        assertTrue(reconnect.maintainConnection)

        val stayDisconnected = reduceHidSystemEvent(
            suspended.state.copy(desiredConnectionState = HidDesiredConnectionState.Disconnected),
            HidSystemEvent.BluetoothOn,
        )
        assertFalse(stayDisconnected.maintainConnection)
    }

    @Test
    fun manualRetryCannotBypassBluetoothSuspension() {
        val off = reduceHidSystemEvent(healthyConnectedState(), HidSystemEvent.BluetoothOff).state
        val retry = reduceHidSystemEvent(off, HidSystemEvent.ManualRetry)

        assertEquals(off, retry.state)
        assertFalse(retry.maintainConnection)
    }

    @Test
    fun screenWakeSchedulesControlPathMaintenanceOnlyForTransientFailure() {
        val transient = healthyConnectedState().copy(
            lifecycle = HidLifecycle.Unavailable,
            isConnected = false,
            failureClass = HidFailureClass.Transient,
            issueCode = ConnectionIssueCode.CONNECT_BACKOFF,
            retry = HidRetryMetadata(HidRetryDisposition.Scheduled, attempt = 2, nextAttemptAtMillis = 20_000L),
        )
        assertTrue(reduceHidSystemEvent(transient, HidSystemEvent.ScreenOn).maintainConnection)

        val blocked = transient.copy(
            failureClass = HidFailureClass.RepairRequired,
            issueCode = ConnectionIssueCode.SSH_AUTH_FAILED,
            retry = HidRetryMetadata(HidRetryDisposition.BlockedUntilRepair),
        )
        assertFalse(reduceHidSystemEvent(blocked, HidSystemEvent.ScreenOn).maintainConnection)
    }

    @Test
    fun writesDeterministicSystemEventTrace() {
        var state = HidState(
            desiredConnectionState = HidDesiredConnectionState.Connected,
            bluetoothPower = HidBluetoothPower.On,
        )
        val trace = JSONArray()
        requiredEvents.forEach { event ->
            val decision = reduceHidSystemEvent(state, event)
            state = decision.state
            trace.put(
                JSONObject()
                    .put("event", eventName(event))
                    .put("lifecycle", state.lifecycle.name)
                    .put("desired", state.desiredConnectionState.name)
                    .put("app", state.appVisibility.name)
                    .put("screen", state.screenState.name)
                    .put("lock", state.userLockState.name)
                    .put("bluetooth", state.bluetoothPower.name)
                    .put("inputAccess", state.inputAccess.name)
                    .put("retry", state.retry.disposition.name)
                    .put("cancelConnectionAttempt", decision.cancelConnectionAttempt)
                    .put("maintainConnection", decision.maintainConnection),
            )
        }
        val output = evidenceDirectory().resolve("system_event_trace.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(trace.toString(2))

        assertEquals(requiredEvents.size, trace.length())
        assertTrue(output.isFile)
    }

    private fun healthyConnectedState(): HidState = HidState(
        lifecycle = HidLifecycle.Connected,
        isReady = true,
        isConnected = true,
        desiredConnectionState = HidDesiredConnectionState.Connected,
        bluetoothPower = HidBluetoothPower.On,
    )

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/HID-02")
    }

    private companion object {
        val requiredEvents = listOf(
            HidSystemEvent.AppForegrounded,
            HidSystemEvent.AppBackgrounded,
            HidSystemEvent.ScreenOn,
            HidSystemEvent.ScreenOff,
            HidSystemEvent.UserLocked,
            HidSystemEvent.UserUnlocked,
            HidSystemEvent.BluetoothOff,
            HidSystemEvent.BluetoothOn,
            HidSystemEvent.ManualRetry,
        )

        fun eventName(event: HidSystemEvent): String = when (event) {
            HidSystemEvent.AppForegrounded -> "AppForegrounded"
            HidSystemEvent.AppBackgrounded -> "AppBackgrounded"
            HidSystemEvent.ScreenOn -> "ScreenOn"
            HidSystemEvent.ScreenOff -> "ScreenOff"
            HidSystemEvent.UserLocked -> "UserLocked"
            HidSystemEvent.UserUnlocked -> "UserUnlocked"
            HidSystemEvent.BluetoothOn -> "BluetoothOn"
            HidSystemEvent.BluetoothOff -> "BluetoothOff"
            HidSystemEvent.ManualRetry -> "ManualRetry"
        }
    }
}
