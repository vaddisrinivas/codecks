package io.codecks.ui.connection

import android.content.Context

class HidConfirmationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "setup_hid_confirmation",
        Context.MODE_PRIVATE,
    )

    fun load(): HidTerminalReceipt? {
        if (preferences.contains("hid_address")) {
            preferences.edit().remove("hid_address").apply()
        }
        val revision = preferences.getString("revision", null) ?: return null
        val targetId = preferences.getString("target_id", null) ?: return null
        val hostToken = preferences.getString("hid_host_token", null) ?: return null
        val confirmedAt = preferences.getLong("confirmed_at", -1L).takeIf { it >= 0L } ?: return null
        return runCatching {
            HidTerminalReceipt(
                setupRevision = revision,
                macTargetId = targetId,
                hidHostToken = hostToken,
                result = HidTerminalResult.USER_CONFIRMED,
                completedAtEpochMs = confirmedAt,
            )
        }.getOrNull()
    }

    fun record(receipt: HidTerminalReceipt) {
        require(receipt.result == HidTerminalResult.USER_CONFIRMED)
        preferences.edit()
            .putString("revision", receipt.setupRevision)
            .putString("target_id", receipt.macTargetId)
            .putString("hid_host_token", receipt.hidHostToken)
            .remove("hid_address")
            .putLong("confirmed_at", receipt.completedAtEpochMs)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
