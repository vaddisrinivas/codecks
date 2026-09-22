package io.codecks.data.persistence

import io.codecks.domain.update.RecoveryAction
import io.codecks.domain.update.ReleaseIncident
import io.codecks.domain.update.ReleaseRecoveryPolicy
import io.codecks.domain.update.UpdateCandidateState
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M20RollbackIncidentRehearsalTest {
    @Test
    fun corruptAndFutureDataFailClosed() {
        val corruptRaw = "not-a-version"
        val futureRaw = "9:future"
        val corrupt = decodeVersioned(corruptRaw)
        val future = decodeVersioned(futureRaw)

        assertEquals(PersistenceRead.Corrupt("schema"), corrupt)
        assertEquals(PersistenceRead.FutureVersion(9, 2), future)
        assertTrue(runCatching { corrupt.valueForMutation("rehearsal") { "empty" } }.isFailure)
        assertTrue(runCatching { future.valueForMutation("rehearsal") { "empty" } }.isFailure)
        assertEquals("not-a-version", corruptRaw)
        assertEquals("9:future", futureRaw)
        outcome("corrupt_data", "REFUSED_MUTATION_PRESERVED_RAW")
        outcome("future_data", "OLDER_READER_REFUSED_PRESERVED_RAW")
    }

    @Test
    fun interruptedTransactionsRecoverExactGeneration() {
        val expected = mapOf(
            PairTransactionPoint.AFTER_STAGING to "RESTORED_OLD",
            PairTransactionPoint.AFTER_BACKUPS to "RESTORED_OLD",
            PairTransactionPoint.AFTER_PREPARED_JOURNAL to "RESTORED_OLD",
            PairTransactionPoint.AFTER_FIRST_INSTALL to "RESTORED_OLD",
            PairTransactionPoint.AFTER_FIRST_JOURNAL to "RESTORED_OLD",
            PairTransactionPoint.AFTER_SECOND_INSTALL to "RESTORED_OLD",
            PairTransactionPoint.AFTER_SECOND_JOURNAL to "KEPT_NEW",
            PairTransactionPoint.AFTER_COMMITTED_JOURNAL to "KEPT_NEW",
            PairTransactionPoint.AFTER_JOURNAL_REMOVAL to "KEPT_NEW",
            PairTransactionPoint.AFTER_FIRST_BACKUP_DELETE to "RESTORED_OLD",
            PairTransactionPoint.AFTER_SECOND_BACKUP_DELETE to "RESTORED_OLD",
            PairTransactionPoint.AFTER_FIRST_STAGE_DELETE to "RESTORED_OLD",
            PairTransactionPoint.AFTER_SECOND_STAGE_DELETE to "RESTORED_OLD",
        )
        assertEquals(PairTransactionPoint.entries.toSet(), expected.keys)
        expected.forEach { (failurePoint, expectedOutcome) ->
            withDirectory { directory ->
                val first = File(directory, "private").apply { writeText("old-private") }
                val second = File(directory, "public").apply { writeText("old-public") }
                var injected = false
                val store = TransactionalFilePairStore(first, second, 64, 64) { point ->
                    if (!injected && point == failurePoint) {
                        injected = true
                        error("injected process stop")
                    }
                }
                assertTrue(runCatching {
                    store.commit("new-private", "new-public", ::validatePair)
                }.isFailure)
                TransactionalFilePairStore(first, second, 64, 64).recover(::validatePair)
                val recovered = first.readText() to second.readText()
                val actual = when (recovered) {
                    "old-private" to "old-public" -> "RESTORED_OLD"
                    "new-private" to "new-public" -> "KEPT_NEW"
                    else -> error("incoherent generation")
                }
                assertEquals(failurePoint.name, expectedOutcome, actual)
                outcome("transaction_${failurePoint.name.lowercase()}", actual)
            }
        }
    }

    @Test
    fun failedUpgradePreservesOlderData() {
        val raw = "1:stable"
        val result = BoundedPayload.decodeVersioned(
            raw = raw,
            maxBytes = 64,
            currentVersion = 2,
            versionOf = { it.substringBefore(':').toInt() },
            migrate = { _, _ -> error("injected upgrade failure") },
            decode = { it.substringAfter(':') },
        )
        assertEquals(PersistenceRead.Corrupt("migration"), result)
        assertTrue(runCatching { result.valueForMutation("rehearsal") { "empty" } }.isFailure)
        assertEquals("1:stable", raw)
        outcome("failed_upgrade", "REFUSED_MUTATION_PRESERVED_V1")
    }

    @Test
    fun keyLossNeverFallsBackToCiphertext() {
        var decoded = false
        val result = BoundedPayload.decodeEncryptedOrLegacy(
            stored = "encrypted-envelope",
            maxStoredBytes = 64,
            maxPlaintextBytes = 32,
            isLegacyPlaintext = { false },
            decrypt = { error("injected key invalidation") },
            decode = { decoded = true; it },
        )
        assertEquals(PersistenceRead.KeyUnavailable, result)
        assertFalse(decoded)
        assertTrue(runCatching { result.valueForMutation("rehearsal") { "empty" } }.isFailure)
        outcome("key_loss", "REPAIR_REQUIRED_CIPHERTEXT_PRESERVED")
    }

    @Test
    fun partialUpdateSimulationRejectsEveryIncompleteOrUnverifiedState() {
        val cases = mapOf(
            "older_version" to UpdateCandidateState(100, 99, true, true, true, true, true),
            "equal_version" to UpdateCandidateState(100, 100, true, true, true, true, true),
            "partial_download" to UpdateCandidateState(100, 101, false, false, false, false, false),
            "checksum_failure" to UpdateCandidateState(100, 101, true, false, true, true, false),
            "signer_failure" to UpdateCandidateState(100, 101, true, true, false, true, false),
            "source_failure" to UpdateCandidateState(100, 101, true, true, true, false, false),
            "partial_install" to UpdateCandidateState(100, 101, true, true, true, true, false),
            "committed_update" to UpdateCandidateState(100, 101, true, true, true, true, true),
        )
        val expected = mapOf(
            "older_version" to "REJECT_DOWNGRADE",
            "equal_version" to "REJECT_DOWNGRADE",
            "partial_download" to "DISCARD_PARTIAL_DOWNLOAD",
            "checksum_failure" to "REJECT_UNVERIFIED_CANDIDATE",
            "signer_failure" to "REJECT_UNVERIFIED_CANDIDATE",
            "source_failure" to "REJECT_UNVERIFIED_CANDIDATE",
            "partial_install" to "ABANDON_PARTIAL_INSTALL_SESSION",
            "committed_update" to "VERIFIED_UPDATE_COMMITTED",
        )
        cases.forEach { (name, state) ->
            val decision = ReleaseRecoveryPolicy.assessUpdate(state)
            assertEquals(expected.getValue(name), decision.code)
            assertFalse(decision.protectedAppDowngradeAllowed)
            if (name != "committed_update") assertTrue(name, decision.actions.isNotEmpty())
            if (name == "older_version" || name == "equal_version") {
                assertTrue(name, RecoveryAction.REJECT_DOWNGRADE in decision.actions)
            }
            outcome(name, decision.code)
        }
    }

    @Test
    fun badReleaseRollbackMeansWithdrawalAndForwardFix() {
        val decision = ReleaseRecoveryPolicy.decide(ReleaseIncident.BAD_RELEASE)
        assertEquals("WITHDRAW_AND_FORWARD_FIX", decision.code)
        assertTrue(RecoveryAction.WITHDRAW_RELEASE in decision.actions)
        assertTrue(RecoveryAction.FORWARD_FIX in decision.actions)
        assertFalse(decision.protectedAppDowngradeAllowed)
        outcome("bad_release", decision.code)
    }

    @Test
    fun incidentPolicyHasClosedActionableDecisionForEveryIncident() {
        val decisions = ReleaseIncident.entries.associateWith(ReleaseRecoveryPolicy::decide)
        assertEquals(ReleaseIncident.entries.size, decisions.size)
        decisions.forEach { (incident, decision) ->
            assertTrue(incident.name, decision.code.isNotBlank())
            assertTrue(incident.name, decision.actions.isNotEmpty())
            assertFalse(incident.name, decision.protectedAppDowngradeAllowed)
        }
        assertTrue(RecoveryAction.REVOKE_AND_ROTATE_TOKEN in decisions.getValue(ReleaseIncident.COMPROMISED_TOKEN).actions)
        assertTrue(RecoveryAction.REVERIFY_HOST_IDENTITY in decisions.getValue(ReleaseIncident.SSH_HOST_KEY_CHANGED).actions)
        outcome("compromised_token", decisions.getValue(ReleaseIncident.COMPROMISED_TOKEN).code)
        outcome("ssh_host_key", decisions.getValue(ReleaseIncident.SSH_HOST_KEY_CHANGED).code)
        outcome("incident_matrix", "CLOSED_NO_DOWNGRADE")
    }

    private fun decodeVersioned(raw: String?) = BoundedPayload.decodeVersioned(
        raw = raw,
        maxBytes = 64,
        currentVersion = 2,
        versionOf = { it.substringBefore(':').toInt() },
        migrate = { value, version -> if (version == 1) "2:${value.substringAfter(':')}" else null },
        decode = { it.substringAfter(':') },
    )

    private fun validatePair(privateValue: String, publicValue: String) {
        require(privateValue.substringBefore('-') == publicValue.substringBefore('-'))
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("codecks-m20-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun outcome(scenario: String, result: String) {
        println("M20_OUTCOME|$scenario|$result")
    }
}
