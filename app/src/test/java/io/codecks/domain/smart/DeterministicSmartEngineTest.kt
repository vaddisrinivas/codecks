package io.codecks.domain.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicSmartEngineTest {
    private val engine = DeterministicSmartEngine()
    private val now = 1_800_000L

    @Test
    fun sameContextProducesSameRankingWithoutAi() {
        val context = smartContext(activeMacApp = "Google Chrome", recentActionIds = listOf("reload"))
        val first = engine.suggest(context, actions(), nowMillis = now)
        val second = engine.suggest(context, actions(), nowMillis = now)

        assertEquals(first.candidates.map { it.actionId }, second.candidates.map { it.actionId })
        assertEquals("reload", first.candidates.first().actionId)
    }

    @Test
    fun confidenceNeverGrantsExecutionPermission() {
        val context = smartContext(activeMacApp = "Terminal", recentActionIds = listOf("danger"))
        val candidate = engine.suggest(context, actions(), nowMillis = now).candidates.first { it.actionId == "danger" }

        assertTrue(candidate.confidenceLabel in SmartConfidenceLabel.entries)
        assertTrue(SmartRisk.Dangerous in candidate.risks)
        assertFalse("Smart candidates are suggestions only", candidate.capabilities.contains(SmartCapability.RuleDraft))
    }

    @Test
    fun hideAndNeverSuppressSuggestions() {
        val context = smartContext(activeMacApp = "Chrome", recentActionIds = listOf("reload"))
        val hidden = SmartFeedbackSummary(
            hiddenCandidateIds = setOf("smart:reload"),
            neverAppActionKeys = setOf("chrome:browser"),
        )
        val ranked = engine.suggest(context, actions(), hidden, now).candidates.mapNotNull { it.actionId }

        assertFalse("reload" in ranked)
        assertFalse("browser" in ranked)
    }

    private fun smartContext(
        activeMacApp: String?,
        recentActionIds: List<String>,
    ) = SmartContext(
        currentSurface = "Deck",
        selectedMacId = "mac",
        macConnected = true,
        activeMacApp = activeMacApp,
        recentActionIds = recentActionIds,
        notificationSourceKeys = emptyList(),
        coarsePhoneContext = "desktop",
        supportedCapabilities = setOf(
            SmartCapability.LocalNavigation,
            SmartCapability.MacCommand,
            SmartCapability.ConnectionRepair,
            SmartCapability.Keyboard,
            SmartCapability.Clipboard,
        ),
        hourBucket = 12,
        createdAtMillis = now,
        expiresAtMillis = now + 60_000L,
    )

    private fun actions() = listOf(
        SmartActionRef("reload", "Reload browser", "Refresh tab", "Ssh", null, requiresMac = true, dangerous = false),
        SmartActionRef("browser", "Open Chrome", "Browser", "Local", "trackpad", requiresMac = false, dangerous = false),
        SmartActionRef("danger", "Danger", "Dangerous terminal action", "Ssh", null, requiresMac = true, dangerous = true),
    )
}
