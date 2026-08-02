package io.codecks.data

import io.codecks.domain.backup.BackupManifest
import io.codecks.domain.backup.BackupSectionManifest
import io.codecks.domain.backup.RestorePlan
import io.codecks.domain.backup.RestoreSectionChangeKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDryRunTest {
    @Test
    fun previewReportsChangesMigrationAndWarningsWithoutMutation() = runTest {
        val fakes = BackupFakes()
        val backup = legacyBackup(
            deck = "{\"schemaVersion\":3,\"items\":[{\"id\":\"new\"}]}",
            automations = fakes.automations.exported,
        )

        val plan = fakes.repository().createRestorePlan(backup).getOrThrow()

        assertTrue(plan is RestorePlan.Ready)
        plan as RestorePlan.Ready
        assertTrue(plan.canConfirm)
        assertTrue(plan.migrations.isNotEmpty())
        assertTrue(
            plan.sections.any {
                it.section == "deck" && it.kind == RestoreSectionChangeKind.Replaced
            },
        )
        assertTrue(
            plan.sections.any {
                it.section == "automations" && it.kind == RestoreSectionChangeKind.Unchanged
            },
        )
        assertTrue(plan.warnings.isNotEmpty())
        assertFalse(plan.planId.contains("new"))
        assertNoMutation(fakes)
    }

    @Test
    fun incompatibleBackupProducesBlockedPlanWithoutConfirmOrMutation() = runTest {
        val fakes = BackupFakes()

        val plan = fakes.repository().createRestorePlan("not a backup".toByteArray()).getOrThrow()

        assertTrue(plan is RestorePlan.Blocked)
        assertFalse(plan.canConfirm)
        assertNoMutation(fakes)
    }

    @Test
    fun previewReportsAddedAndRemovedSections() = runTest {
        val fakes = BackupFakes()
        val deck = fakes.deck.exported.toByteArray()
        val added = "{}".toByteArray()
        val archive = BackupArchiveCodec.encode(
            manifest = BackupManifest(
                schemaVersion = 2,
                sourceAppVersion = "test",
                sections = listOf(
                    section("deck", "sections/deck.json", deck),
                    section("new_section", "sections/new.json", added),
                ),
            ),
            entries = linkedMapOf(
                "sections/deck.json" to deck,
                "sections/new.json" to added,
            ),
        )

        val plan = fakes.repository().createRestorePlan(archive).getOrThrow() as RestorePlan.Ready

        assertTrue(
            plan.sections.any {
                it.section == "new_section" && it.kind == RestoreSectionChangeKind.Added
            },
        )
        assertTrue(
            plan.sections.any {
                it.section == "automations" && it.kind == RestoreSectionChangeKind.Removed
            },
        )
        assertNoMutation(fakes)
    }

    private fun assertNoMutation(fakes: BackupFakes) {
        assertTrue(fakes.deck.importCalls == 0)
        assertTrue(fakes.automations.importCalls == 0)
    }
}

private fun section(name: String, path: String, content: ByteArray): BackupSectionManifest =
    BackupSectionManifest(
        name = name,
        path = path,
        sha256 = java.security.MessageDigest.getInstance("SHA-256")
            .digest(content)
            .joinToString("") { "%02x".format(it) },
        uncompressedBytes = content.size.toLong(),
    )

internal fun legacyBackup(deck: String, automations: String): ByteArray =
    org.json.JSONObject()
        .put("schemaVersion", 1)
        .put("product", "Codecks")
        .put("credentialStoresIncluded", false)
        .put("deck", deck)
        .put("automations", automations)
        .toString()
        .toByteArray()
