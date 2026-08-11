package io.codecks.data.privacy

import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticEventCode
import io.codecks.domain.privacy.DiagnosticResultCode
import io.codecks.domain.privacy.SupportActionHealth
import io.codecks.domain.privacy.SupportBatteryState
import io.codecks.domain.privacy.SupportConnectionHealth
import io.codecks.domain.privacy.SupportHidHealth
import io.codecks.domain.privacy.SupportIntervalBucket
import io.codecks.domain.privacy.SupportPermissionState
import io.codecks.domain.privacy.SupportSpeedBucket
import org.json.JSONArray
import org.json.JSONObject

/** Closed export schema. Every string is an enum or stable support code; arbitrary text is rejected. */
internal object SupportBundleSchemaValidator {
    private val entryKeys = setOf(
        "manifest.json", "health.json", "events.json", "receipts.json", "runtime.json", "settings.json",
    )

    fun validate(entries: Map<String, String>) {
        require(entries.keys == entryKeys) { "Unknown or missing support entry" }
        entries.forEach { (name, value) ->
            require(value.toByteArray(Charsets.UTF_8).size <= 128 * 1024) { "$name is oversized" }
        }
        exactObject(entries.getValue("manifest.json"), setOf("schemaVersion", "appVersionCode", "debugBuild", "createdAtEpochMs")).also {
            it.strictInt("schemaVersion", 1..1)
            it.strictInt("appVersionCode", 0..Int.MAX_VALUE)
            it.strictBoolean("debugBuild")
            it.strictLong("createdAtEpochMs", 0L..Long.MAX_VALUE)
        }
        exactObject(entries.getValue("health.json"), HEALTH_KEYS).also {
            it.strictEnum("connection", SupportConnectionHealth.entries.map { value -> value.name.lowercase() })
            it.strictBoolean("sshKeyPresent")
            it.strictBoolean("pinnedIdentityPresent")
            it.strictEnum("hid", SupportHidHealth.entries.map { value -> value.name.lowercase() })
            COUNT_KEYS.forEach { key -> it.strictInt(key, 0..100_000) }
            it.strictEnum("action", SupportActionHealth.entries.map { value -> value.name.lowercase() })
        }
        validateEvents(entries.getValue("events.json"))
        validateReceipts(entries.getValue("receipts.json"))
        exactObject(entries.getValue("runtime.json"), RUNTIME_KEYS).also {
            it.strictEnum("bluetoothPermission", SupportPermissionState.entries.map { value -> value.name.lowercase() })
            it.strictEnum("notificationPermission", SupportPermissionState.entries.map { value -> value.name.lowercase() })
            it.strictEnum("batterySaver", SupportBatteryState.entries.map { value -> value.name.lowercase() })
            it.strictBoolean("backgroundRestricted")
            it.strictBoolean("batteryOptimizationExempt")
        }
        exactObject(entries.getValue("settings.json"), SETTINGS_KEYS).also {
            it.strictEnum("pointerSpeed", SupportSpeedBucket.entries.map { value -> value.name.lowercase() })
            it.strictBoolean("scrollRailEnabled")
            it.strictBoolean("hapticsEnabled")
            it.strictBoolean("pointerTraceEnabled")
            it.strictBoolean("clipboardEnabled")
            it.strictEnum("clipboardInterval", SupportIntervalBucket.entries.map { value -> value.name.lowercase() })
            it.strictBoolean("dynamicDeckEnabled")
            it.strictInt("featureOverrideCount", 0..100)
            it.strictBoolean("labsEnabled")
        }
    }

    private fun validateEvents(raw: String) {
        val root = exactObject(raw, setOf("schemaVersion", "events"))
        root.strictInt("schemaVersion", 1..1)
        val events = root.get("events") as? JSONArray ?: error("events must be an array")
        require(events.length() <= DiagnosticEventStore.MAX_ENTRIES) { "Too many events" }
        repeat(events.length()) { index ->
            val event = events.optJSONObject(index) ?: error("Event must be an object")
            require(event.keys().asSequence().toSet() == EVENT_KEYS) { "Unknown event key" }
            event.strictEnum("component", DiagnosticComponent.entries.map { it.persistedCode })
            event.strictEnum("event", DiagnosticEventCode.entries.map { it.persistedCode })
            event.strictEnum("result", DiagnosticResultCode.entries.map { it.persistedCode })
            event.strictInt("attempt", 0..1_000)
            event.strictLong("durationMs", 0L..86_400_000L)
            event.strictLong("timestampEpochMs", 0L..Long.MAX_VALUE)
        }
    }

    private fun validateReceipts(raw: String) {
        val root = exactObject(raw, setOf("schemaVersion", "receipts"))
        root.strictInt("schemaVersion", 1..1)
        val receipts = root.get("receipts") as? JSONArray ?: error("receipts must be an array")
        require(receipts.length() <= SupportBundleBuilder.MAX_RECEIPTS) { "Too many receipts" }
        repeat(receipts.length()) { index ->
            val receipt = receipts.optJSONObject(index) ?: error("Receipt must be an object")
            require(receipt.keys().asSequence().toSet() == RECEIPT_KEYS) { "Unknown or missing receipt key" }
            receipt.strictEnum("component", DiagnosticComponent.entries.map { it.persistedCode })
            receipt.strictEnum("result", DiagnosticResultCode.entries.map { it.persistedCode })
            receipt.strictInt("attempt", 0..1_000)
            receipt.strictLong("durationMs", 0L..86_400_000L)
            receipt.strictLong("timestampEpochMs", 0L..Long.MAX_VALUE)
        }
    }

    private fun exactObject(raw: String, keys: Set<String>): JSONObject = JSONObject(raw).also {
        require(it.keys().asSequence().toSet() == keys) { "Unknown or missing support field" }
    }

    private fun JSONObject.strictBoolean(key: String) {
        require(get(key) is Boolean) { "$key must be boolean" }
    }

    private fun JSONObject.strictEnum(key: String, allowed: List<String>) {
        require((get(key) as? String) in allowed) { "$key is not an approved value" }
    }

    private fun JSONObject.strictInt(key: String, range: IntRange) {
        val number = get(key) as? Number ?: error("$key must be integer")
        val value = number.toInt()
        require(number.toDouble() == value.toDouble() && value in range) { "$key is outside its bound" }
    }

    private fun JSONObject.strictLong(key: String, range: LongRange) {
        val number = get(key) as? Number ?: error("$key must be integer")
        val value = number.toLong()
        require(number.toDouble() == value.toDouble() && value in range) { "$key is outside its bound" }
    }

    private val HEALTH_KEYS = setOf(
        "connection", "sshKeyPresent", "pinnedIdentityPresent", "hid", "knownHostCount",
        "visibleActionCount", "catalogActionCount", "action", "activityCount", "activityFailureCount",
    )
    private val COUNT_KEYS = setOf("knownHostCount", "visibleActionCount", "catalogActionCount", "activityCount", "activityFailureCount")
    private val EVENT_KEYS = setOf("component", "event", "result", "attempt", "durationMs", "timestampEpochMs")
    private val RECEIPT_KEYS = setOf("component", "result", "attempt", "durationMs", "timestampEpochMs")
    private val RUNTIME_KEYS = setOf(
        "bluetoothPermission", "notificationPermission", "batterySaver", "backgroundRestricted", "batteryOptimizationExempt",
    )
    private val SETTINGS_KEYS = setOf(
        "pointerSpeed", "scrollRailEnabled", "hapticsEnabled", "pointerTraceEnabled", "clipboardEnabled",
        "clipboardInterval", "dynamicDeckEnabled", "featureOverrideCount", "labsEnabled",
    )
}
