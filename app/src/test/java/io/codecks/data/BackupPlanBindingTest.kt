package io.codecks.data

import io.codecks.domain.backup.RestorePlan
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPlanBindingTest {
    @Test
    fun changedBackupBytesInvalidateConfirmationBeforeMutation() = runTest {
        val fakes = BackupFakes()
        val original = legacyBackup("{\"value\":1}", "{\"value\":1}")
        val changed = legacyBackup("{\"value\":2}", "{\"value\":1}")
        val plan = fakes.repository().createRestorePlan(original).getOrThrow() as RestorePlan.Ready

        val result = fakes.repository().restoreConfirmed(plan.planId, changed)

        assertTrue(result.isFailure)
        assertEquals(0, fakes.deck.importCalls)
        assertEquals(0, fakes.automations.importCalls)
    }

    @Test
    fun changedCurrentStateInvalidatesConfirmationBeforeMutation() = runTest {
        val fakes = BackupFakes()
        val backup = legacyBackup("{\"value\":1}", "{\"value\":1}")
        val plan = fakes.repository().createRestorePlan(backup).getOrThrow() as RestorePlan.Ready
        fakes.deck.exported = "{\"changedAfterPreview\":true}"

        val result = fakes.repository().restoreConfirmed(plan.planId, backup)

        assertTrue(result.isFailure)
        assertEquals(0, fakes.deck.importCalls)
        assertEquals(0, fakes.automations.importCalls)
    }

    @Test
    fun exactPlanBytesAndStateCanConfirm() = runTest {
        val fakes = BackupFakes()
        val backup = legacyBackup("{\"value\":1}", "{\"value\":2}")
        val plan = fakes.repository().createRestorePlan(backup).getOrThrow() as RestorePlan.Ready

        val result = fakes.repository().restoreConfirmed(plan.planId, backup)

        assertTrue(result.isSuccess)
        assertEquals(1, fakes.deck.importCalls)
        assertEquals(1, fakes.automations.importCalls)
    }
}
