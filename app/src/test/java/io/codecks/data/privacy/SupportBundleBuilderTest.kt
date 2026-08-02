package io.codecks.data.privacy

import io.codecks.domain.privacy.SupportActionHealth
import io.codecks.domain.privacy.SupportBundleHealth
import io.codecks.domain.privacy.SupportBundleManifest
import io.codecks.domain.privacy.SupportBundleSettings
import io.codecks.domain.privacy.SupportBundleSnapshot
import io.codecks.domain.privacy.SupportConnectionHealth
import io.codecks.domain.privacy.SupportHidHealth
import io.codecks.domain.privacy.SupportIntervalBucket
import io.codecks.domain.privacy.SupportSpeedBucket
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SupportBundleBuilderTest {
    @Test
    fun multiDomainCanariesHaveZeroArchiveMatches() {
        val canaries = listOf(
            "HID_DEVICE_ID_CANARY",
            "CLIPBOARD_VALUE_CANARY",
            "AUTOMATION_STDOUT_CANARY",
            "BACKUP_PATH_CANARY",
            "UPDATE_HOST_CANARY",
            "MESSAGE_CANARY",
            "STDERR_CANARY",
            "PROMPT_CANARY",
        )
        val rawEvents = JSONArray().apply {
            canaries.forEachIndexed { index, canary ->
                put(
                    JSONObject()
                        .put("component", canary)
                        .put("event", canary)
                        .put("result", canary)
                        .put("attempt", index)
                        .put("durationMs", 1)
                        .put("timestampEpochMs", 100 + index)
                        .put("message", canary)
                        .put("stdout", canary)
                        .put("stderr", canary)
                        .put("path", canary)
                        .put("prompt", canary)
                        .put("clipboard", canary)
                        .put("host", canary)
                        .put("deviceId", canary),
                )
            }
        }
        val journal = JSONObject()
            .put("schemaVersion", 1)
            .put("events", rawEvents)
            .toString()
        val safeEvents = DiagnosticEventStore(FakeDiagnosticEventBackend(journal)) { 1_000L }.events()

        val archive = SupportBundleBuilder.build(snapshot(safeEvents))
        val entries = unzip(archive)
        val archiveText = entries.values.joinToString("\n")

        assertEquals(
            setOf("manifest.json", "health.json", "events.json", "settings.json"),
            entries.keys,
        )
        canaries.forEach { canary ->
            assertFalse("Support archive leaked $canary", archiveText.contains(canary))
        }
        FORBIDDEN_FIELD_NAMES.forEach { field ->
            assertFalse("Support archive exposed field $field", archiveText.contains("\"$field\""))
        }
    }

    private fun snapshot(events: List<io.codecks.domain.privacy.DiagnosticEvent>) =
        SupportBundleSnapshot(
            manifest = SupportBundleManifest(
                appVersionCode = 31,
                debugBuild = true,
                createdAtEpochMs = 1_000L,
            ),
            health = SupportBundleHealth(
                connection = SupportConnectionHealth.READY,
                sshKeyPresent = true,
                pinnedIdentityPresent = true,
                hid = SupportHidHealth.CONNECTED,
                knownHostCount = 1,
                visibleActionCount = 4,
                catalogActionCount = 12,
                action = SupportActionHealth.SUCCEEDED,
                activityCount = 2,
                activityFailureCount = 0,
            ),
            events = events,
            settings = SupportBundleSettings(
                pointerSpeed = SupportSpeedBucket.MEDIUM,
                scrollRailEnabled = true,
                hapticsEnabled = true,
                pointerTraceEnabled = false,
                clipboardEnabled = true,
                clipboardInterval = SupportIntervalBucket.MEDIUM,
                dynamicDeckEnabled = true,
                featureOverrideCount = 0,
                labsEnabled = false,
            ),
        )

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private companion object {
        val FORBIDDEN_FIELD_NAMES = setOf(
            "message",
            "stdout",
            "stderr",
            "path",
            "prompt",
            "clipboard",
            "host",
            "deviceId",
            "identifier",
            "command",
            "logs",
            "file",
        )
    }
}
