package io.codecks.data.privacy

import io.codecks.domain.privacy.SupportBundleSnapshot
import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.SupportOperationReceipt
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

object SupportBundleBuilder {
    const val MAX_ARCHIVE_BYTES = 256 * 1024
    const val MAX_RECEIPTS = 20
    private val entryNames = listOf(
        "manifest.json",
        "health.json",
        "events.json",
        "receipts.json",
        "runtime.json",
        "settings.json",
    )

    fun build(snapshot: SupportBundleSnapshot): ByteArray {
        val entries = linkedMapOf(
            "manifest.json" to manifestJson(snapshot),
            "health.json" to healthJson(snapshot),
            "events.json" to DiagnosticEventCodec.encode(snapshot.events),
            "receipts.json" to receiptsJson(snapshot),
            "runtime.json" to runtimeJson(snapshot),
            "settings.json" to settingsJson(snapshot),
        )
        check(entries.keys.toList() == entryNames)
        SupportBundleSchemaValidator.validate(entries)
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, value) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                    zip.write(value.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }.toByteArray().also { require(it.size <= MAX_ARCHIVE_BYTES) { "Support bundle exceeds size bound" } }
    }

    private fun manifestJson(snapshot: SupportBundleSnapshot): String =
        snapshot.manifest.let {
            JSONObject()
                .put("schemaVersion", it.schemaVersion)
                .put("appVersionCode", it.appVersionCode)
                .put("debugBuild", it.debugBuild)
                .put("createdAtEpochMs", it.createdAtEpochMs)
                .toString()
        }

    private fun healthJson(snapshot: SupportBundleSnapshot): String =
        snapshot.health.let {
            JSONObject()
                .put("connection", it.connection.name.lowercase())
                .put("sshKeyPresent", it.sshKeyPresent)
                .put("pinnedIdentityPresent", it.pinnedIdentityPresent)
                .put("hid", it.hid.name.lowercase())
                .put("knownHostCount", it.knownHostCount.coerceAtLeast(0))
                .put("visibleActionCount", it.visibleActionCount.coerceAtLeast(0))
                .put("catalogActionCount", it.catalogActionCount.coerceAtLeast(0))
                .put("action", it.action.name.lowercase())
                .put("activityCount", it.activityCount.coerceAtLeast(0))
                .put("activityFailureCount", it.activityFailureCount.coerceAtLeast(0))
                .toString()
        }

    private fun settingsJson(snapshot: SupportBundleSnapshot): String =
        snapshot.settings.let {
            JSONObject()
                .put("pointerSpeed", it.pointerSpeed.name.lowercase())
                .put("scrollRailEnabled", it.scrollRailEnabled)
                .put("hapticsEnabled", it.hapticsEnabled)
                .put("pointerTraceEnabled", it.pointerTraceEnabled)
                .put("clipboardEnabled", it.clipboardEnabled)
                .put("clipboardInterval", it.clipboardInterval.name.lowercase())
                .put("dynamicDeckEnabled", it.dynamicDeckEnabled)
                .put("featureOverrideCount", it.featureOverrideCount.coerceAtLeast(0))
                .put("labsEnabled", it.labsEnabled)
                .toString()
        }

    private fun runtimeJson(snapshot: SupportBundleSnapshot): String =
        snapshot.runtime.let {
            JSONObject()
                .put("bluetoothPermission", it.bluetoothPermission.name.lowercase())
                .put("notificationPermission", it.notificationPermission.name.lowercase())
                .put("batterySaver", it.batterySaver.name.lowercase())
                .put("backgroundRestricted", it.backgroundRestricted)
                .put("batteryOptimizationExempt", it.batteryOptimizationExempt)
                .toString()
        }

    private fun receiptsJson(snapshot: SupportBundleSnapshot): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put(
                "receipts",
                JSONArray().apply {
                    snapshot.receipts.takeLast(MAX_RECEIPTS).forEach { receipt ->
                        put(
                            JSONObject()
                                .put("component", receipt.component.persistedCode)
                                .put("result", receipt.result.persistedCode)
                                .put("attempt", receipt.attempt)
                                .put("durationMs", receipt.durationMs)
                                .put("timestampEpochMs", receipt.timestampEpochMs),
                        )
                    }
                },
            )
            .toString()
}

internal fun List<io.codecks.domain.privacy.DiagnosticEvent>.toSupportReceipts(): List<SupportOperationReceipt> =
    asSequence()
        .filter { it.event in setOf(io.codecks.domain.privacy.DiagnosticEventCode.ATTEMPT_FINISHED, io.codecks.domain.privacy.DiagnosticEventCode.RECEIPT_RECORDED) }
        .toList()
        .takeLast(SupportBundleBuilder.MAX_RECEIPTS)
        .asSequence()
        .map { event ->
            SupportOperationReceipt(
                component = event.component,
                result = event.result,
                attempt = event.attempt,
                durationMs = event.durationMs,
                timestampEpochMs = event.timestampEpochMs,
            )
        }
        .toList()
