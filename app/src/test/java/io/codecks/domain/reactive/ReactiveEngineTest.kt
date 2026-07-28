package io.codecks.domain.reactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactiveEngineTest {
    private val now = 9_000L

    @Test
    fun missingCapabilityRemovesControl() {
        val engine = DeterministicReactiveEngine(
            providers = listOf(
                FakeReactiveProvider(
                    control(
                        id = "reload",
                        basePriority = 10,
                        requiredCapabilities = setOf(CodecksCapability.MacCommand),
                    ),
                ),
            ),
        )

        val decision = engine.controls(
            state = state(capabilities = emptySet()),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertTrue(decision.controls.isEmpty())
    }

    @Test
    fun hiddenControlIsRemoved() {
        val hiddenId = ControlId("reactive-hidden")
        val engine = DeterministicReactiveEngine(
            providers = listOf(
                FakeReactiveProvider(
                    control(
                        id = hiddenId.value,
                        basePriority = 10,
                        requiredCapabilities = emptySet(),
                    ),
                ),
            ),
            policy = object : ReactivePolicy {
                override fun allows(
                    control: ReactiveControl,
                    state: MacStateSnapshot,
                    context: ReactiveTrackpadContext,
                    nowMillis: Long,
                ): Boolean = control.id !in context.hiddenControlIds
            },
        )

        val decision = engine.controls(
            state = state(),
            context = ReactiveTrackpadContext(hiddenControlIds = setOf(hiddenId)),
            nowMillis = now,
        )

        assertTrue(decision.controls.isEmpty())
    }

    @Test
    fun dangerousPenaltyCanChangeRanking() {
        val safe = control(id = "safe", basePriority = 20, risk = ReactiveRisk.Safe)
        val dangerous = control(id = "danger", basePriority = 40, risk = ReactiveRisk.Dangerous)

        val decision = DeterministicReactiveEngine(
            providers = listOf(FakeReactiveProvider(safe, dangerous)),
        ).controls(
            state = state(),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertEquals(listOf("safe", "danger"), decision.controls.map { it.actionId() })
    }

    @Test
    fun deterministicTieBreakUsesControlId() {
        val first = control(id = "reactive-a", actionId = "a", basePriority = 10)
        val second = control(id = "reactive-b", actionId = "b", basePriority = 10)

        val decision = DeterministicReactiveEngine(
            providers = listOf(FakeReactiveProvider(second, first)),
        ).controls(
            state = state(),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertEquals(listOf("a", "b"), decision.controls.map { it.actionId() })
    }

    @Test
    fun duplicateMismatchIsRejected() {
        val first = control(id = "reactive-dupe", actionId = "reload", basePriority = 10)
        val second = control(
            id = "reactive-dupe",
            actionId = "reload",
            basePriority = 20,
            risk = ReactiveRisk.Dangerous,
        )

        val decision = DeterministicReactiveEngine(
            providers = listOf(FakeReactiveProvider(first, second)),
        ).controls(
            state = state(),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertTrue(decision.controls.isEmpty())
        assertEquals(listOf("duplicate_mismatch:reactive-dupe"), decision.contractErrors)
    }

    @Test
    fun staleStatePenaltyIsApplied() {
        val control = control(id = "reactive-stale", actionId = "reload", basePriority = 10)

        val score = reactiveScore(
            control = control,
            state = state(capturedAtMillis = 1_000L),
            context = ReactiveTrackpadContext(),
            nowMillis = 5_000L,
        )

        assertEquals(7, score)
    }

    @Test
    fun deniedPolicyAndAllowConflictsAreRemoved() {
        val denied = control(
            id = "reactive-denied",
            actionId = "denied",
            basePriority = 80,
            policy = ReactiveControlPolicy.Deny,
        )
        val conflicted = control(
            id = "reactive-conflict",
            actionId = "conflict",
            basePriority = 80,
            conflicts = listOf(ReactiveControlConflict("same_slot")),
        )
        val reviewConflict = control(
            id = "reactive-review",
            actionId = "review",
            basePriority = 80,
            policy = ReactiveControlPolicy.RequiresReview,
            conflicts = listOf(ReactiveControlConflict("needs_user_pick")),
        )

        val decision = DeterministicReactiveEngine(
            providers = listOf(FakeReactiveProvider(denied, conflicted, reviewConflict)),
        ).controls(
            state = state(),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertEquals(listOf("review"), decision.controls.map { it.actionId() })
    }

    @Test
    fun staleDangerousControlIsDeniedButSafeControlIsDowngraded() {
        val safe = control(
            id = "reactive-safe",
            actionId = "safe",
            basePriority = 80,
            staleBehavior = ReactiveStaleBehavior.Downgrade,
        )
        val dangerous = control(
            id = "reactive-danger",
            actionId = "danger",
            basePriority = 100,
            risk = ReactiveRisk.Dangerous,
            staleBehavior = ReactiveStaleBehavior.Deny,
        )

        val decision = DeterministicReactiveEngine(
            providers = listOf(FakeReactiveProvider(dangerous, safe)),
        ).controls(
            state = state(capturedAtMillis = 1_000L),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertEquals(listOf("safe"), decision.controls.map { it.actionId() })
        assertTrue(decision.controls.single().reason.contains("stale_state_downgraded"))
    }

    private fun state(
        capturedAtMillis: Long = now,
        capabilities: Set<CapabilityState> = setOf(
            CapabilityState(CodecksCapability.MacCommand, CapabilityAvailability.Available),
        ),
    ): MacStateSnapshot = MacStateSnapshot(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 1L,
        capturedAtMillis = capturedAtMillis,
        frontApp = observed(
            MacApplication(
                bundleId = "com.google.Chrome",
                displayName = "Google Chrome",
                kind = MacAppKind.Browser,
            ),
        ),
        activeWindow = observed<MacWindow>(null),
        displays = observed(emptyList()),
        cursor = observed<MacCursorState>(null),
        selection = observed(MacSelection.None),
        clipboard = observed<MacClipboardMetadata>(null),
        media = observed<MacMediaState>(null),
        system = observed<MacSystemState>(null),
        meeting = observed<MacMeetingState>(null),
        latestScreenshot = observed<MacScreenshotState>(null),
        capabilities = capabilities,
    )

    private fun control(
        id: String,
        actionId: String = id,
        basePriority: Int,
        requiredCapabilities: Set<CodecksCapability> = setOf(CodecksCapability.MacCommand),
        risk: ReactiveRisk = ReactiveRisk.Safe,
        policy: ReactiveControlPolicy = ReactiveControlPolicy.Allow,
        conflicts: List<ReactiveControlConflict> = emptyList(),
        staleBehavior: ReactiveStaleBehavior = ReactiveStaleBehavior.Downgrade,
    ): ReactiveControl = ReactiveControl(
        id = ControlId(id),
        providerId = "fake",
        actionId = actionId,
        title = actionId,
        subtitle = null,
        icon = ReactiveIcon.Generic,
        action = ReactiveAction.ExistingCatalog(actionId),
        source = ReactiveControlSource.FrontApp,
        basePriority = basePriority,
        reason = "test",
        explanation = "test explanation",
        policy = policy,
        conflicts = conflicts,
        requiredCapabilities = requiredCapabilities,
        risk = risk,
        staleBehavior = staleBehavior,
        reversible = false,
        stateRevision = 1L,
        actionRevision = ActionRevision(actionId.padEnd(32, '0').take(32)),
        expiresAtMillis = Long.MAX_VALUE,
    )

    private fun ReactiveControl.actionId(): String = (action as ReactiveAction.ExistingCatalog).actionId

    private fun <T> observed(value: T?): Observed<T> = Observed(
        value = value,
        status = if (value == null) ObservationStatus.Unavailable else ObservationStatus.Fresh,
        observedAtMillis = now,
        source = StateSource.Helper,
    )
}

private class FakeReactiveProvider(
    private vararg val controls: ReactiveControl,
) : ReactiveControlProvider {
    override val id: String = "fake"

    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl> = controls.toList()
}
