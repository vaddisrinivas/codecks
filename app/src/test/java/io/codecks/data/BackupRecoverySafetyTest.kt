package io.codecks.data

import io.codecks.domain.backup.RestorePlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRecoverySafetyTest {
    @Test
    fun pendingStartupRecoveryBlocksNewRestoreUntilExactRecoveryCompletes() = runTest {
        val fakes = BackupFakes()
        val store = InMemoryBackupRecoveryStore()
        val recoveryId = store.save(
            mapOf("deck" to "{\"prior\":1}", "automations" to "{\"prior\":2}"),
        )
        val repository = fakes.repository(recoveryStore = store)
        val backup = legacyBackup("{\"next\":1}", "{\"next\":2}")

        assertEquals(recoveryId, repository.pendingRecoveryId())
        assertTrue(repository.createRestorePlan(backup).isFailure)
        repository.recoverPending(recoveryId).getOrThrow()

        assertNull(repository.pendingRecoveryId())
        assertEquals("{\"prior\":1}", fakes.deck.exported)
        assertEquals("{\"prior\":2}", fakes.automations.exported)
        assertTrue(repository.createRestorePlan(backup).getOrThrow() is RestorePlan.Ready)
    }

    @Test
    fun cancellationRollsBackExactlyThenPropagates() = runTest {
        assertPropagatesAfterRollback(CancellationException("cancel"))
    }

    @Test
    fun fatalErrorRollsBackExactlyThenPropagates() = runTest {
        assertPropagatesAfterRollback(LinkageError("fatal"))
    }

    @Test
    fun secretShapedAutomationCommandBlocksArchiveExport() = runTest {
        val fakes = BackupFakes()
        fakes.automations.exported =
            """{"schemaVersion":3,"command":"API_KEY=abcdefgh12345678"}"""

        val result = fakes.repository().exportArchive()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("secret-shaped"))
    }

    @Test
    fun diagnosticFailureCannotTurnCommittedRestoreIntoRollback() = runTest {
        val fakes = BackupFakes()
        val store = InMemoryBackupRecoveryStore()
        val repository = fakes.repository(
            recoveryStore = store,
            terminalEvent = { _, _, _ -> error("diagnostic backend unavailable") },
        )
        val backup = legacyBackup("{\"next\":1}", "{\"next\":2}")
        val plan = repository.createRestorePlan(backup).getOrThrow() as RestorePlan.Ready

        val outcome = repository.restoreConfirmed(plan.planId, backup).getOrThrow()

        assertTrue(outcome is io.codecks.domain.backup.BackupRestoreResult.Committed)
        assertNull(repository.pendingRecoveryId())
        assertEquals("{\"next\":1}", fakes.deck.exported)
        assertEquals("{\"next\":2}", fakes.automations.exported)
    }

    @Test
    fun recoveryRequiredOutcomeIsImmediatelyPublishedAndClosesStalePreview() {
        val source = java.io.File("src/main/java/io/codecks/MainActivity.kt").readText()

        assertTrue(source.contains("pendingBackupRecoveryId = outcome.recoveryId"))
        assertTrue(source.contains("pendingRestorePayload = null"))
        assertTrue(source.contains("pendingRestorePlan = null"))
    }

    private suspend fun assertPropagatesAfterRollback(thrown: Throwable) {
        val fakes = BackupFakes()
        val priorDeck = fakes.deck.exported
        val priorAutomations = fakes.automations.exported
        val repository = fakes.repository(
            failureInjector = BackupFailureInjector { point ->
                if (point == BackupFailurePoint.AfterAutomationsApply) throw thrown
            },
        )
        val backup = legacyBackup("{\"next\":1}", "{\"next\":2}")
        val plan = repository.createRestorePlan(backup).getOrThrow() as RestorePlan.Ready

        val observed = try {
            repository.restoreConfirmed(plan.planId, backup)
            null
        } catch (error: Throwable) {
            error
        }

        assertTrue(observed === thrown)
        assertEquals(priorDeck, fakes.deck.exported)
        assertEquals(priorAutomations, fakes.automations.exported)
    }
}
