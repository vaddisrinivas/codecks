package io.codecks.ui.connection

import android.content.Context

class HidConfirmationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "setup_hid_confirmation",
        Context.MODE_PRIVATE,
    )

    fun load(): HidTerminalReceipt? {
        val revision = preferences.getString("revision", null) ?: return null
        val targetId = preferences.getString("target_id", null) ?: return null
        val address = preferences.getString("hid_address", null) ?: return null
        val confirmedAt = preferences.getLong("confirmed_at", -1L).takeIf { it >= 0L } ?: return null
        return runCatching {
            HidTerminalReceipt(
                setupRevision = revision,
                macTargetId = targetId,
                hidHostAddress = address,
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
            .putString("hid_address", receipt.hidHostAddress)
            .putLong("confirmed_at", receipt.completedAtEpochMs)
            .apply()
    }
}
