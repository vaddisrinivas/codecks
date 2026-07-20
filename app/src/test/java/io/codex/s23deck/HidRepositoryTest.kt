package io.codex.s23deck

import org.junit.Assert.assertEquals
import org.junit.Test

class HidRepositoryTest {
    @Test
    fun prioritizeHosts_prefersRememberedAndComputerLikeDevices() {
        val hosts = listOf(
            HidHost(address = "3", label = "AirPods Pro 3  00:00"),
            HidHost(address = "2", label = "MacBook Pro  11:11"),
            HidHost(address = "1", label = "Studio Display  22:22"),
        )

        val prioritized = prioritizeHosts(hosts, selectedAddress = "1")

        assertEquals(listOf("1", "2"), prioritized.map { it.address })
    }

    @Test
    fun prioritizeHosts_hidesPhoneAndUnknownDevicesWhenComputerTargetsExist() {
        val hosts = listOf(
            HidHost(address = "4", label = "Test phone  44:44"),
            HidHost(address = "3", label = "Bedroom Lamp  33:33"),
            HidHost(address = "2", label = "Mac Studio  22:22"),
            HidHost(address = "1", label = "MacBook Pro  11:11"),
        )

        val prioritized = prioritizeHosts(hosts, selectedAddress = null)

        assertEquals(listOf("2", "1"), prioritized.map { it.address })
    }

    @Test
    fun prioritizeHosts_hidesUnknownDevicesWhenNothingLooksCompatible() {
        val hosts = listOf(
            HidHost(address = "2", label = "Bedroom Lamp  11:11"),
            HidHost(address = "1", label = "Office Hub  22:22"),
        )

        val prioritized = prioritizeHosts(hosts, selectedAddress = null)

        assertEquals(emptyList<String>(), prioritized.map { it.address })
    }

    @Test
    fun diagnosticSummaryRedactsBluetoothAddresses() {
        val state = HidState(
            status = "Connected AA:BB:CC:DD:EE:FF",
            lifecycle = HidLifecycle.Connected,
            isReady = true,
            isConnected = true,
            hosts = listOf(HidHost(address = "AA:BB:CC:DD:EE:FF", label = "MacBook")),
            selectedHostAddress = "AA:BB:CC:DD:EE:FF",
            reconnectAttempt = 2,
            nextReconnectAtMillis = 11_000L,
            lastTransitionReason = "Disconnected AA:BB:CC:DD:EE:FF",
            lastTransitionAtMillis = 1_000L,
        )

        assertEquals(
            "lifecycle=Connected ready=true connected=true hosts=1 selected=true reconnectAttempt=2 retryIn=1s lastReason=Disconnected [bluetooth-address] lastAge=9s",
            state.redactedDiagnosticSummary(nowMillis = 10_000L),
        )
    }
}
