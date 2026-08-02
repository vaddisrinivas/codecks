package io.codecks.data.privacy

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportBundleTempFilePolicyTest {
    @Test
    fun cleanupDeletesOnlyOwnedFilesOlderThanTwentyFourHours() {
        val root = createTempDirectory("support-policy-").toFile()
        try {
            val policy = SupportBundleTempFilePolicy(root)
            val now = 2 * SupportBundleTempFilePolicy.MAX_AGE_MILLIS
            val old = policy.write(byteArrayOf(1), 1L).getOrThrow().apply {
                setLastModified(now - SupportBundleTempFilePolicy.MAX_AGE_MILLIS - 1L)
            }
            val fresh = policy.write(byteArrayOf(2), 2L).getOrThrow().apply {
                setLastModified(now)
            }
            val unrelated = File(root, "unrelated.txt").apply { writeText("keep") }

            assertEquals(1, policy.cleanupExpired(now))
            assertFalse(old.exists())
            assertTrue(fresh.exists())
            assertTrue(unrelated.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancelDeletesOwnedCandidateOnly() {
        val root = createTempDirectory("support-cancel-").toFile()
        try {
            val policy = SupportBundleTempFilePolicy(root)
            val owned = policy.write(byteArrayOf(1), 10L).getOrThrow()
            val unrelated = File(root, "other.zip").apply { writeBytes(byteArrayOf(2)) }

            policy.cancel(owned)
            policy.cancel(unrelated)

            assertFalse(owned.exists())
            assertTrue(unrelated.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
