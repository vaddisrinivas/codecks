package io.codecks.data.clipboard

import android.content.Context
import io.codecks.domain.clipboard.ClipboardDirection
import io.codecks.domain.clipboard.ClipboardFailureCode
import io.codecks.domain.clipboard.ClipboardReceipt
import io.codecks.domain.clipboard.ClipboardTerminalResult
import org.json.JSONObject

class ClipboardLastSyncStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "clipboard_last_sync",
        Context.MODE_PRIVATE,
    )

    fun read(): ClipboardReceipt? = ClipboardLastSyncCodec.decode(
        preferences.getString("receipt_v1", null),
    )

    fun save(receipt: ClipboardReceipt) {
        preferences.edit().putString("receipt_v1", ClipboardLastSyncCodec.encode(receipt)).apply()
    }
}

internal object ClipboardLastSyncCodec {
    fun encode(receipt: ClipboardReceipt): String = JSONObject()
        .put("direction", receipt.direction.persistedCode)
        .put("terminalResult", receipt.terminalResult.persistedCode)
        .put("failureCode", receipt.failureCode.persistedCode)
        .put("startedAtMillis", receipt.startedAtMillis)
        .put("completedAtMillis", receipt.completedAtMillis)
        .toString()

    fun decode(value: String?): ClipboardReceipt? = runCatching {
        val json = JSONObject(value?.takeIf(String::isNotBlank) ?: return null)
        if (json.keys().asSequence().toSet() != ALLOWED_FIELDS) return null
        ClipboardReceipt(
            direction = ClipboardDirection.entries.single { it.persistedCode == json.getString("direction") },
            terminalResult = ClipboardTerminalResult.entries.single {
                it.persistedCode == json.getString("terminalResult")
            },
            failureCode = ClipboardFailureCode.entries.single {
                it.persistedCode == json.getString("failureCode")
            },
            startedAtMillis = json.getLong("startedAtMillis"),
            completedAtMillis = json.getLong("completedAtMillis"),
        )
    }.getOrNull()

    internal val ALLOWED_FIELDS = setOf(
        "direction",
        "terminalResult",
        "failureCode",
        "startedAtMillis",
        "completedAtMillis",
    )
}
