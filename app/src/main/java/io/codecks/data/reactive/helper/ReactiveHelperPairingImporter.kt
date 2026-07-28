package io.codecks.data.reactive.helper

import io.codecks.platform.helper.StoredReactiveHelperIdentity
import org.json.JSONObject

data class ReactiveHelperPairingPayload(
    val macId: String,
    val displayName: String,
    val helperId: String,
    val publicKeyFingerprint: String,
    val sharedSecretHex: String,
    val host: String? = null,
    val port: Int? = null,
) {
    init {
        require(macId.isSafeToken()) { "macId is invalid" }
        require(displayName.isSafeDisplayName()) { "displayName is invalid" }
        require(helperId.isSafeToken()) { "helperId is invalid" }
        require(publicKeyFingerprint.length in 32..128) { "publicKeyFingerprint length invalid" }
        require(sharedSecretHex.hexToByteArrayOrNull()?.size ?: 0 >= 32) { "sharedSecretHex is invalid" }
        require(host == null || host.isSafeHost()) { "host is invalid" }
        require(port == null || port in 1..65_535) { "port is invalid" }
    }
}

class ReactiveHelperPairingImporter(
    private val store: AndroidReactiveHelperCredentialStore,
) {
    suspend fun importJson(payloadJson: String): StoredReactiveHelperIdentity {
        val payload = payloadJson.decodePairingPayload()
        val identity = StoredReactiveHelperIdentity(
            macId = payload.macId,
            displayName = payload.displayName,
            helperId = payload.helperId,
            publicKeyFingerprint = payload.publicKeyFingerprint,
            secretAlias = "reactive_helper_${payload.macId.sha256Prefix()}",
        )
        store.savePairing(identity, requireNotNull(payload.sharedSecretHex.hexToByteArrayOrNull()))
        return identity
    }
}

internal fun String.decodePairingPayload(): ReactiveHelperPairingPayload {
    val json = JSONObject(this)
    return ReactiveHelperPairingPayload(
        macId = json.getString("macId"),
        displayName = json.optString("displayName", json.getString("macId")),
        helperId = json.getString("helperId"),
        publicKeyFingerprint = json.getString("publicKeyFingerprint"),
        sharedSecretHex = json.getString("sharedSecretHex"),
        host = json.optString("host").takeIf(String::isNotBlank),
        port = if (json.has("port")) json.getInt("port") else null,
    )
}

private fun String.isSafeToken(): Boolean =
    isNotBlank() &&
        length <= 128 &&
        none { it.isISOControl() || it == '\u0000' }

private fun String.isSafeDisplayName(): Boolean =
    isNotBlank() &&
        length <= 96 &&
        none { it.isISOControl() || it == '\u0000' }

private fun String.isSafeHost(): Boolean =
    isNotBlank() &&
        length <= 253 &&
        none { it.isISOControl() || it in setOf('\u0000', '/', '\\', '@') }

private fun String.sha256Prefix(): String =
    io.codecks.domain.reactive.sha256Hex(this).take(16)

private fun String.hexToByteArrayOrNull(): ByteArray? {
    val hex = trim()
    if (hex.isEmpty() || hex.length % 2 != 0) return null
    return runCatching {
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }.getOrNull()
}
