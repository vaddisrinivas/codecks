package io.codecks.backend.contracts

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendArchitectureTest {
    @Test
    fun `backend remains pure and has no deploy credential network or Android dependencies`() {
        val root = Path.of(System.getProperty("user.dir"))
        val sourceRoot = root.resolve("src/main/kotlin")
        val sources = Files.walk(sourceRoot).use { paths ->
            paths.filter { it.extension == "kt" }.toList()
        }
        val forbidden = listOf(
            "import android.",
            "import androidx.",
            "com.google.cloud",
            "ktor-client",
            "okhttp",
            "retrofit",
            "System.getenv",
            "GOOGLE_APPLICATION_CREDENTIALS",
            "println(",
            "printStackTrace(",
            "java.util.logging",
            "org.slf4j",
        )
        sources.forEach { source ->
            val text = source.readText()
            forbidden.forEach { marker -> assertFalse(marker in text, "$source contains $marker") }
        }
    }

    @Test
    fun `domain surfaces expose hashes never raw purchase token values`() {
        val root = Path.of(System.getProperty("user.dir"))
        val sourceRoot = root.resolve("src/main/kotlin")
        val combined = Files.walk(sourceRoot).use { paths ->
            paths.filter { it.extension == "kt" }.map { it.readText() }.toList().joinToString("\n")
        }
        assertTrue("PurchaseTokenHash" in combined)
        assertFalse(Regex("\\b(rawToken|purchaseToken):\\s*String\\b").containsMatchIn(combined))
    }

    @Test
    fun `security identifiers and evidence are diagnostics safe`() {
        val account = AccountId.fromVerifiedGoogleSubject(subject()) { "account-00000001" }
        val binding = IntegrityBinding(account, op("redact01"), hash('a'), 100L)
        val proof = evidence("redact02", binding)

        assertFalse(hash('a').toString().contains("a".repeat(64)))
        assertFalse(token('b').toString().contains("b".repeat(64)))
        assertFalse(proof.toString().contains("redact02"))
    }
}
