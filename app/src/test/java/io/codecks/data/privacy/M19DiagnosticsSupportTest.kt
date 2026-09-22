package io.codecks.data.privacy

import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticEvent
import io.codecks.domain.privacy.DiagnosticEventCode
import io.codecks.domain.privacy.DiagnosticResultCode
import io.codecks.domain.privacy.SupportActionHealth
import io.codecks.domain.privacy.SupportBatteryState
import io.codecks.domain.privacy.SupportBundleHealth
import io.codecks.domain.privacy.SupportBundleManifest
import io.codecks.domain.privacy.SupportBundleRuntime
import io.codecks.domain.privacy.SupportBundleSettings
import io.codecks.domain.privacy.SupportBundleSnapshot
import io.codecks.domain.privacy.SupportConnectionHealth
import io.codecks.domain.privacy.SupportHidHealth
import io.codecks.domain.privacy.SupportIntervalBucket
import io.codecks.domain.privacy.SupportPermissionState
import io.codecks.domain.privacy.SupportSpeedBucket
import io.codecks.ui.connection.SupportFailureScenario
import io.codecks.ui.connection.ConnectionSupportCode
import io.codecks.ui.connection.M06_ACTIONABLE_SUPPORT_CODES
import io.codecks.ui.connection.diagnoseInjectedSupportFailure
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M19DiagnosticsSupportTest {
    @Test
    fun productionSupportCodesComeOnlyFromCanonicalTypedRegistry() {
        val productionRoot = File("src/main/java")
        val canonicalRegistry = File(productionRoot, "io/codecks/ui/connection/UnifiedConnectionPresentation.kt")
            .canonicalFile
        val offenders = productionRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.canonicalFile != canonicalRegistry }
            .filter { it.readText().contains(Regex("\\\"[^\\\"\\n]*CX-")) }
            .map { it.relativeTo(productionRoot).path }
            .toList()

        assertTrue("raw production support-code literals outside canonical registry: $offenders", offenders.isEmpty())
    }

    @Test
    fun closedSchemaAcceptsOnlyKnownBoundedTypedPayload() {
        val entries = entries(snapshot())
        SupportBundleSchemaValidator.validate(entries)

        val unknownEntry = entries + ("raw.log" to "secret")
        assertRejected(unknownEntry)
        assertRejected(entries.mutate("health.json") { put("hostname", "private.local") })
        assertRejected(entries.mutate("runtime.json") { put("batterySaver", 1) })
        assertRejected(entries.mutate("runtime.json") { put("batterySaver", "PRIVATE_CONTENT_CANARY") })
        assertRejected(entries.mutateArray("events.json", "events", 201) { validEvent() })
        assertRejected(entries.mutateArray("receipts.json", "receipts", 21) { validReceipt() })
    }

    @Test
    fun bundleIsBoundedAndContainsNoContentBearingFieldsOrCanaries() {
        val archive = SupportBundleBuilder.build(snapshot())
        val unpacked = unzip(archive)
        val text = unpacked.values.joinToString("\n")

        assertTrue(archive.size <= SupportBundleBuilder.MAX_ARCHIVE_BYTES)
        assertEquals(6, unpacked.size)
        FORBIDDEN.forEach { forbidden -> assertFalse("leaked $forbidden", text.contains(forbidden, ignoreCase = true)) }
    }

    @Test
    fun receiptsAreDerivedFromTerminalTypedEventsAndBounded() {
        val events = (0 until 40).map { index ->
            DiagnosticEvent(
                component = DiagnosticComponent.CLIPBOARD,
                event = if (index % 2 == 0) DiagnosticEventCode.ATTEMPT_FINISHED else DiagnosticEventCode.STATE_CHANGED,
                result = DiagnosticResultCode.FAILED,
                attempt = index,
                durationMs = index.toLong(),
                timestampEpochMs = index.toLong(),
            )
        }
        val receipts = events.toSupportReceipts()

        assertEquals(20, receipts.size)
        assertTrue(receipts.all { it.component == DiagnosticComponent.CLIPBOARD })
        val exported = unzip(SupportBundleBuilder.build(snapshot())).getValue("receipts.json")
        assertFalse(exported.contains("supportCode"))
    }

    @Test
    fun receiptsOmitUncapturedSupportCodesAndRejectInjectedCodes() {
        val docs = File("../docs/support/TROUBLESHOOTING.md").readText()
        val documentedRepairs = docs.lineSequence()
            .filter { it.startsWith("| `CX-") }
            .associate { row ->
                val cells = row.split('|').map(String::trim)
                cells[1].removeSurrounding("`") to cells[3]
            }
        val receipts = DiagnosticComponent.entries.flatMap { component ->
            DiagnosticResultCode.entries.flatMap { result ->
                listOf(DiagnosticEvent(
                    component = component,
                    event = DiagnosticEventCode.ATTEMPT_FINISHED,
                    result = result,
                    attempt = 1,
                    durationMs = 1,
                    timestampEpochMs = 1,
                )).toSupportReceipts()
            }
        }
        assertTrue(receipts.isNotEmpty())
        assertTrue(M06_ACTIONABLE_SUPPORT_CODES.all { documentedRepairs[it].orEmpty().isNotBlank() })
        val exported = unzip(SupportBundleBuilder.build(snapshot())).values.joinToString("\n")
        assertFalse(exported.contains("supportCode"))
        assertFalse(exported.contains("CX-"))
        val entries = entries(snapshot())
        assertRejected(entries.mutate("receipts.json") {
            getJSONArray("receipts").getJSONObject(0).put("supportCode", ConnectionSupportCode.ClipboardFailed.value)
        })
    }

    @Test
    fun everyInjectedFailureHasActionableM06DiagnosisAndPublicDocumentation() {
        val docs = File("../docs/support/TROUBLESHOOTING.md").readText()
        assertEquals(ConnectionSupportCode.entries.map { it.value }.toSet(), M06_ACTIONABLE_SUPPORT_CODES)
        SupportFailureScenario.entries.forEach { scenario ->
            val diagnosis = diagnoseInjectedSupportFailure(scenario)
            assertTrue("missing repair for $scenario", diagnosis.repairs.isNotEmpty())
            assertTrue(diagnosis.supportCode.value.matches(Regex("^CX-(HID|SSH|HLP|CLP)-[A-Z0-9-]{2,15}$")))
            assertTrue("undocumented ${diagnosis.supportCode.value}", docs.contains("`${diagnosis.supportCode.value}`"))
            assertFalse(diagnosis.detail.contains("private.local"))
        }
        M06_ACTIONABLE_SUPPORT_CODES.forEach { code ->
            assertTrue("undocumented stable support code $code", docs.contains("`$code`"))
        }
    }

    @Test
    fun releaseSettingsExposeRedactedSupportExport() {
        val composition = File("src/main/java/io/codecks/AppCompositionRoot.kt").readText()
        val settings = File("src/main/java/io/codecks/ui/settings/SettingsScreen.kt").readText()
        assertTrue(composition.contains("debugBundleEnabled = true"))
        assertTrue(settings.contains("Create redacted support bundle"))
        assertTrue(settings.contains("bounded operation receipts"))
    }

    private fun snapshot(): SupportBundleSnapshot {
        val events = listOf(
            DiagnosticEvent(
                component = DiagnosticComponent.CONNECTION,
                event = DiagnosticEventCode.ATTEMPT_FINISHED,
                result = DiagnosticResultCode.RETRYABLE,
                attempt = 2,
                durationMs = 200,
                timestampEpochMs = 1_000,
            ),
        )
        return SupportBundleSnapshot(
            manifest = SupportBundleManifest(appVersionCode = 39, debugBuild = false, createdAtEpochMs = 1_000),
            health = SupportBundleHealth(
                connection = SupportConnectionHealth.CONFIGURED,
                sshKeyPresent = true,
                pinnedIdentityPresent = true,
                hid = SupportHidHealth.READY,
                knownHostCount = 1,
                visibleActionCount = 8,
                catalogActionCount = 20,
                action = SupportActionHealth.FAILED,
                activityCount = 10,
                activityFailureCount = 1,
            ),
            events = events,
            receipts = events.toSupportReceipts(),
            runtime = SupportBundleRuntime(
                bluetoothPermission = SupportPermissionState.GRANTED,
                notificationPermission = SupportPermissionState.MISSING,
                batterySaver = SupportBatteryState.ACTIVE,
                backgroundRestricted = true,
                batteryOptimizationExempt = false,
            ),
            settings = SupportBundleSettings(
                pointerSpeed = SupportSpeedBucket.MEDIUM,
                scrollRailEnabled = true,
                hapticsEnabled = true,
                pointerTraceEnabled = false,
                clipboardEnabled = true,
                clipboardInterval = SupportIntervalBucket.MEDIUM,
                dynamicDeckEnabled = false,
                featureOverrideCount = 0,
                labsEnabled = false,
            ),
        )
    }

    private fun entries(snapshot: SupportBundleSnapshot): Map<String, String> = unzip(SupportBundleBuilder.build(snapshot))

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun Map<String, String>.mutate(name: String, change: JSONObject.() -> Unit): Map<String, String> =
        toMutableMap().apply { put(name, JSONObject(getValue(name)).apply(change).toString()) }

    private fun Map<String, String>.mutateArray(
        name: String,
        arrayName: String,
        count: Int,
        value: () -> JSONObject,
    ): Map<String, String> = mutate(name) {
        put(arrayName, JSONArray().apply { repeat(count) { put(value()) } })
    }

    private fun assertRejected(entries: Map<String, String>) {
        assertTrue(runCatching { SupportBundleSchemaValidator.validate(entries) }.isFailure)
    }

    private fun validEvent() = JSONObject()
        .put("component", "app")
        .put("event", "state_changed")
        .put("result", "failed")
        .put("attempt", 1)
        .put("durationMs", 1)
        .put("timestampEpochMs", 1)

    private fun validReceipt() = JSONObject()
        .put("component", "clipboard")
        .put("result", "failed")
        .put("attempt", 1)
        .put("durationMs", 1)
        .put("timestampEpochMs", 1)

    private companion object {
        val FORBIDDEN = setOf(
            "PRIVATE_CONTENT_CANARY", "password", "username", "hostname", "fingerprint", "clipboardText",
            "prompt", "response", "token", "accountId", "purchaseId", "deviceSerial", "stdout", "stderr", "command",
        )
    }
}
