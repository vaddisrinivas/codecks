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
            bodyJson: #"{"type":"execute","actionId":"macos.finder.reveal","actionRevision":"rev-1","operationId":"op-1","idempotencyKey":"idem-1","timeoutMillis":5000,"cancellationToken":"op-1","arguments":{}}"#
        )
        let response = try coordinator.handleRequest(request, nowMillis: 2_000)
        XCTAssertEqual(response.status, .denied)
        XCTAssertEqual(response.code, ReactiveErrorCode.unsupportedCapability.rawValue)
    }

    func testExecuteHandlerReturnsTypedReceipt() throws {
        let coordinator = ReactiveSessionCoordinator(
            macId: "mac-fixture",
            helperIdentity: identity,
            pairingStore: InMemoryPairingStore(),
            actionHandlers: [
                "apple_shortcuts.run": ClosureReactiveHelperActionHandler { request, _ in
                    XCTAssertEqual(request.arguments["shortcutName"], "Daily Standup")
                    return .completed(resultCode: "shortcut_completed", undoToken: "undo-1")
                }
            ]
        )
        let session = try authenticate(coordinator)
        let request = signedRequest(
            sessionId: session.sessionId,
            sequence: 1,
            requestId: "request-execute",
            bodyJson: #"""
            {"type":"execute","actionId":"apple_shortcuts.run","actionRevision":"rev-1","operationId":"op-1","idempotencyKey":"idem-1","timeoutMillis":5000,"cancellationToken":"op-1","arguments":{"shortcutName":"Daily Standup"}}
            """#
        )

        let response = try coordinator.handleRequest(request, nowMillis: 2_000)

        XCTAssertEqual(response.status, .completed)
        let receipt = try JSONDecoder().decode(ReactiveHelperActionReceipt.self, from: Data(response.bodyJson!.utf8))
        XCTAssertEqual(receipt.actionId, "apple_shortcuts.run")
        XCTAssertEqual(receipt.operationId, "op-1")
        XCTAssertEqual(receipt.resultCode, "shortcut_completed")
        XCTAssertEqual(receipt.undoToken, "undo-1")
    }

    func testCancelAndUndoAreExplicitlyDeniedStubs() throws {
        let coordinator = makeCoordinator()
        let session = try authenticate(coordinator)

        let cancel = try coordinator.handleRequest(
            signedRequest(
                sessionId: session.sessionId,
                sequence: 1,
                requestId: "request-cancel",
                bodyJson: #"{"type":"cancel","operationId":"op-1","cancellationToken":"op-1"}"#
            ),
            nowMillis: 2_000
        )
        let undo = try coordinator.handleRequest(
            signedRequest(
                sessionId: session.sessionId,
                sequence: 2,
                requestId: "request-undo",
                bodyJson: #"{"type":"undo","undoToken":"undo-1","reason":"test"}"#
            ),
            nowMillis: 2_000
        )

        XCTAssertEqual(cancel.status, .denied)
        XCTAssertEqual(cancel.code, ReactiveErrorCode.cancelled.rawValue)
        XCTAssertEqual(undo.status, .denied)
        XCTAssertEqual(undo.code, ReactiveErrorCode.unsupportedCapability.rawValue)
    }

    func testFramedTransportRoutesHelloProofAndExecute() throws {
        let coordinator = ReactiveSessionCoordinator(
            macId: "mac-fixture",
            helperIdentity: identity,
            pairingStore: InMemoryPairingStore(),
            actionHandlers: [
                "apple_shortcuts.run": ClosureReactiveHelperActionHandler { request, _ in
                    XCTAssertEqual(request.operationId, "op-transport")
                    return .completed(resultCode: "transport_completed")
                }
            ]
        )
        let transport = ReactiveFramedTransportService(coordinator: coordinator, secret: secret)
        let hello = ReactiveHello(deviceId: "phone-fixture", clientNonce: "client-nonce-fixture", clientTimeMillis: 1_000)
        let challenge: ReactiveChallenge = try framedRoundTrip(hello, through: transport, nowMillis: 1_000)
        XCTAssertEqual(challenge.macId, "mac-fixture")

        let auth = ReactiveAuthenticator(secret: secret)
        let proof = ReactiveProof(
            sessionId: challenge.sessionId,
            proof: auth.hmacHex(clientProofTranscript(hello: hello, challenge: challenge)),
            pinAcknowledgement: auth.hmacHex(pinAcknowledgementTranscript(challenge: challenge))
        )
        let authResult: ReactiveAuthResult = try framedRoundTrip(proof, through: transport, nowMillis: 1_100)
        XCTAssertTrue(authResult.accepted)
        XCTAssertEqual(authResult.pinnedHelperIdentity, identity)

        let request = signedRequest(
            sessionId: authResult.sessionId,
            sequence: 1,
            requestId: "request-transport",
            bodyJson: #"{"type":"execute","actionId":"apple_shortcuts.run","actionRevision":"rev-1","operationId":"op-transport","idempotencyKey":"idem-transport","timeoutMillis":5000,"cancellationToken":"op-transport","arguments":{"shortcutName":"Daily Standup"}}"#
        )
        let response: ReactiveResponseEnvelope = try framedRoundTrip(request, through: transport, nowMillis: 2_000)

        XCTAssertEqual(response.status, .completed)
        let receipt = try JSONDecoder().decode(ReactiveHelperActionReceipt.self, from: Data(response.bodyJson!.utf8))
        XCTAssertEqual(receipt.operationId, "op-transport")
        XCTAssertEqual(receipt.resultCode, "transport_completed")
    }

    func testFramedTransportRejectsBadFramesAndUnknownPayloads() throws {
        let transport = ReactiveFramedTransportService(coordinator: makeCoordinator(), secret: secret)

        XCTAssertThrowsError(try transport.handleFrame(Data([0, 0, 0, 8, 1, 2, 3]), nowMillis: 1_000))
        XCTAssertThrowsError(
            try transport.handleFrame(
                ReactiveFrameCodec.encode(Data(#"{"schema":"reactive.v1","nope":true}"#.utf8)),
                nowMillis: 1_000
            )
        )
    }

    func testFramedTransportRejectsReplayAtFrameBoundary() throws {
        let coordinator = makeCoordinator()
        let transport = ReactiveFramedTransportService(coordinator: coordinator, secret: secret)
        let session = try authenticate(coordinator)
        let request = signedRequest(sessionId: session.sessionId, sequence: 1, requestId: "request-replay-frame", bodyJson: #"{"type":"basic_state"}"#)

        let first: ReactiveResponseEnvelope = try framedRoundTrip(request, through: transport, nowMillis: 2_000)
        let replay: ReactiveResponseEnvelope = try framedRoundTrip(request, through: transport, nowMillis: 2_000)

        XCTAssertEqual(first.status, .completed)
        XCTAssertEqual(replay.status, .denied)
        XCTAssertEqual(replay.code, ReactiveErrorCode.replayDetected.rawValue)
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
            pinAcknowledgement: auth.hmacHex(pinAcknowledgementTranscript(challenge: challenge))
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

    private func framedRoundTrip<Input: Encodable, Output: Decodable>(
        _ input: Input,
        through transport: ReactiveFramedTransportService,
        nowMillis: Int64
    ) throws -> Output {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let frame = try ReactiveFrameCodec.encode(try encoder.encode(input))
        let responseFrame = try transport.handleFrame(frame, nowMillis: nowMillis)
        return try JSONDecoder().decode(Output.self, from: ReactiveFrameCodec.decode(responseFrame))
    }
}
