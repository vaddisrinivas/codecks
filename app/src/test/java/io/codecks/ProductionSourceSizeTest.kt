package io.codecks

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionSourceSizeTest {
    @Test
    fun handwrittenProductionFilesDoNotExceedThousandLines() {
        val sourceRoot = File("src/main/java")
        val oversized = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .map { file -> file.relativeTo(sourceRoot).path to file.readLines().size }
            .filter { (_, lines) -> lines > 1_000 }
            .sortedByDescending { (_, lines) -> lines }
            .toList()

        assertTrue(
            "Hand-written production files above 1,000 lines: $oversized",
            oversized.isEmpty(),
        )
    }
}
