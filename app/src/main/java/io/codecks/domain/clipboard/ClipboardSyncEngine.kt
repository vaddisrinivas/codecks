package io.codecks.domain.clipboard

import java.security.MessageDigest

enum class ClipboardSyncMode { Off, PhoneToMac, MacToPhone, Bidirectional }

enum class ClipboardEndpoint { Phone, Mac }

@JvmInline
value class ClipboardSourceId(val value: String)

data class ClipboardRevision(
    val revision: Long,
    val endpoint: ClipboardEndpoint,
    val sourceId: ClipboardSourceId,
    val hash: String,
    val observedAtMillis: Long,
)

data class ClipboardConflict(
    val phone: ClipboardRevision,
    val mac: ClipboardRevision,
)

data class ClipboardSyncSnapshot(
    val phone: ClipboardRevision? = null,
    val mac: ClipboardRevision? = null,
    val history: List<ClipboardRevision> = emptyList(),
    val conflict: ClipboardConflict? = null,
    val staleEndpoints: Set<ClipboardEndpoint> = emptySet(),
) {
    val latestRevision: Long get() = history.lastOrNull()?.revision ?: 0L
}

sealed interface ClipboardSyncAction {
    data object None : ClipboardSyncAction
    data class WriteToPhone(val hash: String) : ClipboardSyncAction
    data class WriteToMac(val hash: String) : ClipboardSyncAction
    data class Conflict(val conflict: ClipboardConflict) : ClipboardSyncAction
}

data class ClipboardObservation(
    val revision: ClipboardRevision,
    val changed: Boolean,
    val loopEcho: Boolean,
    val snapshot: ClipboardSyncSnapshot,
)

class ClipboardSyncEngine(
    private val maxHistory: Int = 24,
    private val staleAfterMillis: Long = 15_000L,
) {
    private var nextRevision = 0L
    private var phone: ClipboardRevision? = null
    private var mac: ClipboardRevision? = null
    private var commonHash: String? = null
    private val pendingEchoHashes = mutableMapOf<ClipboardEndpoint, String>()
    private val history = ArrayDeque<ClipboardRevision>()
    private var conflict: ClipboardConflict? = null

    fun observe(
        endpoint: ClipboardEndpoint,
        text: String,
        sourceId: ClipboardSourceId,
        nowMillis: Long,
    ): ClipboardObservation {
        val hash = ClipboardHash.of(text)
        val previous = revisionFor(endpoint)
        val loopEcho = pendingEchoHashes[endpoint] == hash
        if (previous?.hash == hash) {
            if (loopEcho) pendingEchoHashes.remove(endpoint)
            return ClipboardObservation(previous, changed = false, loopEcho = loopEcho, snapshot(nowMillis))
        }

        val revision = ClipboardRevision(
            revision = ++nextRevision,
            endpoint = endpoint,
            sourceId = sourceId,
            hash = hash,
            observedAtMillis = nowMillis,
        )
        setRevision(endpoint, revision)
        appendHistory(revision)

        if (loopEcho) {
            pendingEchoHashes.remove(endpoint)
            commonHash = hash
            conflict = null
        } else {
            refreshCommonHash()
        }

        return ClipboardObservation(revision, changed = true, loopEcho = loopEcho, snapshot(nowMillis))
    }

    fun decide(mode: ClipboardSyncMode, nowMillis: Long): ClipboardSyncAction {
        val phoneRevision = phone
        val macRevision = mac
        if (mode == ClipboardSyncMode.Off || (phoneRevision == null && macRevision == null)) {
            return ClipboardSyncAction.None
        }

        return when (mode) {
            ClipboardSyncMode.Off -> ClipboardSyncAction.None
            ClipboardSyncMode.PhoneToMac -> {
                if (phoneRevision != null && phoneRevision.hash != macRevision?.hash) {
                    ClipboardSyncAction.WriteToMac(phoneRevision.hash)
                } else {
                    ClipboardSyncAction.None
                }
            }
            ClipboardSyncMode.MacToPhone -> {
                if (macRevision != null && macRevision.hash != phoneRevision?.hash) {
                    ClipboardSyncAction.WriteToPhone(macRevision.hash)
                } else {
                    ClipboardSyncAction.None
                }
            }
            ClipboardSyncMode.Bidirectional -> decideBidirectional(phoneRevision, macRevision)
        }
    }

    fun markApplied(action: ClipboardSyncAction) {
        when (action) {
            is ClipboardSyncAction.WriteToMac -> pendingEchoHashes[ClipboardEndpoint.Mac] = action.hash
            is ClipboardSyncAction.WriteToPhone -> pendingEchoHashes[ClipboardEndpoint.Phone] = action.hash
            ClipboardSyncAction.None,
            is ClipboardSyncAction.Conflict,
            -> Unit
        }
    }

    fun snapshot(nowMillis: Long): ClipboardSyncSnapshot = ClipboardSyncSnapshot(
        phone = phone,
        mac = mac,
        history = history.toList(),
        conflict = conflict,
        staleEndpoints = staleEndpoints(nowMillis),
    )

    private fun decideBidirectional(
        phoneRevision: ClipboardRevision?,
        macRevision: ClipboardRevision?,
    ): ClipboardSyncAction {
        if (phoneRevision == null && macRevision != null) return ClipboardSyncAction.WriteToPhone(macRevision.hash)
        if (macRevision == null && phoneRevision != null) return ClipboardSyncAction.WriteToMac(phoneRevision.hash)
        if (phoneRevision == null || macRevision == null || phoneRevision.hash == macRevision.hash) {
            refreshCommonHash()
            return ClipboardSyncAction.None
        }

        val common = commonHash
        val phoneChanged = phoneRevision.hash != common
        val macChanged = macRevision.hash != common
        if (common != null && phoneChanged && !macChanged) return ClipboardSyncAction.WriteToMac(phoneRevision.hash)
        if (common != null && macChanged && !phoneChanged) return ClipboardSyncAction.WriteToPhone(macRevision.hash)

        val currentConflict = ClipboardConflict(phoneRevision, macRevision)
        conflict = currentConflict
        return ClipboardSyncAction.Conflict(currentConflict)
    }

    private fun revisionFor(endpoint: ClipboardEndpoint): ClipboardRevision? = when (endpoint) {
        ClipboardEndpoint.Phone -> phone
        ClipboardEndpoint.Mac -> mac
    }

    private fun setRevision(endpoint: ClipboardEndpoint, revision: ClipboardRevision) {
        when (endpoint) {
            ClipboardEndpoint.Phone -> phone = revision
            ClipboardEndpoint.Mac -> mac = revision
        }
    }

    private fun appendHistory(revision: ClipboardRevision) {
        history.addLast(revision)
        while (history.size > maxHistory) history.removeFirst()
    }

    private fun refreshCommonHash() {
        val phoneHash = phone?.hash
        val macHash = mac?.hash
        if (phoneHash != null && phoneHash == macHash) {
            commonHash = phoneHash
            conflict = null
        }
    }

    private fun staleEndpoints(nowMillis: Long): Set<ClipboardEndpoint> = buildSet {
        phone?.let { if (nowMillis - it.observedAtMillis > staleAfterMillis) add(ClipboardEndpoint.Phone) }
        mac?.let { if (nowMillis - it.observedAtMillis > staleAfterMillis) add(ClipboardEndpoint.Mac) }
    }
}

object ClipboardHash {
    fun of(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
