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
}
