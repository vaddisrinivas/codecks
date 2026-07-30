package io.codecks.ui.automations

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationsUiRecoveryTest {
    @Test
    fun editorUsesWhenIfThenLanguage() {
        val source = File("src/main/java/io/codecks/ui/automations/AutomationsScreen.kt").readText()

        assertTrue(source.contains("AutomationSectionHeader("))
        assertTrue(source.contains("""label = "When""""))
        assertTrue(source.contains("""label = "If""""))
        assertTrue(source.contains("""label = "Then""""))
        assertTrue(source.contains("Validate + preflight + live test"))
        assertTrue(source.contains("Validation + preflight + live test are required"))
        assertTrue(source.contains("SAFE_COMMAND_TEMPLATES"))
        assertTrue(source.contains("AutomationHistoryDialog"))
        assertTrue(source.contains("AutomationStatusPill("))
        assertTrue(source.contains("AutomationRuleLine("))
        assertTrue(source.contains("\"WHEN\""))
        assertTrue(source.contains("\"IF\""))
        assertTrue(source.contains("\"THEN\""))
    }
}
