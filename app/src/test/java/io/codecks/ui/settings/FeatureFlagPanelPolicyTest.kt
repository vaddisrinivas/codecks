package io.codecks.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagPanelPolicyTest {
    @Test
    fun reactiveTrackpadFlagIsIndependentFromLabsBlock() {
        val source = File("src/main/java/io/codecks/ui/settings/SettingsScreen.kt").readText()
        val reactiveIndex = source.indexOf("""FeatureFlag.ReactiveTrackpad""")
        val labsIfIndex = source.indexOf("""if (labsEnabled) {""")

        assertTrue("ReactiveTrackpad flag should exist in settings", reactiveIndex >= 0)
        assertTrue("labsEnabled block should exist in settings", labsIfIndex >= 0)
        assertTrue(
            "ReactiveTrackpad flag should be declared before the Labs-only block",
            reactiveIndex < labsIfIndex,
        )
    }
}
