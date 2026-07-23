package io.codecks.domain.smart.providers

import io.codecks.domain.smart.SmartAppActionMapping
import io.codecks.domain.smart.DeterministicSmartEngine
import io.codecks.domain.smart.ScoredSmartCandidate
import io.codecks.domain.smart.SmartCandidateProvider
import io.codecks.domain.smart.SmartActionRef
import io.codecks.domain.smart.SmartAppKey
import io.codecks.domain.smart.SmartCandidate
import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.smart.SmartConfidenceLabel
import io.codecks.domain.smart.SmartContext
import io.codecks.domain.smart.SmartActionKind
import io.codecks.domain.smart.SmartFeedbackSummary
import io.codecks.domain.smart.SmartRisk
import io.codecks.domain.smart.SmartMacId
import io.codecks.domain.smart.SmartPhoneContext
import io.codecks.domain.smart.SmartSurface
import io.codecks.domain.smart.smartCandidateId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCandidateProviderTest {

    private val now = 9_000L

    @Test
    fun browserContextPromotesBrowserControls() {
        val context = smartContext(activeMacApp = SmartAppKey("Google Chrome"))
        val provider = ActiveAppCandidateProvider(
            mappings = listOf(
                SmartAppActionMapping(
                    appTokens = setOf("chrome", "safari"),
                    actionTokens = setOf("reload", "browser"),
                    reason = "Useful while browser is active",
                    score = 18,
                ),
            ),
        )

        val action = SmartActionRef(
            id = "reload",
            title = "Reload",
            description = "Refresh current tab",
            kind = SmartActionKind.MacCommand,
            route = "finder",
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            dangerous = false,
        )

        val candidates = provider.candidates(
            context = context,
            actions = listOf(action),
            feedback = SmartFeedbackSummary(),
            nowMillis = now,
        )

        assertEquals(1, candidates.size)
        assertEquals("reload", candidates.single().candidate.actionId)
    }

    @Test
    fun ideContextPromotesDeveloperControls() {
        val context = smartContext(activeMacApp = SmartAppKey("Visual Studio Code"))
        val provider = ActiveAppCandidateProvider(
            mappings = listOf(
                SmartAppActionMapping(
                    appTokens = setOf("visual studio code", "vscode", "xcode", "android studio"),
                    actionTokens = setOf("terminal", "github", "coding"),
                    reason = "Useful during coding",
                    score = 18,
                ),
            ),
        )

        val action = SmartActionRef(
            id = "terminal",
            title = "Open Terminal",
            description = "Show terminal",
            kind = SmartActionKind.LocalNavigation,
            route = "terminal",
            requiredCapabilities = setOf(SmartCapability.LocalNavigation),
            dangerous = false,
        )

        val candidates = provider.candidates(context, listOf(action), SmartFeedbackSummary(), now)

        assertEquals(1, candidates.size)
        assertEquals("terminal", candidates.single().candidate.actionId)
    }

    @Test
    fun meetingContextPromotesMeetingControls() {
        val context = smartContext(activeMacApp = SmartAppKey("Zoom"))
        val provider = ActiveAppCandidateProvider(
            mappings = listOf(
                SmartAppActionMapping(
                    appTokens = setOf("zoom", "teams", "meet"),
                    actionTokens = setOf("mute", "meeting", "notes"),
                    reason = "Useful during meetings",
                    score = 18,
                ),
            ),
        )

        val action = SmartActionRef(
            id = "mute",
            title = "Mute",
            description = "Mute microphone",
            kind = SmartActionKind.LocalNavigation,
            route = "clipboard",
            requiredCapabilities = setOf(SmartCapability.LocalNavigation),
            dangerous = false,
        )

        val candidates = provider.candidates(context, listOf(action), SmartFeedbackSummary(), now)

        assertEquals(1, candidates.size)
        assertEquals("mute", candidates.single().candidate.actionId)
    }

    @Test
    fun disconnectedContextPromotesSetup() {
        val context = smartContext(activeMacApp = SmartAppKey("X"), macConnected = false)
        val provider = ConnectionRepairCandidateProvider()

        val action = SmartActionRef(
            id = "settings",
            title = "Setup",
            description = "Pair this Mac",
            kind = SmartActionKind.MacCommand,
            route = "settings",
            requiredCapabilities = setOf(SmartCapability.ConnectionRepair),
            dangerous = false,
        )

        val candidates = provider.candidates(context, listOf(action), SmartFeedbackSummary(), now)

        assertEquals(1, candidates.size)
        assertEquals("settings", candidates.first().candidate.actionId)
    }

    @Test
    fun duplicateCandidatesAreMergedAndKeepHighestScore() {
        val context = smartContext(activeMacApp = SmartAppKey("Chrome"))
        val action = SmartActionRef(
            id = "finder",
            title = "Finder",
            description = "Open Finder",
            kind = SmartActionKind.LocalNavigation,
            route = "finder",
            requiredCapabilities = setOf(SmartCapability.LocalNavigation),
            dangerous = false,
        )

        val engine = DeterministicSmartEngine(
            providers = listOf(
                FakeScoredProvider(
                    providerId = "recent",
                    actionId = "finder",
                    score = 10,
                    reason = "recent",
                ),
                FakeScoredProvider(
                    providerId = "transition",
                    actionId = "finder",
                    score = 20,
                    reason = "transition",
                ),
            ),
        )

        val ranked = engine.suggest(context, listOf(action), SmartFeedbackSummary(), now).candidates

        assertEquals(1, ranked.size)
        assertEquals("finder", ranked.single().actionId)
        assertTrue(ranked.single().reason.contains("recent"))
        assertTrue(ranked.single().reason.contains("transition"))
    }

    @Test
    fun dangerousCandidatesRemainPenalizedByPolicy() {
        val context = smartContext(activeMacApp = SmartAppKey("Chrome"))
        val action = SmartActionRef(
            id = "danger",
            title = "Danger",
            description = "Dangerous action",
            kind = SmartActionKind.MacCommand,
            route = null,
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            dangerous = true,
        )

        val engine = DeterministicSmartEngine(
            providers = listOf(
                FakeScoredProvider(
                    providerId = "recent",
                    actionId = "danger",
                    score = 29,
                    reason = "dangerous",
                    dangerous = true,
                ),
            ),
        )

        val ranked = engine.suggest(context, listOf(action), SmartFeedbackSummary(), now).candidates

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun missingCapabilityRemovesCandidate() {
        val context = smartContext(
            activeMacApp = SmartAppKey("Chrome"),
            supportedCapabilities = setOf(SmartCapability.LocalNavigation),
        )
        val action = SmartActionRef(
            id = "finder",
            title = "Finder",
            description = "Launch finder",
            kind = SmartActionKind.MacCommand,
            route = "finder",
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            dangerous = false,
        )

        val provider = FakeScoredProvider(
            providerId = "recent",
            actionId = "finder",
            score = 30,
            reason = "command",
            requiredCapabilities = setOf(SmartCapability.MacCommand),
        )

        val engine = DeterministicSmartEngine(providers = listOf(provider))
        val ranked = engine.suggest(context, listOf(action), SmartFeedbackSummary(), now).candidates

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun repeatedRankingForSameContextIsStable() {
        val context = smartContext(activeMacApp = SmartAppKey("Chrome"))
        val actions = listOf(
            SmartActionRef(
                id = "reload",
                title = "Reload",
                description = "Refresh",
                kind = SmartActionKind.MacCommand,
                route = null,
                requiredCapabilities = setOf(SmartCapability.MacCommand),
                dangerous = false,
            ),
            SmartActionRef(
                id = "finder",
                title = "Finder",
                description = "Browse files",
                kind = SmartActionKind.LocalNavigation,
                route = "finder",
                requiredCapabilities = setOf(SmartCapability.LocalNavigation),
                dangerous = false,
            ),
        )
        val first = DeterministicSmartEngine().suggest(context, actions, SmartFeedbackSummary(), now).candidates
            .map { it.actionId }
        val second = DeterministicSmartEngine().suggest(context, actions, SmartFeedbackSummary(), now).candidates
            .map { it.actionId }

        assertEquals(first, second)
    }

    @Test
    fun recentProviderLeavesFeedbackScoringToEngine() {
        val context = smartContext(
            activeMacApp = SmartAppKey("Chrome"),
            recentActionIds = listOf("current", "target"),
        )
        val action = SmartActionRef(
            id = "target",
            title = "Target",
            description = "Target",
            kind = SmartActionKind.LocalNavigation,
            route = null,
            requiredCapabilities = setOf(SmartCapability.LocalNavigation),
            dangerous = false,
        )

        val candidate = RecentActionCandidateProvider().candidates(
            context = context,
            actions = listOf(action),
            feedback = SmartFeedbackSummary(actionScores = mapOf("target" to 100)),
            nowMillis = now,
        ).single()

        assertEquals(13, candidate.score)
    }

    @Test
    fun engineAppliesFeedbackScoreExactlyOnce() {
        val context = smartContext(activeMacApp = SmartAppKey("Chrome"))
        val actions = listOf(
            SmartActionRef(
                id = "learned",
                title = "Learned",
                description = "Learned",
                kind = SmartActionKind.LocalNavigation,
                route = null,
                requiredCapabilities = setOf(SmartCapability.LocalNavigation),
                dangerous = false,
            ),
            SmartActionRef(
                id = "baseline",
                title = "Baseline",
                description = "Baseline",
                kind = SmartActionKind.LocalNavigation,
                route = null,
                requiredCapabilities = setOf(SmartCapability.LocalNavigation),
                dangerous = false,
            ),
        )
        val engine = DeterministicSmartEngine(
            providers = listOf(
                FakeScoredProvider("learned-provider", "learned", 10, "learned"),
                FakeScoredProvider("baseline-provider", "baseline", 20, "baseline"),
            ),
        )

        val ranked = engine.suggest(
            context = context,
            actions = actions,
            feedback = SmartFeedbackSummary(actionScores = mapOf("learned" to 6)),
            nowMillis = now,
        ).candidates.mapNotNull(SmartCandidate::actionId)

        assertEquals(listOf("baseline", "learned"), ranked)
    }

    private fun smartContext(
        activeMacApp: SmartAppKey,
        currentSurface: SmartSurface = SmartSurface.Deck,
        selectedMacId: SmartMacId = SmartMacId("mac_123"),
        macConnected: Boolean = true,
        supportedCapabilities: Set<SmartCapability> = setOf(
            SmartCapability.LocalNavigation,
            SmartCapability.MacCommand,
            SmartCapability.ConnectionRepair,
            SmartCapability.Keyboard,
            SmartCapability.Clipboard,
        ),
        recentActionIds: List<String> = emptyList(),
    ) = SmartContext(
        currentSurface = currentSurface,
        selectedMacId = selectedMacId,
        macConnected = macConnected,
        macInputConnected = true,
        activeMacApp = activeMacApp,
        recentActionIds = recentActionIds,
        supportedCapabilities = supportedCapabilities,
        hourBucket = 12,
        createdAtMillis = now,
        expiresAtMillis = now + 30_000L,
        phoneContext = SmartPhoneContext.Desktop,
    )

    private class FakeScoredProvider(
        private val providerId: String,
        private val actionId: String,
        private val score: Int,
        private val reason: String,
        private val dangerous: Boolean = false,
        private val requiredCapabilities: Set<SmartCapability> = setOf(SmartCapability.LocalNavigation),
    ) : SmartCandidateProvider {

        override val id: String = providerId

        override fun candidates(
            context: SmartContext,
            actions: List<SmartActionRef>,
            feedback: SmartFeedbackSummary,
            nowMillis: Long,
        ): List<ScoredSmartCandidate> = actions
            .filter { it.id == actionId }
            .map { action ->
                ScoredSmartCandidate(
                    candidate = SmartCandidate(
                        id = smartCandidateId(context.currentSurface, context.activeMacApp, action.id),
                        actionId = action.id,
                        title = action.title,
                        summary = action.description,
                        reason = reason,
                        confidenceLabel = SmartConfidenceLabel.Possible,
                        capabilities = requiredCapabilities,
                        risks = if (dangerous) setOf(SmartRisk.Dangerous) else setOf(SmartRisk.Normal),
                        contextKeys = context.sanitizedKeys(),
                        expiresAtMillis = context.expiresAtMillis,
                    ),
                    score = score,
                    providerId = providerId,
                )
            }
    }
}
