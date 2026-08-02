package io.codecks.data

import io.codecks.domain.backup.BackupRestoreResult
import io.codecks.domain.backup.RestorePlan
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMigrationTest {
    @Test
    fun supportedLegacyV1FixtureMigratesAndCommits() = runTest {
        val fakes = BackupFakes()
        val repository = fakes.repository()
        val fixture = resource("backups/legacy-v1.json")
        val plan = repository.createRestorePlan(fixture).getOrThrow() as RestorePlan.Ready

        val outcome = repository.restoreConfirmed(plan.planId, fixture).getOrThrow()

        assertEquals(1, plan.sourceSchemaVersion)
        assertTrue(plan.migrations.isNotEmpty())
        assertTrue(outcome is BackupRestoreResult.Committed)
        assertEquals("{\"schemaVersion\":3,\"items\":[]}", fakes.deck.exported)
        assertEquals("{\"schemaVersion\":3,\"items\":[]}", fakes.automations.exported)
    }

    @Test
    fun supportedCurrentV2ArchiveCommitsWithoutMigration() = runTest {
        val source = BackupFakes().apply {
            deck.exported = "{\"deck\":\"from-v2\"}"
            automations.exported = "{\"rules\":\"from-v2\"}"
        }
        val archive = source.repository().exportArchive().getOrThrow()
        val target = BackupFakes()
        val repository = target.repository()
        val plan = repository.createRestorePlan(archive).getOrThrow() as RestorePlan.Ready

        val outcome = repository.restoreConfirmed(plan.planId, archive).getOrThrow()

        assertEquals(2, plan.sourceSchemaVersion)
        assertTrue(plan.migrations.isEmpty())
        assertTrue(outcome is BackupRestoreResult.Committed)
        assertEquals(source.deck.exported, target.deck.exported)
        assertEquals(source.automations.exported, target.automations.exported)
    }
}
