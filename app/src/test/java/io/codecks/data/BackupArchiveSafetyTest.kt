package io.codecks.data

import io.codecks.domain.backup.BackupRejectionReason
import io.codecks.domain.backup.CompatibilityVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveSafetyTest {
    private val manifest = resource("backups/current-v2-manifest.json")
    private val validContent = "{\"schemaVersion\":3,\"items\":[]}".toByteArray()

    @Test
    fun rejectsTraversalBeforeRepositoryMutation() {
        val fakes = BackupFakes()
        val archive = zip("../deck.json" to validContent)

        assertRejected(fakes, archive, BackupRejectionReason.UnsafePath)
    }

    @Test
    fun rejectsDuplicateEntriesBeforeRepositoryMutation() {
        val original = zip(
            "manifest.json" to manifest,
            "first.json" to validContent,
            "other.json" to validContent,
        )
        val duplicate = replaceAscii(original, "other.json", "first.json")

        assertRejected(BackupFakes(), duplicate, BackupRejectionReason.DuplicateEntry)
    }

    @Test
    fun rejectsExcessiveEntryCountAndUncompressedSize() {
        val manyEntries = (0..BackupArchiveCodec.MAX_ENTRIES).map { index ->
            "entry-$index.json" to byteArrayOf(1)
        }.toTypedArray()
        assertRejected(
            BackupFakes(),
            zip(*manyEntries),
            BackupRejectionReason.ExcessiveEntryCount,
        )
        assertRejected(
            BackupFakes(),
            zip("large.bin" to ByteArray(BackupArchiveCodec.MAX_UNCOMPRESSED_BYTES + 1)),
            BackupRejectionReason.ExcessiveUncompressedSize,
        )
    }

    @Test
    fun rejectsCorruptChecksumMissingAndUnexpectedEntries() {
        val corrupt = zip(
            "manifest.json" to manifest,
            "sections/deck.json" to "changed".toByteArray(),
            "sections/automations.json" to validContent,
        )
        assertRejected(BackupFakes(), corrupt, BackupRejectionReason.CorruptChecksum)

        val missing = zip(
            "manifest.json" to manifest,
            "sections/deck.json" to validContent,
        )
        assertRejected(BackupFakes(), missing, BackupRejectionReason.MissingEntry)

        val unexpected = zip(
            "manifest.json" to manifest,
            "sections/deck.json" to validContent,
            "sections/automations.json" to validContent,
            "extra.json" to validContent,
        )
        assertRejected(BackupFakes(), unexpected, BackupRejectionReason.UnexpectedEntry)
    }

    private fun assertRejected(
        fakes: BackupFakes,
        archive: ByteArray,
        expected: BackupRejectionReason,
    ) {
        val verdict = fakes.repository().compatibilityVerdict(archive)
        assertTrue(verdict is CompatibilityVerdict.Rejected)
        assertEquals(expected, (verdict as CompatibilityVerdict.Rejected).reason)
        assertEquals(0, fakes.deck.importCalls)
        assertEquals(0, fakes.automations.importCalls)
    }

    private fun replaceAscii(bytes: ByteArray, from: String, to: String): ByteArray {
        require(from.length == to.length)
        val result = bytes.copyOf()
        val needle = from.toByteArray()
        val replacement = to.toByteArray()
        for (index in 0..result.size - needle.size) {
            if (needle.indices.all { result[index + it] == needle[it] }) {
                replacement.copyInto(result, index)
            }
        }
        return result
    }
}
