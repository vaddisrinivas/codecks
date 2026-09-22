package io.codecks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshKeyRotationTransactionTest {
    @Test
    fun `candidate failures never invoke live commit`() {
        CandidateFailure.entries.forEach { failure ->
            var live = "old-encrypted" to "old-public"
            val result = runCatching {
                prepareAndCommitSshKeyPair(
                    generate = {
                        if (failure == CandidateFailure.GENERATION) error("generation")
                        "new-private" to "new-public"
                    },
                    encrypt = {
                        if (failure == CandidateFailure.ENCRYPTION) error("encryption")
                        "encrypted:$it"
                    },
                    validatePlain = { _, _ ->
                        if (failure == CandidateFailure.PLAIN_VALIDATION) error("validation")
                    },
                    validateStored = { _, _ ->
                        if (failure == CandidateFailure.STORED_VALIDATION) error("validation")
                    },
                    commit = { privateKey, publicKey, _ -> live = privateKey to publicKey },
                )
            }

            assertTrue("$failure must fail", result.isFailure)
            assertEquals("$failure touched live pair", "old-encrypted" to "old-public", live)
        }
    }

    @Test
    fun `validated encrypted candidate is committed once`() {
        var commits = 0
        var live = "old-encrypted" to "old-public"

        val publicKey = prepareAndCommitSshKeyPair(
            generate = { "new-private" to " new-public\n" },
            encrypt = { "encrypted:$it" },
            validatePlain = { privateKey, candidatePublic ->
                require(privateKey == "new-private" && candidatePublic.trim() == "new-public")
            },
            validateStored = { privateKey, candidatePublic ->
                require(privateKey == "encrypted:new-private" && candidatePublic.trim() == "new-public")
            },
            commit = { privateKey, candidatePublic, validate ->
                validate(privateKey, candidatePublic)
                commits += 1
                live = privateKey to candidatePublic
            },
        )

        assertEquals("new-public", publicKey)
        assertEquals(1, commits)
        assertEquals("encrypted:new-private" to " new-public\n", live)
    }

    @Test
    fun `legacy cleanup runs only after live pair commit succeeds`() {
        var legacyPresent = true
        val failure = runCatching {
            prepareAndCommitSshKeyPair(
                generate = { "new-private" to "new-public" },
                encrypt = { "encrypted:$it" },
                validatePlain = { _, _ -> },
                validateStored = { _, _ -> },
                commit = { _, _, _ -> error("install failed") },
                afterCommit = { legacyPresent = false },
            )
        }
        assertTrue(failure.isFailure)
        assertTrue(legacyPresent)

        prepareAndCommitSshKeyPair(
            generate = { "new-private" to "new-public" },
            encrypt = { "encrypted:$it" },
            validatePlain = { _, _ -> },
            validateStored = { _, _ -> },
            commit = { _, _, _ -> },
            afterCommit = { legacyPresent = false },
        )
        assertTrue(!legacyPresent)
    }

    private enum class CandidateFailure {
        GENERATION,
        PLAIN_VALIDATION,
        ENCRYPTION,
        STORED_VALIDATION,
    }
}
