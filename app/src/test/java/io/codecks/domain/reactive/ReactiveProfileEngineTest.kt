package io.codecks.domain.reactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactiveProfileEngineTest {
    private val now = 9_000L

    @Test
    fun disabledProviderIsSkipped() {
        val decision = DeterministicReactiveEngine(
            providers = listOf(
                TestProvider("browser", control("browser-control", basePriority = 100)),
                TestProvider("finder", control("finder-control", basePriority = 10)),
            ),
        ).controls(
            state = state(),
            context = ReactiveTrackpadContext(disabledProviderIds = setOf("browser")),
            nowMillis = now,
        )

        assertEquals(listOf("finder-control"), decision.controls.map { it.id.value })
    }

    @Test
    fun pinnedControlsLeadInPreferenceOrder() {
        val pinnedSecond = ControlId("reactive-second")
        val pinnedFirst = ControlId("reactive-first")

        val decision = DeterministicReactiveEngine(
            providers = listOf(
                TestProvider(
                    "profile",
                    control(pinnedSecond.value, basePriority = 1),
                    control("reactive-third", basePriority = 100),
                    control(pinnedFirst.value, basePriority = 1),
                ),
            ),
        ).controls(
            state = state(),
            context = ReactiveTrackpadContext(pinnedControlIds = listOf(pinnedFirst, pinnedSecond)),
            nowMillis = now,
        )

        assertEquals(
            listOf("reactive-first", "reactive-second", "reactive-third"),
            decision.controls.map { it.id.value },
        )
    }

    @Test
    fun contextMaxControlsLimitsRankedControls() {
        val decision = DeterministicReactiveEngine(
            providers = listOf(
                TestProvider(
                    "profile",
                    control("reactive-a", basePriority = 30),
                    control("reactive-b", basePriority = 20),
                    control("reactive-c", basePriority = 10),
                ),
            ),
        ).controls(
            state = state(),
            context = ReactiveTrackpadContext(maxControls = 2),
            nowMillis = now,
        )

        assertEquals(listOf("reactive-a", "reactive-b"), decision.controls.map { it.id.value })
    }

    @Test
    fun hiddenControlStillWinsOverPinnedControl() {
        val hidden = ControlId("reactive-hidden")

        val decision = DeterministicReactiveEngine(
            providers = listOf(TestProvider("profile", control(hidden.value, basePriority = 100))),
        ).controls(
            state = state(),
            context = ReactiveTrackpadContext(
                hiddenControlIds = setOf(hidden),
                pinnedControlIds = listOf(hidden),
            ),
            nowMillis = now,
        )

        assertTrue(decision.controls.isEmpty())
    }

    private fun state(): MacStateSnapshot = MacStateSnapshot(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 1L,
        capturedAtMillis = now,
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
        capabilities = setOf(CapabilityState(CodecksCapability.MacCommand, CapabilityAvailability.Available)),
    )

    private fun control(
        id: String,
        basePriority: Int,
    ): ReactiveControl = ReactiveControl(
        id = ControlId(id),
        title = id,
        subtitle = null,
        icon = ReactiveIcon.Generic,
        action = ReactiveAction.ExistingCatalog(id),
        source = ReactiveControlSource.FrontApp,
        basePriority = basePriority,
        reason = "test",
        requiredCapabilities = setOf(CodecksCapability.MacCommand),
        risk = ReactiveRisk.Safe,
        reversible = false,
        stateRevision = 1L,
        actionRevision = ActionRevision(id.padEnd(32, '0').take(32)),
        expiresAtMillis = Long.MAX_VALUE,
    )

    private fun <T> observed(value: T?): Observed<T> = Observed(
        value = value,
        status = if (value == null) ObservationStatus.Unavailable else ObservationStatus.Fresh,
        observedAtMillis = now,
        source = StateSource.Helper,
    )
}

private class TestProvider(
    override val id: String,
    private vararg val controls: ReactiveControl,
) : ReactiveControlProvider {
    override fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl> = controls.toList()
}
