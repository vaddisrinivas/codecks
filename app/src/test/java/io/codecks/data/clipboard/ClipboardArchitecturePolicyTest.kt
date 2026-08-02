package io.codecks.data.clipboard

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class ClipboardArchitecturePolicyTest {
    @Test
    fun clipboardPackagesDoNotUseWorkManagerOrAcquireWakeLocks() {
        val clipboardSources = listOf(
            File("src/main/java/io/codecks/data/clipboard"),
            File("src/main/java/io/codecks/domain/clipboard"),
            File("src/main/java/io/codecks/ui/clipboard"),
        ).flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }.joinToString("\n") { it.readText() }

        assertFalse(clipboardSources.contains("androidx.work"))
        assertFalse(clipboardSources.contains("WorkManager"))
        assertFalse(clipboardSources.contains("WakeLock"))
        assertFalse(clipboardSources.contains("newWakeLock"))
    }
}
