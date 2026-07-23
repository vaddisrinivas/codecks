package io.codecks.domain.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.codecks.domain.smart.SmartCandidate
import io.codecks.domain.smart.SmartConfidenceLabel
import io.codecks.domain.smart.SmartRisk

class DeterministicSmartEngineTest {
    private val engine = DeterministicSmartEngine()
    private val now = 1_800_000L

    @Test
    fun sameContextProducesSameRankingWithoutAi() {
        val context = smartContext(
            currentSurface = SmartSurface.Deck,
            activeMacApp = SmartAppKey("Google Chrome"),
            recentActionIds = listOf("reload"),
        )
        val first = engine.suggest(context, actions(), nowMillis = now)
        val second = engine.suggest(context, actions(), nowMillis = now)

        assertEquals(first.candidates.map { it.actionId }, second.candidates.map { it.actionId })
        assertEquals("browser", first.candidates.first().actionId)
    }

    @Test
    fun engineHandlesDeckTrackpadAndKeyboardSurfaces() {
        val deck = smartContext(SmartSurface.Deck)
        val trackpad = smartContext(SmartSurface.Trackpad)
        val keyboard = smartContext(SmartSurface.Keyboard)

        val deckIds = engine.suggest(deck, actions(), nowMillis = now).candidates.map { it.id }.toSet()
        val trackpadIds = engine.suggest(trackpad, actions(), nowMillis = now).candidates.map { it.id }.toSet()
        val keyboardIds = engine.suggest(keyboard, actions(), nowMillis = now).candidates.map { it.id }.toSet()

        assertTrue(deckIds.any { it.startsWith("smart:deck") })
        assertTrue(trackpadIds.any { it.startsWith("smart:trackpad") })
        assertTrue(keyboardIds.any { it.startsWith("smart:keyboard") })
    }

    @Test
    fun confidenceNeverGrantsExecutionPermission() {
        val context = smartContext(
            currentSurface = SmartSurface.Settings,
            activeMacApp = SmartAppKey("Terminal"),
            recentActionIds = listOf("browser", "danger"),
        )
        val feedback = SmartFeedbackSummary(actionScores = mapOf("danger" to 5))
        val candidate = engine.suggest(context, actions(), feedback, nowMillis = now).candidates.first { it.actionId == "danger" }

        assertTrue(candidate.confidenceLabel in SmartConfidenceLabel.entries)
        assertTrue(SmartRisk.Dangerous in candidate.risks)
        assertFalse("Smart candidates are suggestions only", SmartCapability.RuleDraft in candidate.capabilities)
    }

    @Test
    fun hideAndNeverSuppressSuggestions() {
        val context = smartContext(activeMacApp = SmartAppKey("Chrome"), recentActionIds = listOf("reload"))
        val hidden = SmartFeedbackSummary(
            suppressedContextActionKeys = setOf("smart:deck:chrome:reload"),
            globallySuppressedActionIds = setOf("browser"),
        )
        val ranked = engine.suggest(context, actions(), hidden, now).candidates.mapNotNull { it.actionId }

        assertFalse("reload" in ranked)
        assertFalse("browser" in ranked)
    }

    @Test
    fun suppressHereAffectsOnlyTheMatchingSurfaceAndApp() {
        val context = smartContext(activeMacApp = SmartAppKey("Chrome"), recentActionIds = listOf("reload"))
        val trackpadContext = smartContext(currentSurface = SmartSurface.Trackpad, activeMacApp = SmartAppKey("Chrome"), recentActionIds = listOf("reload"))
        val feedback = SmartFeedbackSummary(
            suppressedContextActionKeys = setOf("smart:deck:chrome:reload"),
        )

        val deckRanked = engine.suggest(context, actions(), feedback, nowMillis = now).candidates.mapNotNull { it.actionId }
        val trackpadRanked = engine.suggest(trackpadContext, actions(), feedback, nowMillis = now).candidates.mapNotNull { it.actionId }

        assertFalse("reload" in deckRanked)
        assertTrue("reload" in trackpadRanked)
    }

    @Test
    fun expiredContextReturnsNoCandidatesWithLaterClock() {
        val context = smartContext(activeMacApp = SmartAppKey("Chrome"), recentActionIds = listOf("reload"))

        val decision = engine.suggest(context, actions(), nowMillis = context.expiresAtMillis + 1L)

        assertEquals(SmartUnavailable.Expired, decision.unavailable)
        assertTrue(decision.candidates.isEmpty())
    }

    @Test
    fun missingMacCapabilityRemovesMacCandidates() {
        val context = smartContext(activeMacApp = SmartAppKey("Chrome"), recentActionIds = listOf("reload")).copy(
            macConnected = false,
            supportedCapabilities = setOf(
                SmartCapability.LocalNavigation,
                SmartCapability.ConnectionRepair,
                SmartCapability.Keyboard,
                SmartCapability.Clipboard,
            ),
        )

        val ranked = engine.suggest(context, actions(), nowMillis = now).candidates.mapNotNull { it.actionId }

        assertFalse("reload" in ranked)
        assertFalse("danger" in ranked)
    }

    @Test
    fun hidMediaCandidateRequiresMacInputCapability() {
        val contextWithoutMacInput = smartContext(
            recentActionIds = listOf("browser", "hid_media_play_pause"),
            supportedCapabilities = setOf(
                SmartCapability.LocalNavigation,
                SmartCapability.ConnectionRepair,
                SmartCapability.Keyboard,
                SmartCapability.Clipboard,
            ),
        )

        val rankedWithoutMacInput = engine.suggest(contextWithoutMacInput, mediaActions(), nowMillis = now).candidates.mapNotNull { it.actionId }

        assertFalse("hid_media_play_pause" in rankedWithoutMacInput)
    }

    @Test
    fun hidMediaCandidateAppearsWhenMacInputCapabilityPresent() {
        val contextWithMacInput = smartContext(
            recentActionIds = listOf("browser", "hid_media_play_pause"),
            supportedCapabilities = setOf(
                SmartCapability.LocalNavigation,
                SmartCapability.ConnectionRepair,
                SmartCapability.Keyboard,
                SmartCapability.Clipboard,
                SmartCapability.MacInput,
            ),
        )

        val rankedWithMacInput = engine.suggest(contextWithMacInput, mediaActions(), nowMillis = now).candidates.mapNotNull { it.actionId }

        assertTrue("hid_media_play_pause" in rankedWithMacInput)
    }

    @Test
    fun transitionFeedbackCanPromoteLikelyNextAction() {
        val context = smartContext(activeMacApp = SmartAppKey("Chrome"), recentActionIds = listOf("reload"))
        val feedback = SmartFeedbackSummary(
            transitionScores = mapOf(
                smartTransitionKey(
                    surface = SmartSurface.Deck,
                    appKey = SmartAppKey("Chrome"),
                    macId = SmartMacId("mac_123"),
                    previousActionId = "reload",
                    nextActionId = "browser",
                ) to 30,
            ),
        )

        val ranked = engine.suggest(context, actions(), feedback, now).candidates.mapNotNull { it.actionId }

        assertEquals("browser", ranked.first())
    }

    @Test
    fun neverGlobalAppliesAcrossContexts() {
        val deckContext = smartContext(activeMacApp = SmartAppKey("Chrome"), recentActionIds = listOf("reload"))
        val trackpadContext = smartContext(currentSurface = SmartSurface.Trackpad, activeMacApp = SmartAppKey("Chrome"), recentActionIds = listOf("reload"))
        val feedback = SmartFeedbackSummary(globallySuppressedActionIds = setOf("browser"))

        val deckRanked = engine.suggest(deckContext, actions(), feedback, nowMillis = now).candidates.mapNotNull { it.actionId }
        val trackpadRanked = engine.suggest(trackpadContext, actions(), feedback, nowMillis = now).candidates.mapNotNull { it.actionId }

        assertFalse("browser" in deckRanked)
        assertFalse("browser" in trackpadRanked)
    }

    @Test
    fun returnsAtMostFiveCandidates() {
        val context = smartContext(activeMacApp = SmartAppKey("Chrome"), recentActionIds = listOf("finder"))
        val actions = (1..7).map { index ->
            SmartActionRef(
                id = "c$index",
                title = "Candidate $index",
                description = "Candidate $index",
                kind = SmartActionKind.LocalNavigation,
                route = "finder",
                requiredCapabilities = setOf(SmartCapability.LocalNavigation),
                dangerous = false,
            )
        }
        val top5Engine = DeterministicSmartEngine(
            providers = listOf(
                FakeScoredProvider(
                    providerId = "recent",
                    actions = actions,
                    baseScore = 100,
                ),
            ),
        )
        val ranked = top5Engine.suggest(context, actions, SmartFeedbackSummary(), nowMillis = now).candidates

        assertEquals(5, ranked.size)
    }

    private fun smartContext(
        currentSurface: SmartSurface = SmartSurface.Deck,
        activeMacApp: SmartAppKey? = null,
        recentActionIds: List<String> = listOf("reload"),
        macConnected: Boolean = true,
        supportedCapabilities: Set<SmartCapability> = setOf(
            SmartCapability.LocalNavigation,
            SmartCapability.MacCommand,
            SmartCapability.ConnectionRepair,
            SmartCapability.Keyboard,
            SmartCapability.Clipboard,
            SmartCapability.MacInput,
        ),
    ) = SmartContext(
        currentSurface = currentSurface,
        selectedMacId = SmartMacId("mac_123"),
        macConnected = macConnected,
        macInputConnected = true,
        activeMacApp = activeMacApp,
        recentActionIds = recentActionIds,
        supportedCapabilities = supportedCapabilities,
        hourBucket = 12,
        createdAtMillis = now,
        expiresAtMillis = now + 60_000L,
        phoneContext = SmartPhoneContext.Desktop,
    )

    private fun actions() = listOf(
        SmartActionRef(
            id = "reload",
            title = "Reload browser",
            description = "Refresh tab",
            kind = SmartActionKind.MacCommand,
            route = null,
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            dangerous = false,
        ),
        SmartActionRef(
            id = "browser",
            title = "Open Chrome",
            description = "Browser",
            kind = SmartActionKind.LocalNavigation,
            route = "trackpad",
            requiredCapabilities = setOf(SmartCapability.LocalNavigation),
            dangerous = false,
        ),
        SmartActionRef(
            id = "danger",
            title = "Danger",
            description = "Dangerous terminal action",
            kind = SmartActionKind.MacCommand,
            route = null,
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            dangerous = true,
        ),
    )

    private fun mediaActions() = listOf(
        SmartActionRef(
            id = "hid_media_play_pause",
            title = "Play/Pause",
            description = "Play or pause",
            kind = SmartActionKind.LocalNavigation,
            route = "hid_media_play_pause",
            requiredCapabilities = setOf(SmartCapability.LocalNavigation, SmartCapability.MacInput),
            dangerous = false,
        ),
    )

    private class FakeScoredProvider(
        private val providerId: String,
        private val actions: List<SmartActionRef>,
        private val baseScore: Int = 10,
    ) : SmartCandidateProvider {
        override val id: String = providerId

        override fun candidates(
            context: SmartContext,
            actions: List<SmartActionRef>,
            feedback: SmartFeedbackSummary,
            nowMillis: Long,
        ): List<ScoredSmartCandidate> = actions
            .filter { it.id in this@FakeScoredProvider.actions.map { action -> action.id } }
            .mapIndexed { index, action ->
                ScoredSmartCandidate(
                    candidate = SmartCandidate(
                        id = smartCandidateId(context.currentSurface, context.activeMacApp, action.id),
                        actionId = action.id,
                        title = action.title,
                        summary = action.description,
                        reason = "fake $index",
                        confidenceLabel = SmartConfidenceLabel.Possible,
                        capabilities = action.requiredCapabilities,
                        risks = setOf(SmartRisk.Normal),
                        contextKeys = context.sanitizedKeys(),
                        expiresAtMillis = context.expiresAtMillis,
                    ),
                    score = baseScore - index,
                    providerId = providerId,
                )
            }
    }
}
