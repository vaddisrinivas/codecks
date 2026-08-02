package io.codecks.domain.automation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class AutomationPreflightIsolationTest {
    @Test
    fun preflightDomainNeverImportsPointerGestureOrHidRuntime() {
        val sources = listOf(
            "AutomationCapability.kt",
            "AutomationRecipe.kt",
            "AutomationRevisionGate.kt",
        ).joinToString("\n") { name ->
            File("src/main/java/io/codecks/domain/automation/$name").readText()
        }

        listOf(
            "io.codecks.core.trackpad",
            "io.codecks.ui.mouse",
            "io.codecks.ui.keyboard",
            "HidController",
            "TrackpadGestureEngine",
        ).forEach { forbidden ->
            assertFalse("Preflight hot-path dependency: $forbidden", sources.contains(forbidden))
        }
    }
}
