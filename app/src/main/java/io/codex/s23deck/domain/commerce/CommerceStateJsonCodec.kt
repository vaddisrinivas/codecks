package io.codex.s23deck.domain.commerce

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

private const val COMMERCE_STATE_SCHEMA_VERSION = 1
private val commerceJson = Json { ignoreUnknownKeys = true }

object CommerceStateJsonCodec {
    fun encodeAccount(accountState: AccountState): String? = when (accountState) {
        AccountState.SignedOut -> null
        is AccountState.SignedIn -> buildJsonObject {
            put("schemaVersion", COMMERCE_STATE_SCHEMA_VERSION)
            put("id", accountState.account.id)
            put("email", accountState.account.email)
        }.toString()
    }

    fun decodeAccount(raw: String?): AccountState =
        raw.decodeOrNull { json ->
            if (!json.isSupportedSchemaVersion()) return@decodeOrNull null
            AccountState.SignedIn(Account(json.string("id"), json.string("email")))
        } ?: AccountState.SignedOut

    fun encodeSession(session: BackendAuthSession?): String? = session?.let {
        buildJsonObject {
            put("schemaVersion", COMMERCE_STATE_SCHEMA_VERSION)
            put("subject", it.subject)
            put("email", it.email)
            put("accessToken", it.accessToken)
            put("refreshToken", it.refreshToken)
            put("accessTokenExpiresAtEpochSeconds", it.accessTokenExpiresAtEpochSeconds)
            put("refreshTokenExpiresAtEpochSeconds", it.refreshTokenExpiresAtEpochSeconds)
        }.toString()
    }

    fun decodeSession(raw: String?): BackendAuthSession? =
        raw.decodeOrNull { json ->
            if (!json.isSupportedSchemaVersion()) return@decodeOrNull null
            BackendAuthSession(
                subject = json.string("subject"),
                email = json.stringOrNull("email"),
                accessToken = json.string("accessToken"),
                refreshToken = json.string("refreshToken"),
                accessTokenExpiresAtEpochSeconds = json.long("accessTokenExpiresAtEpochSeconds"),
                refreshTokenExpiresAtEpochSeconds = json.long("refreshTokenExpiresAtEpochSeconds"),
            )
        }

    fun encodeEntitlement(entitlement: Entitlement): String {
        val json = buildJsonObject {
            put("schemaVersion", COMMERCE_STATE_SCHEMA_VERSION)
            put("tier", entitlement.tier.name)
            put("status", entitlement.status.name)
            when (val grace = entitlement.offlineGrace) {
                OfflineGrace.None -> put("offlineGrace", JsonNull)
                is OfflineGrace.Active -> {
                    put(
                        "offlineGrace",
                        buildJsonObject { put("expiresAtEpochMillis", grace.expiresAtEpochMillis) },
                    )
                }
            }
        }
        return json.toString()
    }

    fun decodeEntitlement(raw: String?): Entitlement =
        raw.decodeOrNull { json ->
            if (!json.isSupportedSchemaVersion()) return@decodeOrNull null
            val offline = (json["offlineGrace"] as? JsonObject)?.let {
                OfflineGrace.Active(it.long("expiresAtEpochMillis"))
            } ?: OfflineGrace.None
            Entitlement(
                tier = EntitlementTier.valueOf(json.string("tier")),
                status = EntitlementStatus.valueOf(json.string("status")),
                offlineGrace = offline,
            )
        } ?: Entitlement(EntitlementTier.Free, EntitlementStatus.Free)

    private inline fun <T> String?.decodeOrNull(block: (JsonObject) -> T?): T? =
        this?.let { raw -> runCatching { block(commerceJson.parseToJsonElement(raw).jsonObject) }.getOrNull() }

    private fun JsonObject.isSupportedSchemaVersion(): Boolean {
        val version = this["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull() ?: COMMERCE_STATE_SCHEMA_VERSION
        return version <= COMMERCE_STATE_SCHEMA_VERSION
    }

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.content ?: error("$key missing")
    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.long ?: error("$key missing")
}
