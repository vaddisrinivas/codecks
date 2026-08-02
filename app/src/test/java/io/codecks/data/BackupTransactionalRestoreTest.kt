package io.codecks.data

import io.codecks.domain.backup.BackupRestoreResult
import io.codecks.domain.backup.RestorePlan
import io.codecks.domain.backup.RestoreStage
import io.codecks.domain.backup.RestoreTerminalResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTransactionalRestoreTest {
    @Test
    fun everyForwardBoundaryFailureRestoresExactPriorLogicalState() = runTest {
        val forwardBoundaries = listOf(
            BackupFailurePoint.AfterStage to RestoreStage.Stage,
            BackupFailurePoint.AfterMigration to RestoreStage.Migrate,
            BackupFailurePoint.AfterValidation to RestoreStage.Validate,
            BackupFailurePoint.AfterDeckApply to RestoreStage.ApplyDeck,
            BackupFailurePoint.AfterAutomationsApply to RestoreStage.ApplyAutomations,
            BackupFailurePoint.BeforeVerification to RestoreStage.Verify,
            BackupFailurePoint.BeforeCommit to RestoreStage.Commit,
        )

        forwardBoundaries.forEach { (failurePoint, expectedFailedStage) ->
            val fakes = BackupFakes()
            val priorDeck = fakes.deck.exported
            val priorAutomations = fakes.automations.exported
            val injector = SetBackupFailureInjector(setOf(failurePoint))
            val repository = fakes.repository(failureInjector = injector)
            val backup = legacyBackup("{\"deck\":\"replacement\"}", "{\"rules\":\"replacement\"}")
            val plan = repository.createRestorePlan(backup).getOrThrow() as RestorePlan.Ready

            val outcome = repository.restoreConfirmed(plan.planId, backup).getOrThrow()

            assertTrue("$failurePoint must roll back", outcome is BackupRestoreResult.RolledBack)
            assertEquals(priorDeck, fakes.deck.exported)
            assertEquals(priorAutomations, fakes.automations.exported)
            assertEquals(RestoreTerminalResult.RolledBack, outcome.receipt.terminalResult)
            assertEquals(expectedFailedStage, outcome.receipt.failedStage)
            assertEquals(RestoreStage.Rollback, outcome.receipt.completedStages.last())
        }
    }

    @Test
    fun successfulTransactionReturnsCommittedReceiptAfterExactVerification() = runTest {
        val fakes = BackupFakes()
        val repository = fakes.repository()
        val deck = "{\"deck\":\"replacement\"}"
        val automations = "{\"rules\":\"replacement\"}"
        val backup = legacyBackup(deck, automations)
        val plan = repository.createRestorePlan(backup).getOrThrow() as RestorePlan.Ready

        val outcome = repository.restoreConfirmed(plan.planId, backup).getOrThrow()

        assertTrue(outcome is BackupRestoreResult.Committed)
        assertEquals(deck, fakes.deck.exported)
        assertEquals(automations, fakes.automations.exported)
        assertEquals(RestoreTerminalResult.Committed, outcome.receipt.terminalResult)
        assertEquals(RestoreStage.Commit, outcome.receipt.completedStages.last())
    }
}

internal class SetBackupFailureInjector(
    private val failures: Set<BackupFailurePoint>,
) : BackupFailureInjector {
    override fun failAt(point: BackupFailurePoint) {
        if (point in failures) error("Injected restore failure at $point")
    }
}
