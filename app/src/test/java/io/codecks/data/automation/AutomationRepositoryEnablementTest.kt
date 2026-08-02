package io.codecks.data.automation

import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.ActionResultStatus
import io.codecks.domain.automation.AutomationGateStamp
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationRunSummary
import io.codecks.domain.automation.AutomationStage
import io.codecks.domain.automation.enforceRevisionGate
import io.codecks.domain.automation.hasCurrentRevisionGateForExecution
import io.codecks.domain.automation.revisionFingerprint
import io.codecks.domain.automation.withoutImportedExecutionProof
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRepositoryEnablementTest {
    @Test
    fun newEnabledRecipeIsPersistedDisabledAtDraft() {
        val requested = recipe().copy(enabled = true)

        val stored = requested.enforceRevisionGate(previous = null)

        assertFalse(stored.enabled)
        assertEquals(AutomationStage.DRAFT, stored.stage)
        assertFalse(stored.hasCurrentRevisionGateForExecution())
    }

    @Test
    fun forgedEnabledStageWithoutReceiptIdsIsRejected() {
        val requested = recipe().let { recipe ->
            recipe.copy(
                enabled = true,
                stage = AutomationStage.ENABLED,
                gateStamp = AutomationGateStamp(revisionId = recipe.revisionFingerprint()),
            )
        }

        val stored = requested.enforceRevisionGate(previous = requested.copy(enabled = false))

        assertFalse(stored.enabled)
        assertEquals(AutomationStage.NEEDS_REVIEW, stored.stage)
    }

    @Test
    fun importedRecipeAlwaysLosesExecutionProofAndHistory() {
        val forged = recipe().let { recipe ->
            val revision = recipe.revisionFingerprint()
            val result = AutomationRunSummary(
                status = ActionResultStatus.Succeeded,
                message = "forged",
                timestampMillis = 1L,
            )
            recipe.copy(
                enabled = true,
                stage = AutomationStage.ENABLED,
                gateStamp = AutomationGateStamp(
                    revisionId = revision,
                    validationReceiptId = "forged-validation",
                    preflightReceiptId = "forged-preflight",
                    liveTestReceiptId = "forged-live-test",
                ),
                lastTest = result,
                lastTestRevision = revision,
                lastRun = result,
                runHistory = listOf(result),
            )
        }

        val imported = forged.withoutImportedExecutionProof()

        assertFalse(imported.enabled)
        assertEquals(AutomationStage.DRAFT, imported.stage)
        assertEquals(imported.revisionFingerprint(), imported.gateStamp?.revisionId)
        assertEquals(null, imported.gateStamp?.validationReceiptId)
        assertEquals(null, imported.lastTest)
        assertEquals(null, imported.lastRun)
        assertTrue(imported.runHistory.isEmpty())
    }

    private fun recipe(): AutomationRecipe =
        AutomationRecipe(
            id = "focus",
            title = "Focus",
            description = "Focus",
            enabled = false,
            steps = listOf(ActionSpec.ShellCommand("step", "Step", "open -a Notes")),
        )
}
