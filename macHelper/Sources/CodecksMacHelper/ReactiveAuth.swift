import CryptoKit
import Foundation

public struct ReactiveAuthenticator {
    private let key: SymmetricKey

    public init(secret: Data) {
        self.key = SymmetricKey(data: secret)
    }

    public func hmacHex(_ transcript: String) -> String {
        let mac = HMAC<SHA256>.authenticationCode(for: Data(transcript.utf8), using: key)
        return Data(mac).map { String(format: "%02x", $0) }.joined()
    }

    public func verify(expectedHex: String, transcript: String) -> Bool {
        constantTimeEquals(expectedHex, hmacHex(transcript))
    }
}

public func constantTimeEquals(_ lhs: String, _ rhs: String) -> Bool {
    guard let left = lhs.data(using: .utf8), let right = rhs.data(using: .utf8), left.count == right.count else {
        return false
    }
    var diff: UInt8 = 0
    for index in left.indices {
        diff |= left[index] ^ right[index]
    }
    return diff == 0
}

public func clientProofTranscript(hello: ReactiveHello, challenge: ReactiveChallenge) -> String {
    canonicalFields(
        "client-proof",
        hello.schema,
        hello.deviceId,
        hello.clientNonce,
        challenge.serverNonce,
        challenge.sessionId,
        String(challenge.expiresAtMillis),
        challenge.macId,
        challenge.helperIdentity.publicKeyFingerprint
    )
}

public func serverProofTranscript(hello: ReactiveHello, challenge: ReactiveChallenge) -> String {
    canonicalFields(
        "server-proof",
        hello.schema,
        hello.deviceId,
        hello.clientNonce,
        challenge.serverNonce,
        challenge.sessionId,
        String(challenge.expiresAtMillis),
        challenge.macId,
        challenge.helperIdentity.publicKeyFingerprint
    )
}

public func requestAuthTranscript(_ envelope: ReactiveRequestEnvelope) -> String {
    canonicalFields(
        "request",
        envelope.schema,
        envelope.sessionId,
        String(envelope.sequence),
        envelope.requestId,
        String(envelope.deadlineMillis),
        envelope.bodyJson
    )
}

public func responseAuthTranscript(_ envelope: ReactiveResponseEnvelope) -> String {
    canonicalFields(
        "response",
        envelope.schema,
        envelope.sessionId,
        String(envelope.sequence),
        envelope.requestId,
        envelope.status.rawValue,
        envelope.code ?? "",
        String(envelope.retryable),
        envelope.bodyJson ?? ""
    )
}
