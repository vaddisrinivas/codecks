package io.codecks.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val REACTIVE_PROTOCOL_SCHEMA = "reactive.v1"
const val REACTIVE_PROTOCOL_MIN_COMPATIBLE_MAJOR = 1
const val REACTIVE_PROTOCOL_CURRENT_MAJOR = 1
const val REACTIVE_MAX_BODY_BYTES = 64 * 1024
const val REACTIVE_MAX_TOKEN_BYTES = 128
const val REACTIVE_REPLAY_CACHE_SIZE = 256
const val REACTIVE_CLOCK_SKEW_MILLIS = 2 * 60 * 1000L
const val REACTIVE_REPLAY_WINDOW_MILLIS = 5 * 60 * 1000L
const val REACTIVE_MAX_TRANSFER_BYTES = 50L * 1024L * 1024L

@Serializable
data class ReactiveHello(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val minMajor: Int = REACTIVE_PROTOCOL_MIN_COMPATIBLE_MAJOR,
    val maxMajor: Int = REACTIVE_PROTOCOL_CURRENT_MAJOR,
    val deviceId: String,
    val clientNonce: String,
    val clientTimeMillis: Long,
    val supportedCapabilities: Set<ReactiveCapabilityId> = emptySet(),
)

@Serializable
data class ReactiveChallenge(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val selectedMajor: Int = REACTIVE_PROTOCOL_CURRENT_MAJOR,
    val sessionId: String,
    val serverNonce: String,
    val expiresAtMillis: Long,
    val macId: String,
    val helperIdentity: HelperIdentityPin,
    val verificationCode: String,
)

@Serializable
data class ReactiveProof(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val sessionId: String,
    val proof: String,
    val pinAcknowledgement: String,
)

@Serializable
data class ReactiveAuthResult(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val sessionId: String,
    val accepted: Boolean,
    val expiresAtMillis: Long,
    val serverProof: String,
    val code: String? = null,
    val pinnedHelperIdentity: HelperIdentityPin? = null,
)

@Serializable
data class HelperIdentityPin(
    val helperId: String,
    val publicKeyFingerprint: String,
    val issuedAtMillis: Long,
    val trustState: HelperTrustState,
)

@Serializable
enum class HelperTrustState {
    @SerialName("unverified") Unverified,
    @SerialName("verified") Verified,
    @SerialName("revoked") Revoked,
}

@Serializable
data class ReactivePairingRecord(
    val deviceId: String,
    val helperIdentity: HelperIdentityPin,
    val credentialId: String,
    val createdAtMillis: Long,
    val lastUsedAtMillis: Long,
    val revokedAtMillis: Long? = null,
)

@Serializable
data class ReactiveRevocation(
    val recordId: String,
    val reason: RevocationReason,
    val requestedAtMillis: Long,
    val requiresRepairing: Boolean = true,
)

@Serializable
enum class RevocationReason {
    @SerialName("user_requested") UserRequested,
    @SerialName("helper_identity_changed") HelperIdentityChanged,
    @SerialName("credential_compromised") CredentialCompromised,
    @SerialName("replay_detected") ReplayDetected,
}

@Serializable
data class ReactiveRequestEnvelope(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val sessionId: String,
    val sequence: Long,
    val requestId: String,
    val deadlineMillis: Long,
    val bodyJson: String,
    val authTag: String,
)

@Serializable
data class ReactiveResponseEnvelope(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val sessionId: String,
    val sequence: Long,
    val requestId: String,
    val status: ReceiptStatus,
    val code: String? = null,
    val retryable: Boolean = false,
    val bodyJson: String? = null,
    val authTag: String,
)

@Serializable
data class ReactiveError(
    val code: ReactiveErrorCode,
    val message: String,
    val retryable: Boolean = false,
    val safeForUser: Boolean = true,
)

@Serializable
enum class ReactiveErrorCode {
    @SerialName("unsupported_protocol") UnsupportedProtocol,
    @SerialName("authentication_required") AuthenticationRequired,
    @SerialName("pin_mismatch") PinMismatch,
    @SerialName("replay_detected") ReplayDetected,
    @SerialName("clock_skew") ClockSkew,
    @SerialName("unsupported_capability") UnsupportedCapability,
    @SerialName("precondition_failed") PreconditionFailed,
    @SerialName("conflict") Conflict,
    @SerialName("timeout") Timeout,
    @SerialName("cancelled") Cancelled,
    @SerialName("partial_failure") PartialFailure,
    @SerialName("transfer_too_large") TransferTooLarge,
    @SerialName("path_denied") PathDenied,
    @SerialName("internal") Internal,
}

@Serializable
enum class ReceiptStatus {
    @SerialName("accepted") Accepted,
    @SerialName("completed") Completed,
    @SerialName("partial_failure") PartialFailure,
    @SerialName("timeout") Timeout,
    @SerialName("cancelled") Cancelled,
    @SerialName("failed") Failed,
    @SerialName("denied") Denied,
}

@Serializable
sealed interface ReactiveHelperRequest {
    @Serializable
    @SerialName("ping")
    data class Ping(val clientTimeMillis: Long) : ReactiveHelperRequest

    @Serializable
    @SerialName("capabilities")
    data object Capabilities : ReactiveHelperRequest

    @Serializable
    @SerialName("basic_state")
    data object BasicState : ReactiveHelperRequest

    @Serializable
    @SerialName("state_delta")
    data class StateDelta(val sinceRevision: Long) : ReactiveHelperRequest

    @Serializable
    @SerialName("execute")
    data class Execute(
        val actionId: String,
        val actionRevision: String,
        val operationId: String,
        val idempotencyKey: String,
        val timeoutMillis: Long,
        val cancellationToken: String,
        val preconditions: List<ActionPrecondition> = emptyList(),
        val arguments: Map<String, String> = emptyMap(),
    ) : ReactiveHelperRequest

    @Serializable
    @SerialName("undo")
    data class Undo(val undoToken: String, val reason: String) : ReactiveHelperRequest

    @Serializable
    @SerialName("cancel")
    data class Cancel(val operationId: String, val cancellationToken: String) : ReactiveHelperRequest
}

@Serializable
data class ReactiveHelperCapabilities(
    val helperId: String,
    val protocolMajor: Int,
    val values: Set<ReactiveCapabilityId>,
    val providers: List<ProviderCapability> = emptyList(),
)

@Serializable
enum class ReactiveCapabilityId {
    @SerialName("state.front_app") FrontAppState,
    @SerialName("state.window") WindowState,
    @SerialName("action.execute") ActionExecute,
    @SerialName("action.undo") ActionUndo,
    @SerialName("clipboard.selected_text") ClipboardSelectedText,
    @SerialName("spotlight.search") SpotlightSearch,
    @SerialName("transfer.sftp") TransferSftp,
    @SerialName("shortcuts.apple") AppleShortcuts,
    @SerialName("brightness.monitor") MonitorBrightness,
    @SerialName("accessibility.discovery") AccessibilityDiscovery,
}

@Serializable
data class ProviderCapability(
    val providerId: String,
    val capabilities: Set<ReactiveCapabilityId>,
    val confidenceFloor: Double,
    val requiresConfirmation: Boolean,
)

@Serializable
data class ReactiveHelperBasicState(
    val macId: String,
    val snapshotRevision: Long,
    val capturedAtMillis: Long,
    val freshnessMillis: Long,
    val provenance: StateProvenance,
    val frontAppBundleId: String? = null,
    val frontAppName: String? = null,
    val capabilities: Set<ReactiveCapabilityId> = emptySet(),
    val displays: List<DisplayState> = emptyList(),
    val activeWindow: WindowState? = null,
    val stale: Boolean = false,
)

@Serializable
data class ReactiveStateDelta(
    val macId: String,
    val baseRevision: Long,
    val newRevision: Long,
    val capturedAtMillis: Long,
    val provenance: StateProvenance,
    val changes: List<StateChange>,
)

@Serializable
data class StateChange(
    val path: String,
    val value: JsonElement? = null,
    val removed: Boolean = false,
)

@Serializable
enum class StateProvenance {
    @SerialName("helper") Helper,
    @SerialName("ssh") Ssh,
    @SerialName("cached") Cached,
    @SerialName("test_fixture") TestFixture,
}

@Serializable
data class DisplayState(
    val displayId: String,
    val name: String,
    val brightnessPercent: Int? = null,
)

@Serializable
data class WindowState(
    val windowId: String,
    val title: String,
    val bundleId: String,
    val focused: Boolean,
)

@Serializable
data class ProviderCandidate(
    val candidateId: String,
    val providerId: String,
    val actionId: String,
    val confidence: Double,
    val explanation: String,
    val policy: PolicyDecision,
    val conflicts: List<ProviderConflict> = emptyList(),
)

@Serializable
enum class PolicyDecision {
    @SerialName("allow") Allow,
    @SerialName("confirm") Confirm,
    @SerialName("deny") Deny,
}

@Serializable
data class ProviderConflict(
    val otherCandidateId: String,
    val reason: ConflictReason,
    val message: String,
)

@Serializable
enum class ConflictReason {
    @SerialName("same_gesture") SameGesture,
    @SerialName("unsafe_effect") UnsafeEffect,
    @SerialName("stale_state") StaleState,
    @SerialName("unsupported_capability") UnsupportedCapability,
}

@Serializable
data class ActionPrecondition(
    val kind: ActionPreconditionKind,
    val expectedRevision: Long? = null,
    val expectedBundleId: String? = null,
)

@Serializable
enum class ActionPreconditionKind {
    @SerialName("state_revision_at_least") StateRevisionAtLeast,
    @SerialName("front_app_bundle") FrontAppBundle,
    @SerialName("capability_available") CapabilityAvailable,
}

@Serializable
data class ReactiveHelperActionReceipt(
    val receiptId: String,
    val operationId: String,
    val idempotencyKey: String,
    val actionId: String,
    val actionRevision: String,
    val status: ReceiptStatus,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val resultCode: String,
    val undoToken: String? = null,
    val partialFailures: List<ReactiveError> = emptyList(),
)

@Serializable
data class ReactiveUndoResult(
    val undoToken: String,
    val status: UndoStatus,
    val completedAtMillis: Long,
    val message: String? = null,
)

@Serializable
enum class UndoStatus {
    @SerialName("completed") Completed,
    @SerialName("not_supported") NotSupported,
    @SerialName("expired") Expired,
    @SerialName("failed") Failed,
}

@Serializable
data class ReactiveTransferMetadata(
    val transferId: String,
    val fileName: String,
    val byteCount: Long,
    val sha256: String,
    val sourcePath: String,
    val destinationHint: String,
    val cleanupRequired: Boolean = true,
)

@Serializable
data class RedactionRule(
    val fieldPath: String,
    val strategy: RedactionStrategy,
)

@Serializable
enum class RedactionStrategy {
    @SerialName("drop") Drop,
    @SerialName("hash") Hash,
    @SerialName("last4") Last4,
}

fun requireReactiveSchema(schema: String) {
    require(schema == REACTIVE_PROTOCOL_SCHEMA) {
        "Unsupported Reactive protocol schema: $schema"
    }
}

fun negotiateProtocolMajor(clientMin: Int, clientMax: Int, helperMin: Int, helperMax: Int): Int {
    require(clientMin > 0 && clientMax >= clientMin) { "invalid client compatibility window" }
    require(helperMin > 0 && helperMax >= helperMin) { "invalid helper compatibility window" }
    val selected = minOf(clientMax, helperMax)
    require(selected >= maxOf(clientMin, helperMin)) { "unsupported protocol compatibility window" }
    return selected
}

fun validateHello(hello: ReactiveHello) {
    requireReactiveSchema(hello.schema)
    negotiateProtocolMajor(
        hello.minMajor,
        hello.maxMajor,
        REACTIVE_PROTOCOL_MIN_COMPATIBLE_MAJOR,
        REACTIVE_PROTOCOL_CURRENT_MAJOR,
    )
    requireToken("deviceId", hello.deviceId)
    requireToken("clientNonce", hello.clientNonce)
    require(hello.clientTimeMillis >= 0) { "clientTimeMillis must not be negative" }
}

fun validateChallenge(challenge: ReactiveChallenge, nowMillis: Long) {
    requireReactiveSchema(challenge.schema)
    require(challenge.selectedMajor == REACTIVE_PROTOCOL_CURRENT_MAJOR) { "unsupported selected protocol major" }
    requireToken("sessionId", challenge.sessionId)
    requireToken("serverNonce", challenge.serverNonce)
    requireToken("macId", challenge.macId)
    validateHelperIdentityPin(challenge.helperIdentity)
    require(challenge.verificationCode.length in 4..12) { "verificationCode length invalid" }
    require(challenge.expiresAtMillis >= nowMillis) { "Helper challenge expired" }
}

fun validateAuthResult(result: ReactiveAuthResult, expectedSessionId: String, nowMillis: Long) {
    requireReactiveSchema(result.schema)
    require(result.sessionId == expectedSessionId) { "Authentication session mismatch" }
    require(result.expiresAtMillis >= nowMillis) { "Authentication result expired" }
    require(result.accepted) { "Authentication rejected: ${result.code.orEmpty()}" }
    require(result.serverProof.isNotBlank()) { "serverProof must not be blank" }
    result.pinnedHelperIdentity?.let(::validateHelperIdentityPin)
}

fun validateRequestEnvelope(envelope: ReactiveRequestEnvelope, nowMillis: Long) {
    requireReactiveSchema(envelope.schema)
    requireToken("sessionId", envelope.sessionId)
    requireToken("requestId", envelope.requestId)
    require(envelope.sequence > 0) { "sequence must be positive" }
    require(envelope.deadlineMillis >= nowMillis) { "request deadline expired" }
    require(envelope.deadlineMillis - nowMillis <= REACTIVE_REPLAY_WINDOW_MILLIS) {
        "request deadline exceeds replay window"
    }
    requireBoundedBody(envelope.bodyJson)
    require(envelope.authTag.isNotBlank()) { "authTag must not be blank" }
}

fun validateResponseEnvelope(envelope: ReactiveResponseEnvelope) {
    requireReactiveSchema(envelope.schema)
    requireToken("sessionId", envelope.sessionId)
    requireToken("requestId", envelope.requestId)
    require(envelope.sequence > 0) { "sequence must be positive" }
    envelope.bodyJson?.let(::requireBoundedBody)
    require(envelope.authTag.isNotBlank()) { "authTag must not be blank" }
}

fun clientProofTranscript(
    hello: ReactiveHello,
    challenge: ReactiveChallenge,
): String = canonicalFields(
    "client-proof",
    hello.schema,
    hello.deviceId,
    hello.clientNonce,
    challenge.serverNonce,
    challenge.sessionId,
    challenge.expiresAtMillis.toString(),
    challenge.macId,
    challenge.helperIdentity.publicKeyFingerprint,
)

fun serverProofTranscript(
    hello: ReactiveHello,
    challenge: ReactiveChallenge,
): String = canonicalFields(
    "server-proof",
    hello.schema,
    hello.deviceId,
    hello.clientNonce,
    challenge.serverNonce,
    challenge.sessionId,
    challenge.expiresAtMillis.toString(),
    challenge.macId,
    challenge.helperIdentity.publicKeyFingerprint,
)

fun requestAuthTranscript(envelope: ReactiveRequestEnvelope): String = canonicalFields(
    "request",
    envelope.schema,
    envelope.sessionId,
    envelope.sequence.toString(),
    envelope.requestId,
    envelope.deadlineMillis.toString(),
    envelope.bodyJson,
)

fun responseAuthTranscript(envelope: ReactiveResponseEnvelope): String = canonicalFields(
    "response",
    envelope.schema,
    envelope.sessionId,
    envelope.sequence.toString(),
    envelope.requestId,
    envelope.status.wireName(),
    envelope.code.orEmpty(),
    envelope.retryable.toString(),
    envelope.bodyJson.orEmpty(),
)

private fun ReceiptStatus.wireName(): String = when (this) {
    ReceiptStatus.Accepted -> "accepted"
    ReceiptStatus.Completed -> "completed"
    ReceiptStatus.PartialFailure -> "partial_failure"
    ReceiptStatus.Timeout -> "timeout"
    ReceiptStatus.Cancelled -> "cancelled"
    ReceiptStatus.Failed -> "failed"
    ReceiptStatus.Denied -> "denied"
}

fun validateHelperIdentityPin(pin: HelperIdentityPin) {
    requireToken("helperId", pin.helperId)
    require(pin.publicKeyFingerprint.length in 32..128) { "publicKeyFingerprint length invalid" }
    require(pin.issuedAtMillis >= 0) { "issuedAtMillis must not be negative" }
    require(pin.trustState != HelperTrustState.Revoked) { "helper identity revoked" }
}

fun validatePairingRecord(record: ReactivePairingRecord) {
    requireToken("deviceId", record.deviceId)
    validateHistoricalHelperIdentityPin(record.helperIdentity)
    requireToken("credentialId", record.credentialId)
    require(record.createdAtMillis >= 0) { "createdAtMillis must not be negative" }
    require(record.lastUsedAtMillis >= record.createdAtMillis) { "lastUsedAtMillis before createdAtMillis" }
    record.revokedAtMillis?.let {
        require(it >= record.createdAtMillis) { "revokedAtMillis before createdAtMillis" }
        require(record.helperIdentity.trustState == HelperTrustState.Revoked) {
            "revokedAtMillis requires revoked helper identity"
        }
    }
}

fun validateHistoricalHelperIdentityPin(pin: HelperIdentityPin) {
    requireToken("helperId", pin.helperId)
    require(pin.publicKeyFingerprint.length in 32..128) { "publicKeyFingerprint length invalid" }
    require(pin.issuedAtMillis >= 0) { "issuedAtMillis must not be negative" }
}

fun validateClockSkew(peerTimeMillis: Long, nowMillis: Long) {
    require(kotlin.math.abs(peerTimeMillis - nowMillis) <= REACTIVE_CLOCK_SKEW_MILLIS) {
        "clock skew exceeds limit"
    }
}

fun validateCapabilities(capabilities: ReactiveHelperCapabilities) {
    requireToken("helperId", capabilities.helperId)
    require(capabilities.protocolMajor == REACTIVE_PROTOCOL_CURRENT_MAJOR) { "unsupported capability protocol major" }
    capabilities.providers.forEach {
        requireToken("providerId", it.providerId)
        require(it.confidenceFloor in 0.0..1.0) { "confidenceFloor out of range" }
    }
}

fun validateBasicState(state: ReactiveHelperBasicState, nowMillis: Long) {
    requireToken("macId", state.macId)
    require(state.snapshotRevision >= 0) { "snapshotRevision must not be negative" }
    require(state.capturedAtMillis >= 0) { "capturedAtMillis must not be negative" }
    require(state.freshnessMillis >= 0) { "freshnessMillis must not be negative" }
    require(nowMillis - state.capturedAtMillis <= state.freshnessMillis || state.stale) {
        "stale state must be marked stale"
    }
    state.displays.forEach {
        requireToken("displayId", it.displayId)
        require(it.name.isNotBlank()) { "display name must not be blank" }
        it.brightnessPercent?.let { brightness ->
            require(brightness in 0..100) { "brightnessPercent out of range" }
        }
    }
}

fun validateStateDelta(delta: ReactiveStateDelta) {
    requireToken("macId", delta.macId)
    require(delta.baseRevision >= 0) { "baseRevision must not be negative" }
    require(delta.newRevision > delta.baseRevision) { "newRevision must advance" }
    require(delta.changes.isNotEmpty()) { "delta must include changes" }
    delta.changes.forEach {
        require(it.path.startsWith("/")) { "state change path must be absolute" }
        require(!(it.removed && it.value != null)) { "removed change cannot include value" }
    }
}

fun validateProviderCandidate(candidate: ProviderCandidate) {
    requireToken("candidateId", candidate.candidateId)
    requireToken("providerId", candidate.providerId)
    require(candidate.actionId.contains(".")) { "actionId must be namespaced" }
    require(candidate.confidence in 0.0..1.0) { "confidence out of range" }
    require(candidate.explanation.isNotBlank()) { "explanation must not be blank" }
    if (candidate.conflicts.isNotEmpty()) {
        require(candidate.policy != PolicyDecision.Allow) { "conflicted candidate cannot be allowed" }
    }
}

fun validateExecuteRequest(request: ReactiveHelperRequest.Execute) {
    require(request.actionId.contains(".")) { "actionId must be namespaced" }
    requireToken("actionRevision", request.actionRevision)
    requireToken("operationId", request.operationId)
    requireToken("idempotencyKey", request.idempotencyKey)
    require(request.timeoutMillis in 1..REACTIVE_REPLAY_WINDOW_MILLIS) { "timeoutMillis out of range" }
    requireToken("cancellationToken", request.cancellationToken)
    require(request.preconditions.distinct().size == request.preconditions.size) {
        "duplicate preconditions"
    }
}

fun validateActionReceipt(receipt: ReactiveHelperActionReceipt) {
    requireToken("receiptId", receipt.receiptId)
    requireToken("operationId", receipt.operationId)
    requireToken("idempotencyKey", receipt.idempotencyKey)
    require(receipt.actionId.contains(".")) { "actionId must be namespaced" }
    requireToken("actionRevision", receipt.actionRevision)
    require(receipt.completedAtMillis >= receipt.startedAtMillis) { "completedAtMillis before startedAtMillis" }
    when (receipt.status) {
        ReceiptStatus.Completed -> require(receipt.partialFailures.isEmpty()) {
            "completed receipt cannot include partial failures"
        }
        ReceiptStatus.PartialFailure -> require(receipt.partialFailures.isNotEmpty()) {
            "partial failure receipt must include partialFailures"
        }
        ReceiptStatus.Timeout -> require(receipt.partialFailures.any { it.code == ReactiveErrorCode.Timeout }) {
            "timeout receipt must include timeout error"
        }
        ReceiptStatus.Cancelled -> require(receipt.partialFailures.any { it.code == ReactiveErrorCode.Cancelled }) {
            "cancelled receipt must include cancellation error"
        }
        ReceiptStatus.Accepted,
        ReceiptStatus.Failed,
        ReceiptStatus.Denied,
        -> Unit
    }
}

fun validateUndoResult(result: ReactiveUndoResult) {
    requireToken("undoToken", result.undoToken)
    require(result.completedAtMillis >= 0) { "completedAtMillis must not be negative" }
    if (result.status == UndoStatus.Failed) {
        require(!result.message.isNullOrBlank()) { "failed undo requires message" }
    }
}

fun validateTransferMetadata(metadata: ReactiveTransferMetadata) {
    requireToken("transferId", metadata.transferId)
    require(metadata.fileName.isNotBlank() && "/" !in metadata.fileName) { "fileName must be local name only" }
    require(metadata.byteCount in 1..REACTIVE_MAX_TRANSFER_BYTES) { "transfer byteCount out of range" }
    require(metadata.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) { "sha256 must be hex sha-256" }
    require(metadata.sourcePath.startsWith("/")) { "sourcePath must be absolute" }
    require(!metadata.sourcePath.contains("..")) { "sourcePath must not traverse" }
}

fun redactedFieldValue(rule: RedactionRule, value: String): String = when (rule.strategy) {
    RedactionStrategy.Drop -> "[redacted]"
    RedactionStrategy.Hash -> "hash-redacted:${value.encodeToByteArray().fold(0) { acc, byte -> acc * 31 + byte }.toUInt().toString(16)}"
    RedactionStrategy.Last4 -> "****${value.takeLast(4)}"
}

/**
 * Per-session replay/deadline gate. Authentication is supplied by the
 * platform crypto adapter; state advances only after authentication succeeds.
 */
class ReactiveReplayGuard(
    private val sessionId: String,
    private val verifyAuth: (ReactiveRequestEnvelope) -> Boolean,
) {
    private val sequenceGuard = StrictSequenceGuard(sessionId)

    fun accept(envelope: ReactiveRequestEnvelope, nowMillis: Long): Boolean {
        validateRequestEnvelope(envelope, nowMillis)
        if (!verifyAuth(envelope)) return false
        return sequenceGuard.accept(envelope.sessionId, envelope.sequence, envelope.requestId)
    }

    fun lastAcceptedSequence(): Long = sequenceGuard.lastAcceptedSequence()
}

class ReactiveResponseReplayGuard(
    private val sessionId: String,
    private val verifyAuth: (ReactiveResponseEnvelope) -> Boolean,
) {
    private val sequenceGuard = StrictSequenceGuard(sessionId)

    fun accept(envelope: ReactiveResponseEnvelope): Boolean {
        validateResponseEnvelope(envelope)
        if (!verifyAuth(envelope)) return false
        return sequenceGuard.accept(envelope.sessionId, envelope.sequence, envelope.requestId)
    }

    fun lastAcceptedSequence(): Long = sequenceGuard.lastAcceptedSequence()
}

object ReactiveFrameCodec {
    fun encode(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "frame payload must not be empty" }
        require(payload.size <= REACTIVE_MAX_BODY_BYTES) { "frame payload exceeds limit" }
        val size = payload.size
        return byteArrayOf(
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        ) + payload
    }

    fun decode(frame: ByteArray): ByteArray {
        require(frame.size >= 4) { "frame header incomplete" }
        val size = ((frame[0].toInt() and 0xff) shl 24) or
            ((frame[1].toInt() and 0xff) shl 16) or
            ((frame[2].toInt() and 0xff) shl 8) or
            (frame[3].toInt() and 0xff)
        require(size in 1..REACTIVE_MAX_BODY_BYTES) { "invalid frame length" }
        require(frame.size == size + 4) { "frame length mismatch" }
        return frame.copyOfRange(4, frame.size)
    }
}

private class StrictSequenceGuard(
    private val sessionId: String,
) {
    private var highestSequence = 0L
    private val completedRequests = linkedSetOf<String>()

    fun accept(candidateSessionId: String, sequence: Long, requestId: String): Boolean {
        if (candidateSessionId != sessionId) return false
        if (sequence != highestSequence + 1) return false
        if (requestId in completedRequests) return false
        highestSequence = sequence
        completedRequests += requestId
        while (completedRequests.size > REACTIVE_REPLAY_CACHE_SIZE) {
            completedRequests.remove(completedRequests.first())
        }
        return true
    }

    fun lastAcceptedSequence(): Long = highestSequence
}

private fun canonicalFields(vararg values: String): String = values.joinToString("\u0000")

private fun requireToken(name: String, value: String) {
    require(value.isNotBlank()) { "$name must not be blank" }
    require(value.encodeToByteArray().size <= REACTIVE_MAX_TOKEN_BYTES) {
        "$name exceeds $REACTIVE_MAX_TOKEN_BYTES bytes"
    }
}

private fun requireBoundedBody(value: String) {
    require(value.isNotBlank()) { "body must not be blank" }
    require(value.encodeToByteArray().size <= REACTIVE_MAX_BODY_BYTES) {
        "body exceeds $REACTIVE_MAX_BODY_BYTES bytes"
    }
}
