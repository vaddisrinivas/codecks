package io.codecks.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

internal data class VerifiedHostKey(
    val line: String,
    val fingerprint: String,
)

internal fun Preferences.targets(hasKey: Boolean): List<ConnectionTarget> {
    val decoded = decodeConnectionTargets(
        raw = this[ConnectionPreferenceKeys.TARGETS].orEmpty(),
        hasKey = hasKey,
    )
    return (decoded as? ConnectionTargetsDecodeResult.Success)
        ?.targets
        .orEmpty()
        .sortedWith(compareByDescending<ConnectionTarget> { it.id == this[ConnectionPreferenceKeys.CURRENT_TARGET_ID] }.thenBy { it.host })
}

internal fun Preferences.currentTarget(hasKey: Boolean): ConnectionTarget? {
    val targets = targets(hasKey)
    val currentId = this[ConnectionPreferenceKeys.CURRENT_TARGET_ID]
    return targets.firstOrNull { it.id == currentId } ?: targets.firstOrNull()
}

internal fun Preferences.legacyTarget(hasKey: Boolean): ConnectionTarget? {
    val host = this[ConnectionPreferenceKeys.HOST].orEmpty()
    val user = this[ConnectionPreferenceKeys.USER].orEmpty()
    val port = this[ConnectionPreferenceKeys.PORT] ?: 22
    if (host.isBlank() || user.isBlank()) return null
    return ConnectionTarget(
        id = this[ConnectionPreferenceKeys.CURRENT_TARGET_ID].orEmpty(),
        host = host,
        port = port,
        user = user,
        hasKey = hasKey,
        hostKey = this[ConnectionPreferenceKeys.HOST_KEY].orEmpty(),
    )
}

internal sealed interface ConnectionTargetsDecodeResult {
    data class Success(val targets: List<ConnectionTarget>) : ConnectionTargetsDecodeResult
    data class Failure(val raw: String) : ConnectionTargetsDecodeResult
}

internal fun decodeConnectionTargets(
    raw: String,
    hasKey: Boolean,
): ConnectionTargetsDecodeResult {
    if (raw.isBlank()) return ConnectionTargetsDecodeResult.Success(emptyList())
    return runCatching {
        val array = JSONArray(raw)
        val targets = buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val host = item.getString("host")
                val user = item.getString("user")
                require(host.isNotBlank() && user.isNotBlank()) {
                    "Connection target endpoint is incomplete"
                }
                val port = if (item.has("port")) item.getInt("port") else 22
                require(port in 1..65535) { "Connection target port is invalid" }
                add(
                    ConnectionTarget(
                        id = item.optString("id"),
                        host = host,
                        port = port,
                        user = user,
                        hasKey = hasKey,
                        hostKey = item.optString("hostKey"),
                    ),
                )
            }
        }
        ConnectionTargetsDecodeResult.Success(targets)
    }.getOrElse {
        ConnectionTargetsDecodeResult.Failure(raw)
    }
}

internal fun List<ConnectionTarget>.toJson(): String {
    val array = JSONArray()
    forEach { target ->
        array.put(
            JSONObject()
                .put("id", target.id)
                .put("host", target.host)
                .put("port", target.port)
                .put("user", target.user)
                .put("hostKey", target.hostKey),
        )
    }
    return array.toString()
}

internal data class ConnectionTargetIdentityMigration(
    val targets: List<ConnectionTarget>,
    val currentTargetId: String?,
)

internal sealed interface ConnectionTargetStorageMigration {
    data class Ready(
        val targetsJson: String,
        val currentTargetId: String?,
    ) : ConnectionTargetStorageMigration

    data class PreserveUndecodable(
        val raw: String,
    ) : ConnectionTargetStorageMigration
}

internal fun planConnectionTargetStorageMigration(
    rawTargets: String,
    hasKey: Boolean,
    legacyTarget: ConnectionTarget?,
    currentTargetId: String?,
    newId: () -> String = ::newOpaqueTargetId,
): ConnectionTargetStorageMigration =
    when (val decoded = decodeConnectionTargets(rawTargets, hasKey)) {
        is ConnectionTargetsDecodeResult.Failure ->
            ConnectionTargetStorageMigration.PreserveUndecodable(decoded.raw)
        is ConnectionTargetsDecodeResult.Success -> {
            val migration = migrateConnectionTargetIdentities(
                storedTargets = decoded.targets,
                legacyTarget = legacyTarget,
                currentTargetId = currentTargetId,
                newId = newId,
            )
            ConnectionTargetStorageMigration.Ready(
                targetsJson = migration.targets.toJson(),
                currentTargetId = migration.currentTargetId,
            )
        }
    }

internal fun migrateConnectionTargetIdentities(
    storedTargets: List<ConnectionTarget>,
    legacyTarget: ConnectionTarget?,
    currentTargetId: String?,
    newId: () -> String = ::newOpaqueTargetId,
): ConnectionTargetIdentityMigration {
    val candidates = buildList {
        addAll(storedTargets)
        if (legacyTarget != null && none { it.sameEndpoint(legacyTarget) }) {
            add(legacyTarget)
        }
    }.sortedByDescending { it.id == currentTargetId }

    val usedIds = candidates
        .filterNot { it.id.isBlank() || it.usesLegacyEndpointIdentity() }
        .mapTo(mutableSetOf(), ConnectionTarget::id)
    val migratedByOldId = mutableMapOf<String, String>()
    val migratedTargets = mutableListOf<ConnectionTarget>()

    candidates.forEach { candidate ->
        val existing = migratedTargets.firstOrNull { it.sameEndpoint(candidate) }
        if (existing != null) {
            if (candidate.id.isNotBlank()) migratedByOldId[candidate.id] = existing.id
            return@forEach
        }

        val migratedId = if (candidate.id.isBlank() || candidate.usesLegacyEndpointIdentity()) {
            generateUniqueOpaqueTargetId(usedIds, newId)
        } else {
            candidate.id
        }
        usedIds += migratedId
        if (candidate.id.isNotBlank()) migratedByOldId[candidate.id] = migratedId
        migratedTargets += candidate.copy(id = migratedId)
    }

    val migratedCurrentId = currentTargetId
        ?.let(migratedByOldId::get)
        ?: legacyTarget
            ?.let { legacy -> migratedTargets.firstOrNull { it.sameEndpoint(legacy) }?.id }
        ?: migratedTargets.firstOrNull()?.id

    return ConnectionTargetIdentityMigration(
        targets = migratedTargets,
        currentTargetId = migratedCurrentId,
    )
}

private fun generateUniqueOpaqueTargetId(
    usedIds: Set<String>,
    newId: () -> String,
): String {
    repeat(10) {
        val candidate = newId()
        require(candidate.isNotBlank()) { "Generated target ID must not be blank" }
        if (candidate !in usedIds) return candidate
    }
    error("Could not generate a unique target ID")
}

internal fun newOpaqueTargetId(): String = UUID.randomUUID().toString()

// Recognition-only compatibility for endpoint-derived IDs written by older releases.
// This value is never returned as a ConnectionTarget ID or written back to storage.
private fun legacyEndpointTargetId(host: String, user: String, port: Int = 22): String =
    "mac_${user}_${host}_${port}"
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "mac_current" }

private fun ConnectionTarget.usesLegacyEndpointIdentity(): Boolean =
    id == legacyEndpointTargetId(host, user, port)

internal fun ConnectionTarget.sameEndpoint(other: ConnectionTarget): Boolean =
    sameEndpoint(other.host, other.port, other.user)

internal fun ConnectionTarget.sameEndpoint(host: String, port: Int, user: String): Boolean =
    this.host == host && this.port == port && this.user == user

internal fun legacyConnectionTargetIdMigrations(
    targets: List<ConnectionTarget>,
): Map<String, String> = targets
    .groupBy { target -> legacyEndpointTargetId(target.host, target.user, target.port) }
    .mapNotNull { (legacyId, matches) ->
        matches.map(ConnectionTarget::id)
            .distinct()
            .singleOrNull()
            ?.let { opaqueId -> legacyId to opaqueId }
    }
    .toMap()

private object ConnectionPreferenceKeys {
    val HOST = stringPreferencesKey("host")
    val PORT = intPreferencesKey("port")
    val USER = stringPreferencesKey("user")
    val HOST_KEY = stringPreferencesKey("host_key")
    val TARGETS = stringPreferencesKey("targets")
    val CURRENT_TARGET_ID = stringPreferencesKey("current_target_id")
}
