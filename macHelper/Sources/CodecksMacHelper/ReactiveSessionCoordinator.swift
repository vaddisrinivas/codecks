import Foundation

public final class ReactiveSessionCoordinator {
    private let macId: String
    private let helperIdentity: HelperIdentityPin
    private let pairingStore: PairingStore
    private let supportedActionIds: Set<String>
    private let jsonEncoder = JSONEncoder()
    private let jsonDecoder = JSONDecoder()
    private var pending: [String: PendingSession] = [:]
    private var sessions: [String: AuthenticatedSession] = [:]

    public init(macId: String, helperIdentity: HelperIdentityPin, pairingStore: PairingStore, supportedActionIds: Set<String> = []) {
        self.macId = macId
        self.helperIdentity = helperIdentity
        self.pairingStore = pairingStore
        self.supportedActionIds = supportedActionIds
        jsonEncoder.outputFormatting = [.sortedKeys]
    }

    public func handleHello(_ hello: ReactiveHello, nowMillis: Int64 = nowMillis()) throws -> ReactiveChallenge {
        try validateHello(hello)
        if let record = try pairingStore.load(deviceId: hello.deviceId), record.isRevoked {
            throw ReactiveValidationError("helper identity revoked")
        }
        try helperIdentity.validate()
        let challenge = ReactiveChallenge(
            schema: ReactiveConstants.schema,
            selectedMajor: ReactiveConstants.currentMajor,
            sessionId: "session-\(UUID().uuidString)",
            serverNonce: "server-\(UUID().uuidString)",
            expiresAtMillis: nowMillis + ReactiveConstants.replayWindowMillis,
            macId: macId,
            helperIdentity: helperIdentity,
            verificationCode: String(Int.random(in: 100_000...999_999))
        )
        pending[challenge.sessionId] = PendingSession(hello: hello, challenge: challenge)
        return challenge
    }

    public func handleProof(_ proof: ReactiveProof, secret: Data, nowMillis: Int64 = nowMillis()) throws -> ReactiveAuthResult {
        guard let pendingSession = pending[proof.sessionId] else {
            return rejected(sessionId: proof.sessionId, code: ReactiveErrorCode.authenticationRequired.rawValue, nowMillis: nowMillis)
        }
        guard pendingSession.challenge.expiresAtMillis >= nowMillis else {
            pending.removeValue(forKey: proof.sessionId)
            return rejected(sessionId: proof.sessionId, code: ReactiveErrorCode.clockSkew.rawValue, nowMillis: nowMillis)
        }
        if let record = try pairingStore.load(deviceId: pendingSession.hello.deviceId), record.isRevoked {
            pending.removeValue(forKey: proof.sessionId)
            return rejected(sessionId: proof.sessionId, code: ReactiveErrorCode.pinMismatch.rawValue, nowMillis: nowMillis)
        }

        let auth = ReactiveAuthenticator(secret: secret)
        let expectedProof = auth.hmacHex(clientProofTranscript(hello: pendingSession.hello, challenge: pendingSession.challenge))
        let expectedPin = auth.hmacHex(pendingSession.challenge.helperIdentity.publicKeyFingerprint)
        guard constantTimeEquals(proof.proof, expectedProof), constantTimeEquals(proof.pinAcknowledgement, expectedPin) else {
            return rejected(sessionId: proof.sessionId, code: ReactiveErrorCode.pinMismatch.rawValue, nowMillis: nowMillis)
        }

        let record = ReactivePairingRecord(
            deviceId: pendingSession.hello.deviceId,
            helperIdentity: helperIdentity,
            credentialId: "hmac-local-pairing",
            createdAtMillis: nowMillis,
            lastUsedAtMillis: nowMillis
        )
        try pairingStore.save(record)
        pending.removeValue(forKey: proof.sessionId)
        sessions[proof.sessionId] = AuthenticatedSession(secret: secret, expiresAtMillis: nowMillis + ReactiveConstants.replayWindowMillis)
        return ReactiveAuthResult(
            schema: ReactiveConstants.schema,
            sessionId: proof.sessionId,
            accepted: true,
            expiresAtMillis: nowMillis + ReactiveConstants.replayWindowMillis,
            serverProof: auth.hmacHex(serverProofTranscript(hello: pendingSession.hello, challenge: pendingSession.challenge)),
            code: nil,
            pinnedHelperIdentity: helperIdentity
        )
    }

    public func handleRequest(_ envelope: ReactiveRequestEnvelope, nowMillis: Int64 = nowMillis()) throws -> ReactiveResponseEnvelope {
        guard var session = sessions[envelope.sessionId], session.expiresAtMillis >= nowMillis else {
            return response(for: envelope, status: .denied, code: ReactiveErrorCode.authenticationRequired.rawValue, secret: Data())
        }
        try validateRequest(envelope, nowMillis: nowMillis)
        let auth = ReactiveAuthenticator(secret: session.secret)
        guard auth.verify(expectedHex: envelope.authTag, transcript: requestAuthTranscript(envelope)) else {
            return response(for: envelope, status: .denied, code: ReactiveErrorCode.authenticationRequired.rawValue, secret: session.secret)
        }
        guard session.accept(sequence: envelope.sequence, requestId: envelope.requestId) else {
            sessions[envelope.sessionId] = session
            return response(for: envelope, status: .denied, code: ReactiveErrorCode.replayDetected.rawValue, secret: session.secret)
        }
        sessions[envelope.sessionId] = session

        let body = try jsonDecoder.decode(HelperRequestBody.self, from: Data(envelope.bodyJson.utf8))
        switch body.type {
        case "ping":
            return response(for: envelope, status: .completed, bodyJson: "{\"type\":\"pong\"}", secret: session.secret)
        case "capabilities":
            let capabilities = ReactiveHelperCapabilities(
                helperId: helperIdentity.helperId,
                protocolMajor: ReactiveConstants.currentMajor,
                values: ["state.front_app", "action.execute"]
            )
            return try encodedResponse(for: envelope, status: .completed, body: capabilities, secret: session.secret)
        case "basic_state":
            let state = ReactiveHelperBasicState(
                macId: macId,
                snapshotRevision: 0,
                capturedAtMillis: nowMillis,
                freshnessMillis: 5_000,
                provenance: .helper,
                frontAppBundleId: nil,
                frontAppName: nil,
                capabilities: ["state.front_app", "action.execute"],
                stale: false
            )
            return try encodedResponse(for: envelope, status: .completed, body: state, secret: session.secret)
        case "execute":
            guard let actionId = body.actionId, supportedActionIds.contains(actionId) else {
                return response(for: envelope, status: .denied, code: ReactiveErrorCode.unsupportedCapability.rawValue, secret: session.secret)
            }
            return response(for: envelope, status: .denied, code: ReactiveErrorCode.preconditionFailed.rawValue, secret: session.secret)
        default:
            return response(for: envelope, status: .denied, code: ReactiveErrorCode.unsupportedCapability.rawValue, secret: session.secret)
        }
    }

    private func rejected(sessionId: String, code: String, nowMillis: Int64) -> ReactiveAuthResult {
        ReactiveAuthResult(
            schema: ReactiveConstants.schema,
            sessionId: sessionId,
            accepted: false,
            expiresAtMillis: nowMillis,
            serverProof: "",
            code: code,
            pinnedHelperIdentity: nil
        )
    }

    private func validateHello(_ hello: ReactiveHello) throws {
        guard hello.schema == ReactiveConstants.schema else { throw ReactiveValidationError("Unsupported Reactive protocol schema: \(hello.schema)") }
        guard hello.minMajor > 0, hello.maxMajor >= hello.minMajor, min(hello.maxMajor, ReactiveConstants.currentMajor) >= max(hello.minMajor, ReactiveConstants.minMajor) else {
            throw ReactiveValidationError("unsupported protocol compatibility window")
        }
        try requireToken("deviceId", hello.deviceId)
        try requireToken("clientNonce", hello.clientNonce)
        guard hello.clientTimeMillis >= 0 else { throw ReactiveValidationError("clientTimeMillis must not be negative") }
    }

    private func validateRequest(_ envelope: ReactiveRequestEnvelope, nowMillis: Int64) throws {
        guard envelope.schema == ReactiveConstants.schema else { throw ReactiveValidationError("Unsupported Reactive protocol schema: \(envelope.schema)") }
        try requireToken("sessionId", envelope.sessionId)
        try requireToken("requestId", envelope.requestId)
        guard envelope.sequence > 0 else { throw ReactiveValidationError("sequence must be positive") }
        guard envelope.deadlineMillis >= nowMillis else { throw ReactiveValidationError("request deadline expired") }
        guard envelope.deadlineMillis - nowMillis <= ReactiveConstants.replayWindowMillis else {
            throw ReactiveValidationError("request deadline exceeds replay window")
        }
        guard !envelope.bodyJson.isEmpty, Data(envelope.bodyJson.utf8).count <= ReactiveConstants.maxBodyBytes else {
            throw ReactiveValidationError("body exceeds limit")
        }
        guard !envelope.authTag.isEmpty else { throw ReactiveValidationError("authTag must not be blank") }
    }

    private func encodedResponse<T: Encodable>(for envelope: ReactiveRequestEnvelope, status: ReceiptStatus, body: T, secret: Data) throws -> ReactiveResponseEnvelope {
        let bodyJson = String(decoding: try jsonEncoder.encode(body), as: UTF8.self)
        return response(for: envelope, status: status, bodyJson: bodyJson, secret: secret)
    }

    private func response(for envelope: ReactiveRequestEnvelope, status: ReceiptStatus, code: String? = nil, bodyJson: String? = nil, secret: Data) -> ReactiveResponseEnvelope {
        var response = ReactiveResponseEnvelope(
            sessionId: envelope.sessionId,
            sequence: envelope.sequence,
            requestId: envelope.requestId,
            status: status,
            code: code,
            retryable: false,
            bodyJson: bodyJson,
            authTag: ""
        )
        if !secret.isEmpty {
            response.authTag = ReactiveAuthenticator(secret: secret).hmacHex(responseAuthTranscript(response))
        } else {
            response.authTag = "unauthenticated"
        }
        return response
    }
}

private struct PendingSession {
    var hello: ReactiveHello
    var challenge: ReactiveChallenge
}

private struct AuthenticatedSession {
    var secret: Data
    var expiresAtMillis: Int64
    private var highestSequence: Int64 = 0
    private var completedRequests: Set<String> = []

    init(secret: Data, expiresAtMillis: Int64) {
        self.secret = secret
        self.expiresAtMillis = expiresAtMillis
    }

    mutating func accept(sequence: Int64, requestId: String) -> Bool {
        guard sequence == highestSequence + 1, !completedRequests.contains(requestId) else {
            return false
        }
        highestSequence = sequence
        completedRequests.insert(requestId)
        return true
    }
}
