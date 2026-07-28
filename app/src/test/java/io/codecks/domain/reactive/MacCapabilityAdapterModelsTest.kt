package io.codecks.domain.reactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MacCapabilityAdapterModelsTest {
    private val provenance = ReactiveRequestProvenance(
        macId = MacId("mac-1"),
        snapshotRevision = 7L,
        source = StateSource.Helper,
        observedAtMillis = 1_000L,
    )

    @Test
    fun shortcutsCliBuildsArgvWithoutShellString() {
        val request = AppleShortcutsCliRequest(
            shortcutName = "Daily Standup",
            inputPath = "/Users/me/Library/Caches/codecks/input.json",
            timeoutMillis = 5_000L,
            maxOutputBytes = 1024,
        )

        assertEquals(
            listOf(
                "/usr/bin/shortcuts",
                "run",
                "Daily Standup",
                "--input-path",
                "/Users/me/Library/Caches/codecks/input.json",
            ),
            request.argv(),
        )
    }

    @Test
    fun shortcutsCliRejectsUnsafeNamesAndInputPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            AppleShortcutsCliRequest("Daily\nStandup")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppleShortcutsCliRequest("Daily Standup", inputPath = "../input.json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppleShortcutsCliRequest("Daily Standup", timeoutMillis = 60_000L)
        }
    }

    @Test
    fun brightnessRequestValidatesRangeAndAdapter() {
        val valid = MonitorBrightnessRequest(
            displayId = "external-dell-1",
            percent = 45,
            provenance = provenance,
            adapterId = "betterdisplay",
        )

        assertEquals(45, valid.percent)
        assertThrows(IllegalArgumentException::class.java) {
            MonitorBrightnessRequest("display", 101, provenance, "betterdisplay")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonitorBrightnessRequest("display", 50, provenance, "bad adapter")
        }
    }

    @Test
    fun accessibilityDiscoveryRejectsUnsafeBundleAndCapsActions() {
        val valid = AccessibilityDiscoveryRequest(
            frontAppBundleId = "com.apple.finder",
            provenance = provenance,
            maxActions = 8,
        )

        assertEquals("com.apple.finder", valid.frontAppBundleId)
        assertThrows(IllegalArgumentException::class.java) {
            AccessibilityDiscoveryRequest("../Finder", provenance)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccessibilityDiscoveryRequest("com.apple.finder", provenance, maxActions = 99)
        }
    }
}
