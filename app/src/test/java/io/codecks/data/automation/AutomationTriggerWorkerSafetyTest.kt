package io.codecks.data.automation

import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationRunSummary
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.revisionFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class AutomationTriggerWorkerSafetyTest {
    @Test
    fun persistedEnabledRuleWithoutTest_isBlockedFromBackgroundExecution() {
        val result = automaticRunBlocker(recipe())

        assertNotNull(result)
        assertEquals(ActionResultStatus.RequiresReview, result?.status)
        assertEquals(
            "Trigger matched, but Focus Mode needs a successful test for its current revision",
            result?.message,
        )
    }

    @Test
    fun successfulTestForOlderRevision_isStillBlockedFromBackgroundExecution() {
        val recipe = recipe().copy(
            lastTest = AutomationRunSummary(ActionResultStatus.Succeeded, "Passed"),
            lastTestRevision = "old-command-revision",
        )

        assertNotNull(automaticRunBlocker(recipe))
    }

    @Test
    fun successfulTestForCurrentRevision_allowsBackgroundExecution() {
        val untested = recipe()
        val tested = untested.copy(
            lastTest = AutomationRunSummary(ActionResultStatus.Succeeded, "Passed"),
            lastTestRevision = untested.revisionFingerprint(),
        )

        assertNull(automaticRunBlocker(tested))
    }

    private fun recipe() = AutomationRecipe(
        id = "focus",
        title = "Focus Mode",
        description = "Run when Chrome is active",
        enabled = true,
        trigger = AutomationTrigger.ActiveApp("Chrome"),
        steps = listOf(ActionSpec.ShellCommand("focus", "Focus Mode", "open -a Notes")),
    )
}
