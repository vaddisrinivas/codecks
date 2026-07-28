package io.codecks.shared.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ReactiveProtocolTest {
    @Test
    fun requestRejectsInvalidSequenceExpiredDeadlineAndOversizedBody() {
        assertFailsWith<IllegalArgumentException> {
            validateRequestEnvelope(request(sequence = 0), nowMillis = 50)
        }
        assertFailsWith<IllegalArgumentException> {
            validateRequestEnvelope(request(deadlineMillis = 49), nowMillis = 50)
        }
        assertFailsWith<IllegalArgumentException> {
            validateRequestEnvelope(
                request(bodyJson = "x".repeat(REACTIVE_MAX_BODY_BYTES + 1)),
                nowMillis = 50,
            )
        }
    }

    @Test
    fun proofTranscriptsAreRoleSeparatedAndDeterministic() {
        val hello = ReactiveHello(
            deviceId = "phone",
            clientNonce = "client-nonce",
            clientTimeMillis = 1,
        )
        val challenge = ReactiveChallenge(
            sessionId = "session",
            serverNonce = "server-nonce",
            expiresAtMillis = 100,
            macId = "mac",
            helperIdentity = helperPin(),
            verificationCode = "123456",
        )

        assertEquals(clientProofTranscript(hello, challenge), clientProofTranscript(hello, challenge))
        assertTrue(clientProofTranscript(hello, challenge) != serverProofTranscript(hello, challenge))
        assertTrue(
            clientProofTranscript(hello, challenge) !=
                clientProofTranscript(
                    hello,
                    challenge.copy(
                        helperIdentity = helperPin().copy(publicKeyFingerprint = "b".repeat(64)),
                    ),
                ),
        )
    }

    @Test
    fun frameCodecRoundTripsAndRejectsPartialOrOversizedFrames() {
        val payload = """{"type":"ping"}""".encodeToByteArray()
        assertContentEquals(payload, ReactiveFrameCodec.decode(ReactiveFrameCodec.encode(payload)))
        assertFailsWith<IllegalArgumentException> {
            ReactiveFrameCodec.decode(byteArrayOf(0, 0, 0, 2, 1))
        }
        assertFailsWith<IllegalArgumentException> {
            ReactiveFrameCodec.encode(ByteArray(REACTIVE_MAX_BODY_BYTES + 1))
        }
    }

    @Test
    fun authenticationResultRequiresMatchingLiveSession() {
        val result = ReactiveAuthResult(
            sessionId = "session",
            accepted = true,
            expiresAtMillis = 100,
            serverProof = "proof",
        )
        validateAuthResult(result, expectedSessionId = "session", nowMillis = 50)
        assertFailsWith<IllegalArgumentException> {
            validateAuthResult(result, expectedSessionId = "other", nowMillis = 50)
        }
        assertFailsWith<IllegalArgumentException> {
            validateAuthResult(result, expectedSessionId = "session", nowMillis = 101)
        }
        assertFailsWith<IllegalArgumentException> {
            validateAuthResult(
                result.copy(accepted = false, code = "pin_mismatch"),
                expectedSessionId = "session",
                nowMillis = 50,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            requireReactiveSchema("reactive.v2")
        }
    }

    @Test
    fun typedHelperRequestRoundTripsWithExplicitDiscriminator() {
        val json = Json {
            classDiscriminator = "type"
            encodeDefaults = true
        }
        val request: ReactiveHelperRequest = ReactiveHelperRequest.Execute(
            actionId = "reload",
            actionRevision = "rev-1",
            operationId = "op-1",
            idempotencyKey = "idem-1",
            timeoutMillis = 5000,
            cancellationToken = "cancel-1",
            preconditions = listOf(
                ActionPrecondition(
                    kind = ActionPreconditionKind.FrontAppBundle,
                    expectedBundleId = "com.apple.finder",
                ),
            ),
            arguments = mapOf("target" to "current"),
        )
        val encoded = json.encodeToString(ReactiveHelperRequest.serializer(), request)
        val decoded = json.decodeFromString(ReactiveHelperRequest.serializer(), encoded)

        assertEquals(request, decoded)
        assertTrue(encoded.contains("\"type\":\"execute\""))
        assertFalse(encoded.contains("ReactiveHelperRequest.Execute"))
    }

    @Test
    fun protocolNegotiationRequiresOverlap() {
        assertEquals(1, negotiateProtocolMajor(clientMin = 1, clientMax = 1, helperMin = 1, helperMax = 1))
        assertFailsWith<IllegalArgumentException> {
            negotiateProtocolMajor(clientMin = 0, clientMax = 0, helperMin = 1, helperMax = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            negotiateProtocolMajor(clientMin = 2, clientMax = 2, helperMin = 1, helperMax = 1)
        }
    }

    @Test
    fun pairingPinRevocationAndClockSkewAreRejectedEarly() {
        validateHelperIdentityPin(helperPin())
        validatePairingRecord(
            ReactivePairingRecord(
                deviceId = "phone",
                helperIdentity = helperPin().copy(trustState = HelperTrustState.Revoked),
                credentialId = "credential",
                createdAtMillis = 1,
                lastUsedAtMillis = 2,
                revokedAtMillis = 3,
            ),
        )
        validateClockSkew(peerTimeMillis = 1000, nowMillis = 1000 + REACTIVE_CLOCK_SKEW_MILLIS)

        assertFailsWith<IllegalArgumentException> {
            validateHelperIdentityPin(helperPin().copy(trustState = HelperTrustState.Revoked))
        }
        assertFailsWith<IllegalArgumentException> {
            validateClockSkew(peerTimeMillis = 0, nowMillis = REACTIVE_CLOCK_SKEW_MILLIS + 1)
        }
    }

    @Test
    fun capabilitiesStateDeltaProviderAndTransferRejectInvalidShapes() {
        validateCapabilities(
            ReactiveHelperCapabilities(
                helperId = "helper",
                protocolMajor = 1,
                values = setOf(ReactiveCapabilityId.ActionExecute),
                providers = listOf(
                    ProviderCapability(
                        providerId = "finder",
                        capabilities = setOf(ReactiveCapabilityId.FrontAppState),
                        confidenceFloor = 0.25,
                        requiresConfirmation = false,
                    ),
                ),
            ),
        )
        validateBasicState(
            ReactiveHelperBasicState(
                macId = "mac",
                snapshotRevision = 1,
                capturedAtMillis = 100,
                freshnessMillis = 100,
                provenance = StateProvenance.Helper,
                frontAppBundleId = "com.apple.finder",
            ),
            nowMillis = 200,
        )
        validateStateDelta(
            ReactiveStateDelta(
                macId = "mac",
                baseRevision = 1,
                newRevision = 2,
                capturedAtMillis = 100,
                provenance = StateProvenance.Helper,
                changes = listOf(StateChange(path = "/frontAppBundleId")),
            ),
        )
        validateProviderCandidate(
            ProviderCandidate(
                candidateId = "candidate",
                providerId = "finder",
                actionId = "macos.finder.reveal",
                confidence = 0.9,
                explanation = "Finder is frontmost.",
                policy = PolicyDecision.Allow,
            ),
        )
        validateTransferMetadata(
            ReactiveTransferMetadata(
                transferId = "transfer",
                fileName = "report.pdf",
                byteCount = 42,
                sha256 = "a".repeat(64),
                sourcePath = "/Users/example/report.pdf",
                destinationHint = "Downloads",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            validateBasicState(
                ReactiveHelperBasicState(
                    macId = "mac",
                    snapshotRevision = 1,
                    capturedAtMillis = 0,
                    freshnessMillis = 1,
                    provenance = StateProvenance.Helper,
                ),
                nowMillis = 100,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateStateDelta(
                ReactiveStateDelta(
                    macId = "mac",
                    baseRevision = 2,
                    newRevision = 2,
                    capturedAtMillis = 100,
                    provenance = StateProvenance.Helper,
                    changes = listOf(StateChange(path = "/x")),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateProviderCandidate(
                ProviderCandidate(
                    candidateId = "candidate",
                    providerId = "finder",
                    actionId = "macos.finder.reveal",
                    confidence = 0.9,
                    explanation = "Conflict.",
                    policy = PolicyDecision.Allow,
                    conflicts = listOf(
                        ProviderConflict(
                            otherCandidateId = "other",
                            reason = ConflictReason.SameGesture,
                            message = "Same gesture.",
                        ),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateTransferMetadata(
                ReactiveTransferMetadata(
                    transferId = "transfer",
                    fileName = "../report.pdf",
                    byteCount = REACTIVE_MAX_TRANSFER_BYTES + 1,
                    sha256 = "bad",
                    sourcePath = "../report.pdf",
                    destinationHint = "Downloads",
                ),
            )
        }
    }

    @Test
    fun executionReceiptUndoAndRedactionContractsAreStrict() {
        val execute = ReactiveHelperRequest.Execute(
            actionId = "macos.finder.reveal",
            actionRevision = "rev-1",
            operationId = "op-1",
            idempotencyKey = "idem-1",
            timeoutMillis = 1000,
            cancellationToken = "cancel-1",
        )
        validateExecuteRequest(execute)
        validateActionReceipt(
            ReactiveHelperActionReceipt(
                receiptId = "receipt",
                operationId = "op-1",
                idempotencyKey = "idem-1",
                actionId = "macos.finder.reveal",
                actionRevision = "rev-1",
                status = ReceiptStatus.Completed,
                startedAtMillis = 1,
                completedAtMillis = 2,
                resultCode = "ok",
                undoToken = "undo-1",
            ),
        )
        validateUndoResult(
            ReactiveUndoResult(
                undoToken = "undo-1",
                status = UndoStatus.Completed,
                completedAtMillis = 3,
            ),
        )
        assertEquals("****3456", redactedFieldValue(RedactionRule("/credential", RedactionStrategy.Last4), "123456"))
        assertTrue(redactedFieldValue(RedactionRule("/credential", RedactionStrategy.Hash), "123456").startsWith("hash-redacted:"))

        assertFailsWith<IllegalArgumentException> {
            validateActionReceipt(
                ReactiveHelperActionReceipt(
                    receiptId = "receipt",
                    operationId = "op-1",
                    idempotencyKey = "idem-1",
                    actionId = "macos.finder.reveal",
                    actionRevision = "rev-1",
                    status = ReceiptStatus.Completed,
                    startedAtMillis = 2,
                    completedAtMillis = 1,
                    resultCode = "bad",
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateExecuteRequest(execute.copy(timeoutMillis = REACTIVE_REPLAY_WINDOW_MILLIS + 1))
        }
    }

    @Test
    fun partialTimeoutCancellationAndUndoContractsAreExplicit() {
        validateActionReceipt(
            receipt(
                status = ReceiptStatus.PartialFailure,
                errors = listOf(ReactiveError(ReactiveErrorCode.PartialFailure, "One step failed.")),
            ),
        )
        validateActionReceipt(
            receipt(
                status = ReceiptStatus.Timeout,
                errors = listOf(ReactiveError(ReactiveErrorCode.Timeout, "Timed out.", retryable = true)),
            ),
        )
        validateActionReceipt(
            receipt(
                status = ReceiptStatus.Cancelled,
                errors = listOf(ReactiveError(ReactiveErrorCode.Cancelled, "Cancelled.")),
            ),
        )
        validateUndoResult(
            ReactiveUndoResult(
                undoToken = "undo-1",
                status = UndoStatus.Failed,
                completedAtMillis = 10,
                message = "Original operation already superseded.",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            validateActionReceipt(receipt(status = ReceiptStatus.PartialFailure, errors = emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            validateActionReceipt(
                receipt(
                    status = ReceiptStatus.Timeout,
                    errors = listOf(ReactiveError(ReactiveErrorCode.Internal, "Wrong category.")),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateUndoResult(
                ReactiveUndoResult(
                    undoToken = "undo-1",
                    status = UndoStatus.Failed,
                    completedAtMillis = 10,
                ),
            )
        }
    }

    private fun request(
        sequence: Long = 1,
        deadlineMillis: Long = 100,
        bodyJson: String = "{}",
    ) = ReactiveRequestEnvelope(
        sessionId = "session",
        sequence = sequence,
        requestId = "request",
        deadlineMillis = deadlineMillis,
        bodyJson = bodyJson,
        authTag = "tag",
    )

    private fun helperPin() = HelperIdentityPin(
        helperId = "helper",
        publicKeyFingerprint = "a".repeat(64),
        issuedAtMillis = 1,
        trustState = HelperTrustState.Verified,
    )

    private fun receipt(
        status: ReceiptStatus,
        errors: List<ReactiveError>,
    ) = ReactiveHelperActionReceipt(
        receiptId = "receipt",
        operationId = "op-1",
        idempotencyKey = "idem-1",
        actionId = "macos.finder.reveal",
        actionRevision = "rev-1",
        status = status,
        startedAtMillis = 1,
        completedAtMillis = 2,
        resultCode = status.name,
        partialFailures = errors,
    )
}
