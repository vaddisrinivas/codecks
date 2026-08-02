package io.codecks.data.privacy

import io.codecks.domain.privacy.SupportBundleSnapshot
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

object SupportBundleBuilder {
    private val entryNames = listOf("manifest.json", "health.json", "events.json", "settings.json")

    fun build(snapshot: SupportBundleSnapshot): ByteArray {
        val entries = linkedMapOf(
            "manifest.json" to manifestJson(snapshot),
            "health.json" to healthJson(snapshot),
            "events.json" to DiagnosticEventCodec.encode(snapshot.events),
            "settings.json" to settingsJson(snapshot),
        )
        check(entries.keys.toList() == entryNames)
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, value) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                    zip.write(value.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }.toByteArray()
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
}
