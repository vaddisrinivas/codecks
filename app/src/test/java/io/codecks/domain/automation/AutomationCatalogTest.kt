package io.codecks.domain.automation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationCatalogTest {
    @Test
    fun focusedAutomationActions_existInBundledActionCatalog() {
        val catalog = File("src/main/assets/deckbridge_actions.json")
        val catalogText = catalog.readText()
        val actionIds = Regex(""""id"\s*:\s*"([^"]+)"""")
            .findAll(catalogText)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue(actionIds.containsAll(AutomationCatalog.focusedActionIds))
    }
}
