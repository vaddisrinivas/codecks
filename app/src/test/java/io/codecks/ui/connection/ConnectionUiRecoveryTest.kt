package io.codecks.ui.connection

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionUiRecoveryTest {
    @Test
    fun settingsMacPairingUsesLinearProductStepper() {
        val source = File("src/main/java/io/codecks/ui/settings/SettingsScreen.kt").readText()

        assertTrue(source.contains("MacPairingStepper("))
        assertTrue(source.contains("""FindMac("Find")"""))
        assertTrue(source.contains("""TrustMac("Trust")"""))
        assertTrue(source.contains("""Authorize("Authorize")"""))
        assertTrue(source.contains("""Done("Done")"""))
        assertTrue(source.contains("Connect a Mac"))
        assertTrue(source.contains("Trust this Mac"))
        assertTrue(source.contains("Confirm this is my Mac"))
        assertTrue(source.contains("Use saved password"))
        assertTrue(source.contains("Save Mac"))
        assertTrue(source.contains("Open GitHub helper page"))
        assertTrue(source.contains("Mac actions"))
        assertTrue(source.contains("Mac input"))
        assertFalse(source.contains("""SectionLabel("Readiness")"""))
        assertFalse(source.contains("Reading fingerprint"))
        assertFalse(source.contains("Trust manually"))
        assertFalse(source.contains("Pair Mac"))
        assertFalse(source.contains("Open HID setup"))
        assertFalse(source.contains("Pair HID"))
    }
}
