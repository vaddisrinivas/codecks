import XCTest
@testable import CodecksMacHelper

final class ReactiveMacHelperTests: XCTestCase {
    private let secret = Data("fixture-secret".utf8)
    private let identity = HelperIdentityPin(
        helperId: "mac-helper-fixture",
        publicKeyFingerprint: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        issuedAtMillis: 1_000,
        trustState: .verified
    )

    func testFrameCodecRoundTripAndRejectsMismatch() throws {
        let payload = Data(#"{"schema":"reactive.v1"}"#.utf8)
        let frame = try ReactiveFrameCodec.encode(payload)
        XCTAssertEqual(try ReactiveFrameCodec.decode(frame), payload)
        XCTAssertThrowsError(try ReactiveFrameCodec.decode(frame.dropLast()))
    }

    func testProofMismatchRejected() throws {
        let coordinator = makeCoordinator()
        let hello = ReactiveHello(deviceId: "phone-fixture", clientNonce: "client-nonce-fixture", clientTimeMillis: 1_000)
        let challenge = try coordinator.handleHello(hello, nowMillis: 1_000)
        let result = try coordinator.handleProof(
            ReactiveProof(sessionId: challenge.sessionId, proof: "bad", pinAcknowledgement: "also-bad"),
            secret: secret,
            nowMillis: 1_100
        )
        XCTAssertFalse(result.accepted)
        XCTAssertEqual(result.code, ReactiveErrorCode.pinMismatch.rawValue)
    }

    func testReplayRejectedAfterFirstAcceptedRequest() throws {
        let coordinator = makeCoordinator()
        let session = try authenticate(coordinator)
        let request = signedRequest(sessionId: session.sessionId, sequence: 1, requestId: "request-1", bodyJson: #"{"type":"basic_state"}"#)
        let first = try coordinator.handleRequest(request, nowMillis: 2_000)
        XCTAssertEqual(first.status, .completed)

        let replay = try coordinator.handleRequest(request, nowMillis: 2_000)
        XCTAssertEqual(replay.status, .denied)
        XCTAssertEqual(replay.code, ReactiveErrorCode.replayDetected.rawValue)
    }

    func testRevokedIdentityBlocksPairing() throws {
        var revoked = identity
        revoked.trustState = .revoked
        let record = ReactivePairingRecord(
            deviceId: "phone-fixture",
            helperIdentity: revoked,
            credentialId: "credential",
            createdAtMillis: 1_000,
            lastUsedAtMillis: 1_000,
            revokedAtMillis: 1_100
        )
        let coordinator = ReactiveSessionCoordinator(
            macId: "mac-fixture",
            helperIdentity: identity,
            pairingStore: InMemoryPairingStore(records: [record])
        )

        XCTAssertThrowsError(
            try coordinator.handleHello(ReactiveHello(deviceId: "phone-fixture", clientNonce: "nonce", clientTimeMillis: 1_000), nowMillis: 2_000)
        )
    }

    func testUnsupportedActionDenied() throws {
        let coordinator = makeCoordinator()
        let session = try authenticate(coordinator)
        let request = signedRequest(
            sessionId: session.sessionId,
            sequence: 1,
            requestId: "request-execute",
            bodyJson: #"{"type":"execute","actionId":"macos.finder.reveal"}"#
        )
        let response = try coordinator.handleRequest(request, nowMillis: 2_000)
        XCTAssertEqual(response.status, .denied)
        XCTAssertEqual(response.code, ReactiveErrorCode.unsupportedCapability.rawValue)
    }

    private func makeCoordinator() -> ReactiveSessionCoordinator {
        ReactiveSessionCoordinator(
            macId: "mac-fixture",
            helperIdentity: identity,
            pairingStore: InMemoryPairingStore()
        )
    }

    private func authenticate(_ coordinator: ReactiveSessionCoordinator) throws -> ReactiveAuthResult {
        let hello = ReactiveHello(deviceId: "phone-fixture", clientNonce: "client-nonce-fixture", clientTimeMillis: 1_000)
        let challenge = try coordinator.handleHello(hello, nowMillis: 1_000)
        let auth = ReactiveAuthenticator(secret: secret)
        let proof = ReactiveProof(
            sessionId: challenge.sessionId,
            proof: auth.hmacHex(clientProofTranscript(hello: hello, challenge: challenge)),
            pinAcknowledgement: auth.hmacHex(challenge.helperIdentity.publicKeyFingerprint)
        )
        let result = try coordinator.handleProof(proof, secret: secret, nowMillis: 1_100)
        XCTAssertTrue(result.accepted)
        return result
    }

    private func signedRequest(sessionId: String, sequence: Int64, requestId: String, bodyJson: String) -> ReactiveRequestEnvelope {
        var request = ReactiveRequestEnvelope(
            sessionId: sessionId,
            sequence: sequence,
            requestId: requestId,
            deadlineMillis: 3_000,
            bodyJson: bodyJson,
            authTag: ""
        )
        request.authTag = ReactiveAuthenticator(secret: secret).hmacHex(requestAuthTranscript(request))
        return request
    }
}
