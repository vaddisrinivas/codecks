package io.codecks.data.reactive.helper

import io.codecks.platform.helper.StoredReactiveHelperIdentity
import io.codecks.shared.protocol.PAIRING_V2_LIFETIME_MILLIS
import io.codecks.shared.protocol.PAIRING_V2_SCHEMA
import io.codecks.shared.protocol.PairingV2TranscriptFields
import io.codecks.shared.protocol.pairingV2Transcript
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class ReactiveHelperPairingV2Offer(
    val schema: String,
    val offerId: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val macId: String,
    val displayName: String,
    val helperId: String,
    val publicKeyFingerprint: String,
    val host: String?,
    val port: Int?,
    internal val offerNonce: String,
    internal val offerSecret: ByteArray,
) {
    fun validateShape() {
        require(schema == PAIRING_V2_SCHEMA) { "Unsupported helper pairing payload" }
        require(offerId.isEntropyToken()) { "offerId is invalid" }
        require(macId.isSafeToken()) { "macId is invalid" }
        require(displayName.isSafeDisplayName()) { "displayName is invalid" }
        require(helperId.isSafeToken()) { "helperId is invalid" }
        require(publicKeyFingerprint.length in 32..128) { "publicKeyFingerprint length invalid" }
        require(offerNonce.isEntropyToken()) { "offerNonce is invalid" }
        require(offerSecret.size == 32) { "offer secret is invalid" }
        require(expiresAtMillis - issuedAtMillis == PAIRING_V2_LIFETIME_MILLIS) { "Pairing offer lifetime invalid" }
        require(issuedAtMillis >= 0) { "Pairing offer time invalid" }
        require(host == null || host.isSafeHost()) { "host is invalid" }
        require(port == null || port in 1..65_535) { "port is invalid" }
    }

    fun locallyExpired(nowMillis: Long): Boolean = nowMillis > expiresAtMillis
}

data class PendingReactiveHelperPairing(
    val offer: ReactiveHelperPairingV2Offer,
    val deviceId: String,
    val deviceNonce: String,
    val transcript: String,
    val matchingCode: String,
    internal val derivedCredential: ByteArray,
) {
    val beginProof: String = PairingV2Crypto.beginProof(offer.offerSecret, transcript)
    val confirmationProof: String = PairingV2Crypto.confirmationProof(derivedCredential, transcript)
    fun zeroize() { derivedCredential.fill(0); offer.offerSecret.fill(0) }
}

data class PairingV2ServerAcceptance(
    val offerId: String,
    val deviceId: String,
    val accepted: Boolean,
    val serverProof: String,
)

interface ReactiveHelperPairingStore {
    suspend fun savePairing(identity: StoredReactiveHelperIdentity, sharedSecret: ByteArray)
}

class ReactiveHelperPairingImporter @Inject constructor(
    private val store: ReactiveHelperPairingStore,
) {
    private val commitMutex = Mutex()
    private val completedOffers = linkedSetOf<String>()
    /** Public/manual import never accepts the permanent v1 shared secret. */
    suspend fun importJson(payloadJson: String): StoredReactiveHelperIdentity {
        payloadJson.decodePairingV2Offer(System.currentTimeMillis())
        error("Pairing confirmation is required")
    }

    fun prepareV2(
        payloadJson: String,
        deviceId: String,
        deviceNonce: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): PendingReactiveHelperPairing {
        require(deviceId.isEntropyToken()) { "deviceId is invalid" }
        require(deviceNonce.isEntropyToken()) { "deviceNonce is invalid" }
        val offer = payloadJson.decodePairingV2Offer(nowMillis)
        val transcript = pairingV2Transcript(
            PairingV2TranscriptFields(
                offerId = offer.offerId,
                issuedAtMillis = offer.issuedAtMillis,
                expiresAtMillis = offer.expiresAtMillis,
                macId = offer.macId,
                displayName = offer.displayName,
                helperId = offer.helperId,
                publicKeyFingerprint = offer.publicKeyFingerprint,
                host = offer.host,
                port = offer.port,
                offerNonce = offer.offerNonce,
                deviceId = deviceId,
                deviceNonce = deviceNonce,
            ),
        )
        val credential = PairingV2Crypto.deriveCredential(offer.offerSecret, transcript)
        return PendingReactiveHelperPairing(
            offer = offer,
            deviceId = deviceId,
            deviceNonce = deviceNonce,
            transcript = transcript,
            matchingCode = PairingV2Crypto.matchingCode(credential, transcript),
            derivedCredential = credential,
        )
    }

    suspend fun commitConfirmed(
        pending: PendingReactiveHelperPairing,
        acceptance: PairingV2ServerAcceptance,
    ): StoredReactiveHelperIdentity = commitMutex.withLock {
        require(acceptance.accepted) { "Mac pairing confirmation required" }
        require(acceptance.offerId == pending.offer.offerId && acceptance.deviceId == pending.deviceId) {
            "Pairing acceptance mismatch"
        }
        val expected = PairingV2Crypto.serverAcceptanceProof(pending.derivedCredential, pending.transcript)
        require(constantTimeEquals(acceptance.serverProof, expected)) { "Mac pairing proof invalid" }
        require(completedOffers.add(acceptance.offerId)) { "Pairing acceptance already consumed" }
        if (completedOffers.size > 64) completedOffers.remove(completedOffers.first())
        val offer = pending.offer
        val identity = StoredReactiveHelperIdentity(
            macId = offer.macId, displayName = offer.displayName, helperId = offer.helperId,
            publicKeyFingerprint = offer.publicKeyFingerprint,
            secretAlias = "reactive_helper_${offer.macId.sha256Prefix()}", host = offer.host, port = offer.port,
        )
        try {
            store.savePairing(identity, pending.derivedCredential.copyOf())
            pending.zeroize()
            identity
        } catch (failure: Throwable) {
            completedOffers.remove(acceptance.offerId)
            throw failure
        }
    }
}

fun reactiveHelperPairingJsonFromUri(uri: String?, prefix: String): String? {
    if (uri.isNullOrBlank() || uri.length > MAX_PAIRING_URI_CHARS || !uri.startsWith("$prefix?") || '#' in uri) return null
    val parts = uri.substringAfter('?').split('&')
    if (parts.size != 1 || parts.single().substringBefore('=') != "payload") return null
    val value = parts.single().substringAfter('=', "").takeIf(String::isNotBlank) ?: return null
    val decoded = runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrNull() ?: return null
    if (decoded.encodeToByteArray().size > MAX_PAIRING_JSON_BYTES) return null
    val objectValue = runCatching { strictPairingObject(decoded) }.getOrNull() ?: return null
    if (objectValue.optString("schema") != PAIRING_V2_SCHEMA) return null
    return decoded
}

internal fun String.decodePairingV2Offer(nowMillis: Long): ReactiveHelperPairingV2Offer {
    require(encodeToByteArray().size <= MAX_PAIRING_JSON_BYTES) { "Pairing payload too large" }
    val json = strictPairingObject(this)
    val offer = ReactiveHelperPairingV2Offer(
        schema = json.getString("schema"),
        offerId = json.getString("offerId"),
        issuedAtMillis = json.getLong("issuedAtMillis"),
        expiresAtMillis = json.getLong("expiresAtMillis"),
        macId = json.getString("macId"),
        displayName = json.optString("displayName", json.getString("macId")),
        helperId = json.getString("helperId"),
        publicKeyFingerprint = json.getString("publicKeyFingerprint"),
        host = json.optString("host").takeIf(String::isNotBlank),
        port = if (json.has("port")) json.getInt("port") else null,
        offerNonce = json.getString("offerNonce"),
        offerSecret = requireNotNull(json.getString("offerSecretHex").hexToByteArrayOrNull()) { "offer secret is invalid" },
    )
    offer.validateShape()
    return offer
}

private val allowedPairingKeys = setOf(
    "schema", "offerId", "issuedAtMillis", "expiresAtMillis", "macId", "displayName", "helperId",
    "publicKeyFingerprint", "host", "port", "offerNonce", "offerSecretHex",
)
private val requiredPairingKeys = allowedPairingKeys - setOf("host", "port")
private const val MAX_PAIRING_URI_CHARS = 8 * 1024
private const val MAX_PAIRING_JSON_BYTES = 4 * 1024

private fun strictPairingObject(raw: String): JSONObject {
    val keyPattern = Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:")
    val keys = keyPattern.findAll(raw).map { it.groupValues[1] }.toList()
    require(keys.size == keys.toSet().size) { "Duplicate pairing field" }
    require(keys.toSet().all { it in allowedPairingKeys }) { "Unknown pairing field" }
    require(keys.toSet().containsAll(requiredPairingKeys)) { "Missing pairing field" }
    return JSONObject(raw).also { require(it.length() == keys.size) { "Nested pairing values are not allowed" } }
}

private fun String.isSafeToken(): Boolean =
        isNotBlank() && encodeToByteArray().size <= 128 && none { it.isISOControl() || it == '\u0000' }

private fun String.isEntropyToken(): Boolean {
    if (length != 22 || !all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) return false
    val decoded = runCatching { Base64.getUrlDecoder().decode(this) }.getOrNull() ?: return false
    return decoded.size == 16 && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == this
}

private fun String.isSafeDisplayName(): Boolean =
    isNotBlank() && encodeToByteArray().size <= 96 && none { it.isISOControl() || it == '\u0000' }

private fun String.isSafeHost(): Boolean =
    isNotBlank() && length <= 253 && none { it.isISOControl() || it in setOf('\u0000', '/', '\\', '@') }

private fun String.sha256Prefix(): String = io.codecks.domain.reactive.sha256Hex(this).take(16)

private fun String.hexToByteArrayOrNull(): ByteArray? {
    val hex = trim()
    if (hex.isEmpty() || hex.length % 2 != 0) return null
    return runCatching { ByteArray(hex.length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
}

private fun constantTimeEquals(left: String, right: String): Boolean {
    val a = left.encodeToByteArray()
    val b = right.encodeToByteArray()
    if (a.size != b.size) return false
    var difference = 0
    a.indices.forEach { difference = difference or (a[it].toInt() xor b[it].toInt()) }
    return difference == 0
}
