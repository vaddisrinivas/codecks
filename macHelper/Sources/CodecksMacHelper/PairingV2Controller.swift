import Foundation
import Security

public struct PairingV2Acceptance: Codable, Equatable {
    public let schema: String
    public let offerId: String
    public let deviceId: String
    public let accepted: Bool
    public let serverProof: String
}

public final class PairingV2Controller {
    private struct Completed { let phoneProof: String; let acceptance: PairingV2Acceptance; let expiresAtMillis: Int64 }
    private let lock = NSLock()
    private var completed: [String: Completed] = [:]
    private let state: PairingV2OfferState
    private let credentials: PairingCredentialStore
    private let records: PairingStore
    private let helperIdentity: HelperIdentityPin
    private let invalidateSessions: (String) -> Void

    public init(
        state: PairingV2OfferState = PairingV2OfferState(),
        credentials: PairingCredentialStore,
        records: PairingStore,
        helperIdentity: HelperIdentityPin,
        invalidateSessions: @escaping (String) -> Void = { _ in }
    ) {
        self.state = state
        self.credentials = credentials
        self.records = records
        self.helperIdentity = helperIdentity
        self.invalidateSessions = invalidateSessions
    }

    public func registerOffer(_ offer: PairingV2Offer, nowMillis: Int64) throws {
        guard case .ready = credentials.availability else { throw ReactiveValidationError("pairing_credentials_not_ready") }
        try state.insert(offer, nowMillis: nowMillis)
    }

    public func begin(_ request: PairingV2Begin, nowMillis: Int64) throws -> PairingV2PendingView {
        try state.begin(request, nowMillis: nowMillis)
    }

    public func confirmOnMac(offerId: String, matchingCode: String, nowMillis: Int64) throws {
        try state.confirmOnMac(offerId: offerId, matchingCode: matchingCode, nowMillis: nowMillis)
    }

    public func pendingConfirmation(offerId: String, nowMillis: Int64) -> PairingV2PendingView? {
        state.pendingConfirmation(offerId: offerId, nowMillis: nowMillis)
    }

    public func cancel(offerId: String, nowMillis: Int64) { state.cancel(offerId: offerId, nowMillis: nowMillis) }

    public func isActive(offerId: String, nowMillis: Int64) -> Bool { state.isActive(offerId: offerId, nowMillis: nowMillis) }

    public func isCompleted(offerId: String, nowMillis: Int64) -> Bool {
        lock.lock(); defer { lock.unlock() }
        completed = completed.filter { $0.value.expiresAtMillis >= nowMillis }
        return completed[offerId] != nil
    }

    public func finish(offerId: String, phoneProof: String, nowMillis: Int64) throws -> PairingV2Acceptance {
        lock.lock(); defer { lock.unlock() }
        completed = completed.filter { $0.value.expiresAtMillis >= nowMillis }
        if let previous = completed[offerId] {
            guard constantTimeEquals(previous.phoneProof, phoneProof) else { throw ReactiveValidationError("pairing confirmation mismatch") }
            return previous.acceptance
        }
        var consumed = try state.consumeConfirmed(offerId: offerId, phoneProof: phoneProof, nowMillis: nowMillis)
        defer { consumed.zeroize() }
        let previousCredential = try credentials.load(deviceId: consumed.deviceId)
        try credentials.save(consumed.credential, deviceId: consumed.deviceId)
        let record = ReactivePairingRecord(
            deviceId: consumed.deviceId,
            helperIdentity: helperIdentity,
            credentialId: "pairing-v2:\(consumed.deviceId)",
            createdAtMillis: nowMillis,
            lastUsedAtMillis: nowMillis
        )
        do {
            try records.save(record)
        } catch {
            if let previousCredential {
                try? credentials.save(previousCredential, deviceId: consumed.deviceId)
            } else {
                try? credentials.delete(deviceId: consumed.deviceId)
            }
            throw error
        }
        let acceptance = PairingV2Acceptance(
            schema: PairingV2.schema,
            offerId: consumed.offerId,
            deviceId: consumed.deviceId,
            accepted: true,
            serverProof: PairingV2.serverAcceptanceProof(credential: consumed.credential, transcript: consumed.transcript)
        )
        if completed.count >= PairingV2.maximumConsumed, let first = completed.min(by: { $0.value.expiresAtMillis < $1.value.expiresAtMillis })?.key {
            completed.removeValue(forKey: first)
        }
        completed[offerId] = Completed(phoneProof: phoneProof, acceptance: acceptance, expiresAtMillis: nowMillis + PairingV2.lifetimeMillis)
        return acceptance
    }

    public func revoke(deviceId: String, atMillis: Int64) throws {
        try records.revoke(deviceId: deviceId, atMillis: atMillis)
        invalidateSessions(deviceId)
        try credentials.delete(deviceId: deviceId)
    }
}

public enum PairingV2OfferFactory {
    public static func make(
        macId: String,
        displayName: String,
        helperIdentity: HelperIdentityPin,
        host: String?,
        port: Int?,
        nowMillis: Int64 = nowMillis()
    ) throws -> PairingV2Offer {
        let id = try randomToken()
        let nonce = try randomToken()
        var secret = Data(count: 32)
        let status = secret.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }
        guard status == errSecSuccess else { throw ReactiveValidationError("pairing randomness unavailable") }
        return PairingV2Offer(
            offerId: id,
            issuedAtMillis: nowMillis,
            expiresAtMillis: nowMillis + PairingV2.lifetimeMillis,
            macId: macId,
            displayName: displayName,
            helperId: helperIdentity.helperId,
            publicKeyFingerprint: helperIdentity.publicKeyFingerprint,
            host: host,
            port: port,
            offerNonce: nonce,
            offerSecretHex: secret.map { String(format: "%02x", $0) }.joined()
        )
    }

    private static func randomToken() throws -> String {
        var bytes = Data(count: 16)
        let status = bytes.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }
        guard status == errSecSuccess else { throw ReactiveValidationError("pairing randomness unavailable") }
        return bytes.base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
}
