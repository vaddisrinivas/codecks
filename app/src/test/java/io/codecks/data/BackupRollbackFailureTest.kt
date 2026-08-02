package io.codecks.data

import io.codecks.domain.backup.BackupRestoreResult
import io.codecks.domain.backup.RestorePlan
import io.codecks.domain.backup.RestoreStage
import io.codecks.domain.backup.RestoreTerminalResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRollbackFailureTest {
    @Test
    fun eitherRollbackMutationFailureRequiresRecoveryAndPreservesExactSnapshot() = runTest {
        listOf(
            BackupFailurePoint.RollbackDeck,
            BackupFailurePoint.RollbackAutomations,
        ).forEach { rollbackFailure ->
            val fakes = BackupFakes()
            val priorDeck = fakes.deck.exported
            val priorAutomations = fakes.automations.exported
            val recoveryStore = InMemoryBackupRecoveryStore()
            val repository = fakes.repository(
                failureInjector = SetBackupFailureInjector(
                    setOf(BackupFailurePoint.AfterAutomationsApply, rollbackFailure),
                ),
                recoveryStore = recoveryStore,
            )
            val backup = legacyBackup("{\"deck\":\"replacement\"}", "{\"rules\":\"replacement\"}")
            val plan = repository.createRestorePlan(backup).getOrThrow() as RestorePlan.Ready

            val outcome = repository.restoreConfirmed(plan.planId, backup).getOrThrow()

            assertTrue(outcome is BackupRestoreResult.RecoveryRequired)
            outcome as BackupRestoreResult.RecoveryRequired
            assertEquals(RestoreTerminalResult.RecoveryRequired, outcome.receipt.terminalResult)
            assertEquals(RestoreStage.ApplyAutomations, outcome.receipt.failedStage)
            assertTrue(recoveryStore.contains(outcome.recoveryId))
            assertEquals(
                mapOf("deck" to priorDeck, "automations" to priorAutomations),
                recoveryStore.load(outcome.recoveryId),
            )
        }
    }
}
