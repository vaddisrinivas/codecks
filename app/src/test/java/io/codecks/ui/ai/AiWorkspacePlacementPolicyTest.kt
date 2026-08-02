package io.codecks.ui.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkspacePlacementPolicyTest {
    @Test
    fun deckArtifactsOfferCatalogOnlyAndExplicitPlacementChoices() {
        val workspace = File("src/main/java/io/codecks/ui/ai/AiWorkspaceScreen.kt").readText()
        val route = File("src/main/java/io/codecks/ui/ai/AiProviderSettingsScreen.kt").readText()

        assertTrue(workspace.contains("label = \"Save only\""))
        assertTrue(workspace.contains("\"Place in slot \${preferredDeckSlot + 1}\""))
        assertTrue(workspace.contains("artifact.actions.size == 1"))
        assertTrue(workspace.contains("\"Choose Deck slot\""))
        assertTrue(workspace.contains("text = \"Saved to catalog\""))
        assertTrue(route.contains("controller.markSavedOnly(artifact.id)"))
        assertTrue(route.contains("val deckCompatible"))
        assertTrue(route.contains("AiArtifactPlacementChoice.PlaceHere"))
        assertTrue(route.contains("AiArtifactPlacementChoice.ChooseSlot"))
    }
}
