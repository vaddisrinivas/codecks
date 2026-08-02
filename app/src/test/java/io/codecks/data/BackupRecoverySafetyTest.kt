package io.codecks.data

import io.codecks.domain.backup.RestorePlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
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
    fun restorePreviewPropagatesCancellation() = runTest {
        val fakes = BackupFakes()
        val cancellation = CancellationException("preview cancelled")
        fakes.deck.throwOnExport = cancellation

        val observed = runCatching {
            fakes.repository().createRestorePlan(legacyBackup("{}", "{}"))
        }.exceptionOrNull()

        assertTrue(observed === cancellation)
    }

    @Test
    fun restorePreviewPropagatesFatalError() = runTest {
        val fakes = BackupFakes()
        val fatal = LinkageError("preview fatal")
        fakes.deck.throwOnExport = fatal

        val observed = try {
            fakes.repository().createRestorePlan(legacyBackup("{}", "{}"))
            null
        } catch (error: Throwable) {
            error
        }

        assertTrue(observed === fatal)
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
    fun boundedInputRejectsBeforeReadingAnUnboundedDocument() {
        val oversized = ByteArray(MAX_BACKUP_INPUT_BYTES + 1)

        val failure = runCatching {
            ByteArrayInputStream(oversized).readCodecksBackupBounded()
        }.exceptionOrNull()

        assertTrue(failure is BackupInputTooLargeException)
    }

    @Test
    fun recoveryWriteAndReadShareOneExactBound() {
        assertEquals(
            MAX_BACKUP_RECOVERY_BYTES,
            requireBoundedRecoveryPayload(ByteArray(MAX_BACKUP_RECOVERY_BYTES)).size,
        )
        assertTrue(
            runCatching {
                requireBoundedRecoveryPayload(ByteArray(MAX_BACKUP_RECOVERY_BYTES + 1))
            }.isFailure,
        )
    }

    @Test
    fun failedRecoveryRestoresThePreRecoveryStateAndKeepsSnapshot() = runTest {
        val fakes = BackupFakes()
        val currentDeck = fakes.deck.exported
        val currentAutomations = fakes.automations.exported
        val store = InMemoryBackupRecoveryStore()
        val recoveryId = store.save(
            mapOf("deck" to "{\"prior\":1}", "automations" to "{\"prior\":2}"),
        )
        fakes.automations.failNextImport = true
        val repository = fakes.repository(recoveryStore = store)

        assertTrue(repository.recoverPending(recoveryId).isFailure)
        assertEquals(currentDeck, fakes.deck.exported)
        assertEquals(currentAutomations, fakes.automations.exported)
        assertTrue(store.contains(recoveryId))
    }

    @Test
    fun corruptPendingRecoveryIsTypedAndCanBeQuarantined() {
        val store = object : BackupRecoveryStore {
            var pending = true
            override fun save(sections: Map<String, String>) = "unused"
            override fun clear(recoveryId: String) { pending = false }
            override fun contains(recoveryId: String) = pending
            override fun load(recoveryId: String): Map<String, String>? = null
            override fun pendingIds() = if (pending) listOf(RECOVERY_ID) else emptyList()
            override fun quarantine(recoveryId: String) { pending = false }
        }
        val repository = BackupFakes().repository(recoveryStore = store)

        assertEquals(PendingBackupRecovery.Corrupt(RECOVERY_ID), repository.pendingRecovery())
        repository.quarantineCorruptRecovery(RECOVERY_ID).getOrThrow()
        assertNull(repository.pendingRecovery())
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
        val recoveryBranch = source
            .substringAfter(
                "else if (outcome is io.codecks.domain.backup.BackupRestoreResult.RecoveryRequired)",
            )
            .substringBefore("}")

        assertTrue(recoveryBranch.contains("pendingBackupRecovery = backupRepository.pendingRecovery()"))
        assertTrue(recoveryBranch.contains("pendingRestorePayload = null"))
        assertTrue(recoveryBranch.contains("pendingRestorePlan = null"))
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

    private companion object {
        const val RECOVERY_ID = "00000000-0000-0000-0000-000000000001"
    }
}
