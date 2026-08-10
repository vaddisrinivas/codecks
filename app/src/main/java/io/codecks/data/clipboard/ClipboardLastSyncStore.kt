package io.codecks.data.clipboard

import android.content.Context
import io.codecks.domain.clipboard.ClipboardDirection
import io.codecks.domain.clipboard.ClipboardFailureCode
import io.codecks.domain.clipboard.ClipboardReceipt
import io.codecks.domain.clipboard.ClipboardTerminalResult
import org.json.JSONObject
import io.codecks.data.persistence.BoundedPayload
import io.codecks.data.persistence.PersistenceRead
import io.codecks.data.persistence.valueForMutation

class ClipboardLastSyncStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "clipboard_last_sync",
        Context.MODE_PRIVATE,
    )

    fun read(): ClipboardReceipt? =
        (ClipboardLastSyncCodec.read(preferences.getString(KEY_RECEIPT, null)) as? PersistenceRead.Value)?.value

    fun save(receipt: ClipboardReceipt) {
        ClipboardLastSyncCodec.read(preferences.getString(KEY_RECEIPT, null))
            .valueForMutation("Clipboard receipt") { receipt }
        check(preferences.edit().putString(KEY_RECEIPT, ClipboardLastSyncCodec.encode(receipt)).commit()) {
            "Clipboard receipt commit failed"
        }
    }

    private companion object { const val KEY_RECEIPT = "receipt_v1" }
}

internal object ClipboardLastSyncCodec {
    private const val SCHEMA_VERSION = 2
    private const val LEGACY_SCHEMA_VERSION = 1
    private const val MAX_BYTES = 4 * 1024

    fun encode(receipt: ClipboardReceipt): String = BoundedPayload.utf8(JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("direction", receipt.direction.persistedCode)
        .put("terminalResult", receipt.terminalResult.persistedCode)
        .put("failureCode", receipt.failureCode.persistedCode)
        .put("startedAtMillis", receipt.startedAtMillis)
        .put("completedAtMillis", receipt.completedAtMillis)
        .toString(), MAX_BYTES)

    fun decode(value: String?): ClipboardReceipt? = (read(value) as? PersistenceRead.Value)?.value

    fun read(value: String?): PersistenceRead<ClipboardReceipt> {
        if (value == null) return PersistenceRead.Missing
        if (value.isBlank()) return PersistenceRead.Corrupt("blank")
        if (value.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return PersistenceRead.Corrupt("oversized")
        return runCatching {
            val json = JSONObject(value)
            val version = if (json.has("schemaVersion")) json.strictInt("schemaVersion") else LEGACY_SCHEMA_VERSION
            if (version > SCHEMA_VERSION) return PersistenceRead.FutureVersion(version, SCHEMA_VERSION)
            if (version !in setOf(LEGACY_SCHEMA_VERSION, SCHEMA_VERSION)) return PersistenceRead.Corrupt("schema")
            val expectedFields = if (version == SCHEMA_VERSION) ALLOWED_FIELDS else LEGACY_FIELDS
            if (json.keys().asSequence().toSet() != expectedFields) return PersistenceRead.Corrupt("fields")
            PersistenceRead.Value(ClipboardReceipt(
            direction = ClipboardDirection.entries.single { it.persistedCode == json.getString("direction") },
            terminalResult = ClipboardTerminalResult.entries.single {
                it.persistedCode == json.getString("terminalResult")
            },
            failureCode = ClipboardFailureCode.entries.single {
                it.persistedCode == json.getString("failureCode")
            },
            startedAtMillis = json.strictLong("startedAtMillis"),
            completedAtMillis = json.strictLong("completedAtMillis"),
            ), migratedFrom = version.takeIf { it != SCHEMA_VERSION })
        }.getOrElse { PersistenceRead.Corrupt("decode") }
    }

    internal val ALLOWED_FIELDS = setOf(
        "schemaVersion",
        "direction",
        "terminalResult",
        "failureCode",
        "startedAtMillis",
        "completedAtMillis",
    )
    private val LEGACY_FIELDS = ALLOWED_FIELDS - "schemaVersion"

    private fun JSONObject.strictInt(name: String): Int {
        val raw = opt(name) as? Number ?: error("Invalid $name")
        return raw.toInt().also { require(it.toDouble() == raw.toDouble()) }
    }

    private fun JSONObject.strictLong(name: String): Long {
        val raw = opt(name) as? Number ?: error("Invalid $name")
        return raw.toLong().also { require(it.toDouble() == raw.toDouble()) }
    }
}
