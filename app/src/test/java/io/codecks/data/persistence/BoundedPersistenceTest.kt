package io.codecks.data.persistence

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedPersistenceTest {
    @Test
    fun `bounded version codec migrates old and rejects corrupt future and oversized`() {
        fun decode(raw: String?) = BoundedPayload.decodeVersioned(
            raw = raw,
            maxBytes = 32,
            currentVersion = 2,
            versionOf = { it.substringBefore(':').toInt() },
            migrate = { value, version -> if (version == 1) "2:${value.substringAfter(':')}" else null },
            decode = { it.substringAfter(':') },
        )

        assertEquals(PersistenceRead.Value("old", migratedFrom = 1), decode("1:old"))
        assertEquals(PersistenceRead.Value("now"), decode("2:now"))
        assertTrue(decode("broken") is PersistenceRead.Corrupt)
        assertEquals(PersistenceRead.FutureVersion(3, 2), decode("3:new"))
        assertTrue(decode("2:${"x".repeat(40)}") is PersistenceRead.Corrupt)
    }

    @Test
    fun `keystore loss is distinct and never falls back to ciphertext as plaintext`() {
        var decoded = false
        val result = BoundedPayload.decodeEncryptedOrLegacy(
            stored = "base64-ciphertext",
            maxStoredBytes = 64,
            maxPlaintextBytes = 32,
            isLegacyPlaintext = { it.startsWith("{") },
            decrypt = { error("key permanently invalidated") },
            decode = { decoded = true; it },
        )

        assertEquals(PersistenceRead.KeyUnavailable, result)
        assertTrue(!decoded)
    }

    @Test
    fun `legacy plaintext remains readable without decrypting`() {
        var decryptCalls = 0
        val result = BoundedPayload.decodeEncryptedOrLegacy(
            stored = "{legacy}",
            maxStoredBytes = 64,
            maxPlaintextBytes = 32,
            isLegacyPlaintext = { it.startsWith("{") },
            decrypt = { decryptCalls += 1; "bad" },
            decode = { it.removeSurrounding("{", "}") },
        )

        assertEquals(PersistenceRead.Value("legacy"), result)
        assertEquals(0, decryptCalls)
    }

    @Test
    fun `failed and newer reads cannot be overwritten by mutation`() {
        val blocked = listOf<PersistenceRead<String>>(
            PersistenceRead.KeyUnavailable,
            PersistenceRead.Corrupt("bad"),
            PersistenceRead.FutureVersion(3, 2),
        )
        blocked.forEach { read ->
            assertTrue(runCatching { read.valueForMutation("test") { "empty" } }.isFailure)
        }
        assertEquals("empty", PersistenceRead.Missing.valueForMutation("test") { "empty" })
        assertEquals("saved", PersistenceRead.Value("saved").valueForMutation("test") { "empty" })
    }

    @Test
    fun `atomic file ignores stale staging after process death`() = withDirectory { directory ->
        val target = File(directory, "state.json").apply { writeText("old") }
        File(directory, ".state.json.tmp").writeText("partial")

        assertEquals(PersistenceRead.Value("old"), AtomicBoundedFileStore(target, 32).read())
        assertTrue(!File(directory, ".state.json.tmp").exists())
    }

    @Test
    fun `atomic file restores backup after interrupted replacement`() = withDirectory { directory ->
        val target = File(directory, "state.json")
        File(directory, ".state.json.bak").writeText("safe")

        assertEquals(PersistenceRead.Value("safe"), AtomicBoundedFileStore(target, 32).read())
    }

    @Test
    fun `atomic file rolls back when commit move fails`() = withDirectory { directory ->
        val target = File(directory, "state.json").apply { writeText("old") }
        var moves = 0
        val store = AtomicBoundedFileStore(target, 32) { source, destination ->
            moves += 1
            if (moves == 2) error("simulated crash")
            Files.move(source.toPath(), destination.toPath())
        }

        assertTrue(runCatching { store.write("new") }.isFailure)
        assertEquals("old", target.readText())
    }

    @Test
    fun `atomic file rejects oversized without changing current value`() = withDirectory { directory ->
        val target = File(directory, "state.json").apply { writeText("old") }
        val store = AtomicBoundedFileStore(target, 8)

        assertTrue(runCatching { store.write("x".repeat(9)) }.isFailure)
        assertEquals("old", target.readText())
    }

    @Test
    fun `pair commit rolls both files back when second install fails`() = withDirectory { directory ->
        val first = File(directory, "private").apply { writeText("old-private") }
        val second = File(directory, "public").apply { writeText("old-public") }
        val store = TransactionalFilePairStore(first, second, 64, 64) { point ->
            if (point == PairTransactionPoint.AFTER_FIRST_INSTALL) error("process stopped")
        }

        assertTrue(runCatching { store.commit("new-private", "new-public", ::validatePair) }.isFailure)
        assertEquals("old-private", first.readText())
        assertEquals("old-public", second.readText())
    }

    @Test
    fun `pair recovery restores both files after process death marker`() = withDirectory { directory ->
        val first = File(directory, "private").apply { writeText("partial-new") }
        val second = File(directory, "public").apply { writeText("old-public") }
        File(directory, ".private.pair.bak").writeText("old-private")
        File(directory, ".public.pair.bak").writeText("old-public")
        File(directory, ".private.public.txn").writeText("1|1")

        TransactionalFilePairStore(first, second, 64, 64).recover(::validatePair)

        assertEquals("old-private", first.readText())
        assertEquals("old-public", second.readText())
    }

    @Test
    fun `pair validation failure leaves existing pair untouched`() = withDirectory { directory ->
        val first = File(directory, "private").apply { writeText("old-private") }
        val second = File(directory, "public").apply { writeText("old-public") }
        val store = TransactionalFilePairStore(first, second, 64, 64)

        assertTrue(runCatching {
            store.commit("new-private", "mismatched-public") { _, public -> require(public == "new-public") }
        }.isFailure)
        assertEquals("old-private", first.readText())
        assertEquals("old-public", second.readText())
    }

    @Test
    fun `pair transaction failures always recover an old or new coherent pair`() {
        PairTransactionPoint.entries.forEach { failurePoint ->
            withDirectory { directory ->
                val first = File(directory, "private").apply { writeText("old-private") }
                val second = File(directory, "public").apply { writeText("old-public") }
                var failed = false
                val store = TransactionalFilePairStore(first, second, 64, 64) { point ->
                    if (!failed && point == failurePoint) {
                        failed = true
                        error("crash at $point")
                    }
                }

                assertTrue(runCatching {
                    store.commit("new-private", "new-public", ::validatePair)
                }.isFailure)
                TransactionalFilePairStore(first, second, 64, 64).recover(::validatePair)
                val recovered = first.readText() to second.readText()
                assertTrue(
                    "$failurePoint recovered incoherent pair $recovered",
                    recovered == ("old-private" to "old-public") ||
                        recovered == ("new-private" to "new-public"),
                )
            }
        }
    }

    @Test
    fun `malformed and partial journals restore backups without mixing generations`() = withDirectory { directory ->
        listOf("broken", "version=1\nstate=PREPARED").forEachIndexed { index, journal ->
            val first = File(directory, "private").apply { writeText("new-private") }
            val second = File(directory, "public").apply { writeText("old-public") }
            File(directory, ".private.pair.bak").writeText("old-private")
            File(directory, ".public.pair.bak").writeText("old-public")
            File(directory, ".private.public.txn").writeText(journal)

            TransactionalFilePairStore(first, second, 64, 64).recover(::validatePair)

            assertEquals("journal $index", "old-private", first.readText())
            assertEquals("journal $index", "old-public", second.readText())
        }
    }

    @Test
    fun `malformed journal preserves coherent live pair when backups are absent`() = withDirectory { directory ->
        val first = File(directory, "private").apply { writeText("new-private") }
        val second = File(directory, "public").apply { writeText("new-public") }
        File(directory, ".private.public.txn").writeText("truncated")

        TransactionalFilePairStore(first, second, 64, 64).recover(::validatePair)

        assertEquals("new-private", first.readText())
        assertEquals("new-public", second.readText())
    }

    @Test
    fun `journal checksum corruption restores coherent backup`() = withDirectory { directory ->
        val first = File(directory, "private").apply { writeText("old-private") }
        val second = File(directory, "public").apply { writeText("old-public") }
        val store = TransactionalFilePairStore(first, second, 64, 64) { point ->
            if (point == PairTransactionPoint.AFTER_PREPARED_JOURNAL) error("process stopped")
        }
        assertTrue(runCatching {
            store.commit("new-private", "new-public", ::validatePair)
        }.isFailure)
        val marker = File(directory, ".private.public.txn")
        marker.writeText(marker.readText().replaceAfterLast('=', "0".repeat(64)))

        TransactionalFilePairStore(first, second, 64, 64).recover(::validatePair)

        assertEquals("old-private", first.readText())
        assertEquals("old-public", second.readText())
    }

    @Test
    fun `malformed journal with no coherent pair fails closed and preserves evidence`() = withDirectory { directory ->
        val first = File(directory, "private").apply { writeText("new-private") }
        val second = File(directory, "public").apply { writeText("old-public") }
        val marker = File(directory, ".private.public.txn").apply { writeText("truncated") }

        assertTrue(runCatching {
            TransactionalFilePairStore(first, second, 64, 64).recover(::validatePair)
        }.isFailure)
        assertEquals("new-private", first.readText())
        assertEquals("old-public", second.readText())
        assertTrue(marker.exists())
    }

    @Test
    fun `missing journal with partial backup fails closed and preserves evidence`() = withDirectory { directory ->
        val backup = File(directory, ".private.pair.bak").apply { writeText("old-private") }

        assertTrue(runCatching {
            TransactionalFilePairStore(
                File(directory, "private"),
                File(directory, "public"),
                64,
                64,
            ).recover(::validatePair)
        }.isFailure)
        assertEquals("old-private", backup.readText())
    }

    private fun validatePair(privateKey: String, publicKey: String) {
        require(privateKey.substringBefore('-') == publicKey.substringBefore('-'))
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("codecks-persistence-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
