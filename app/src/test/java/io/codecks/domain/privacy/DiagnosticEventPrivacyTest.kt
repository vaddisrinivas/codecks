package io.codecks.domain.privacy

import io.codecks.data.privacy.DiagnosticEventStore
import io.codecks.data.privacy.FakeDiagnosticEventBackend
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventPrivacyTest {
    @Test
    fun publicEventSchemaHasNoRawOrGeneralPurposeFields() {
        assertEquals(
            setOf(
                "component",
                "event",
                "result",
                "attempt",
                "durationMs",
                "timestampEpochMs",
            ),
            DiagnosticEvent::class.java.declaredFields
                .filterNot { it.isSynthetic || it.name.startsWith("$") }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun prohibitedCanariesCannotSurviveExport() {
        val canaries = linkedMapOf(
            "exceptionMessage" to "EXCEPTION_MESSAGE_CANARY",
            "stdout" to "STDOUT_CANARY",
            "stderr" to "STDERR_CANARY",
            "command" to "COMMAND_CANARY",
            "path" to "/Users/example/.ssh/private_key",
            "host" to "private-mac-canary.local",
            "identifier" to "DEVICE_IDENTIFIER_CANARY",
            "prompt" to "PROMPT_CANARY",
            "clipboard" to "CLIPBOARD_VALUE_CANARY",
        )
        val rawEvent = JSONObject()
            .put("component", canaries.getValue("host"))
            .put("event", canaries.getValue("command"))
            .put("result", canaries.getValue("identifier"))
            .put("attempt", 1)
            .put("durationMs", 2)
            .put("timestampEpochMs", 100)
        canaries.forEach { (field, canary) -> rawEvent.put(field, canary) }
        val rawJournal = JSONObject()
            .put("schemaVersion", 1)
            .put("events", org.json.JSONArray().put(rawEvent))
            .toString()
        val store = DiagnosticEventStore(FakeDiagnosticEventBackend(rawJournal)) { 200L }

        val exported = store.exportJson()

        canaries.values.forEach { canary ->
            assertFalse("Export leaked $canary", exported.contains(canary))
        }
        assertTrue(exported.contains("\"unknown\""))
        writeEvidence(exported, canaries.keys)
    }

    private fun writeEvidence(exported: String, canaryClasses: Set<String>) {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val evidenceDirectory = requireNotNull(moduleDirectory.parentFile)
            .resolve("build/ga-evidence/ARC-02")
        evidenceDirectory.mkdirs()
        evidenceDirectory.resolve("sanitized_event_sample.json").writeText(exported)
        evidenceDirectory.resolve("canary_scan.txt").writeText(
            buildString {
                appendLine("canary_classes=${canaryClasses.sorted().joinToString(",")}")
                appendLine("matches=0")
            },
        )
    }
}
