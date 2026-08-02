package io.codecks.ui.connection

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val SETUP_SNAPSHOT_SCHEMA_VERSION = 1
internal const val SETUP_RECEIPT_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
private const val SETUP_RECEIPT_MAX_FUTURE_SKEW_MS = 5L * 60L * 1_000L

enum class SetupStep(val persistedCode: String) {
    FindMac("find_mac"),
    TrustMac("trust_mac"),
    Authorize("authorize"),
    VerifyControls("verify_controls"),
}

enum class SetupReceiptResult(val persistedCode: String) {
    Passed("passed"),
    Failed("failed"),
    Skipped("skipped"),
}

data class SetupReceipt(
    val schemaVersion: Int = SETUP_SNAPSHOT_SCHEMA_VERSION,
    val step: SetupStep,
    val result: SetupReceiptResult,
    val completedAtMillis: Long,
) {
    fun isValidPassFor(
        expectedStep: SetupStep,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean =
        schemaVersion == SETUP_SNAPSHOT_SCHEMA_VERSION &&
            step == expectedStep &&
            result == SetupReceiptResult.Passed &&
            completedAtMillis > 0L &&
            completedAtMillis <= nowMillis + SETUP_RECEIPT_MAX_FUTURE_SKEW_MS &&
            nowMillis - completedAtMillis <= SETUP_RECEIPT_MAX_AGE_MS
}

data class SetupSnapshot(
    val schemaVersion: Int = SETUP_SNAPSHOT_SCHEMA_VERSION,
    val receipts: Map<SetupStep, SetupReceipt> = emptyMap(),
) {
    fun isPassed(
        step: SetupStep,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean =
        schemaVersion == SETUP_SNAPSHOT_SCHEMA_VERSION &&
            receipts[step]?.isValidPassFor(step, nowMillis) == true

    val firstMandatoryNotPassed: SetupStep?
        get() = SetupStep.entries.firstOrNull { !isPassed(it) }

    val isComplete: Boolean
        get() = firstMandatoryNotPassed == null
}

internal object SetupSnapshotCodec {
    fun encode(snapshot: SetupSnapshot): String = buildString {
        append(SETUP_SNAPSHOT_SCHEMA_VERSION)
        append('|')
        append(
            SetupStep.entries.mapNotNull { step ->
                snapshot.receipts[step]?.let { receipt ->
                    "${step.persistedCode},${receipt.result.persistedCode},${receipt.completedAtMillis}"
                }
            }.joinToString(";"),
        )
    }

    fun decode(encoded: String?): SetupSnapshot {
        if (encoded.isNullOrBlank()) return SetupSnapshot()
        val parts = encoded.split('|', limit = 2)
        if (parts.size != 2 || parts[0].toIntOrNull() != SETUP_SNAPSHOT_SCHEMA_VERSION) {
            return SetupSnapshot()
        }
        val receipts = parts[1]
            .split(';')
            .filter(String::isNotBlank)
            .mapNotNull { encodedReceipt ->
                val fields = encodedReceipt.split(',', limit = 3)
                val step = SetupStep.entries.firstOrNull { it.persistedCode == fields.getOrNull(0) }
                    ?: return@mapNotNull null
                val result = SetupReceiptResult.entries.firstOrNull {
                    it.persistedCode == fields.getOrNull(1)
                } ?: return@mapNotNull null
                val completedAtMillis = fields.getOrNull(2)?.toLongOrNull()?.takeIf { it > 0L }
                    ?: return@mapNotNull null
                step to SetupReceipt(
                    step = step,
                    result = result,
                    completedAtMillis = completedAtMillis,
                )
            }
            .toMap()
        return SetupSnapshot(receipts = receipts)
    }
}

@Singleton
class SetupSnapshotStore private constructor(
    private val preferences: SharedPreferences?,
) {
    private var inMemoryEncoded: String? = null

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    @Synchronized
    fun load(): SetupSnapshot = SetupSnapshotCodec.decode(
        preferences?.getString(SNAPSHOT_KEY, null) ?: inMemoryEncoded,
    )

    @Synchronized
    fun record(receipt: SetupReceipt): SetupSnapshot {
        val current = load()
        val retainedReceipts = if (
            receipt.result == SetupReceiptResult.Passed &&
            current.isPassed(receipt.step)
        ) {
            current.receipts
        } else {
            current.receipts.filterKeys { it.ordinal < receipt.step.ordinal }
        }
        val next = current.copy(receipts = retainedReceipts + (receipt.step to receipt))
        val encoded = SetupSnapshotCodec.encode(next)
        if (preferences != null) {
            preferences.edit().putString(SNAPSHOT_KEY, encoded).apply()
        } else {
            inMemoryEncoded = encoded
        }
        return next
    }

    @Synchronized
    fun clear(): SetupSnapshot {
        if (preferences != null) {
            preferences.edit().remove(SNAPSHOT_KEY).apply()
        } else {
            inMemoryEncoded = null
        }
        return SetupSnapshot()
    }

    fun record(
        step: SetupStep,
        result: SetupReceiptResult,
        completedAtMillis: Long = System.currentTimeMillis(),
    ): SetupSnapshot = record(
        SetupReceipt(
            step = step,
            result = result,
            completedAtMillis = completedAtMillis,
        ),
    )

    companion object {
        private const val PREFERENCES_NAME = "codecks_settings"
        private const val SNAPSHOT_KEY = "setup_snapshot_v1"

        internal fun inMemory(): SetupSnapshotStore = SetupSnapshotStore(preferences = null)
    }
}
