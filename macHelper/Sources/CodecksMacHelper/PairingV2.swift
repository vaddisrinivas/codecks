import CryptoKit
import Foundation

public enum PairingV2 {
    public static let schema = "codecks.pairing.v2"
    public static let lifetimeMillis: Int64 = 120_000
    public static let maximumOffers = 4
    public static let maximumAttemptsPerOffer = 8
    public static let maximumPending = 4
    public static let maximumConsumed = 64
    public static let maximumBeginsPerMinute = 32

    public static func canonical(_ fields: String...) -> String {
        fields.map { "\(Data($0.utf8).count):\($0)" }.joined(separator: "|")
    }

    public static func transcript(offer: PairingV2Offer, deviceId: String, deviceNonce: String) -> String {
        canonical(
            schema, offer.offerId, String(offer.issuedAtMillis), String(offer.expiresAtMillis),
            offer.macId, offer.displayName, offer.helperId, offer.publicKeyFingerprint,
            offer.host ?? "", offer.port.map(String.init) ?? "", offer.offerNonce,
            deviceId, deviceNonce
        )
    }

    public static func beginProof(secret: Data, transcript: String) -> String {
        hmacHex(key: secret, bytes: Data(canonical("pairing-begin", transcript).utf8))
    }

    public static func deriveCredential(secret: Data, transcript: String) -> Data {
        let key = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: secret),
            salt: Data(canonical("pairing-salt", transcript).utf8),
            info: Data(canonical("pairing-credential", transcript).utf8),
            outputByteCount: 32
        )
        return key.withUnsafeBytes { Data($0) }
    }

    public static func matchingCode(credential: Data, transcript: String) -> String {
        let digest = hmac(key: credential, bytes: Data(canonical("pairing-code", transcript).utf8))
        let value = digest.prefix(4).reduce(UInt32(0)) { ($0 << 8) | UInt32($1) } % 1_000_000
        return String(format: "%06u", value)
    }

    public static func confirmationProof(credential: Data, transcript: String) -> String {
        hmacHex(key: credential, bytes: Data(canonical("pairing-confirm", transcript).utf8))
    }

    public static func serverAcceptanceProof(credential: Data, transcript: String) -> String {
        hmacHex(key: credential, bytes: Data(canonical("pairing-accepted", transcript).utf8))
    }

    private static func hmacHex(key: Data, bytes: Data) -> String {
        hmac(key: key, bytes: bytes).map { String(format: "%02x", $0) }.joined()
    }

    private static func hmac(key: Data, bytes: Data) -> Data {
        Data(HMAC<SHA256>.authenticationCode(for: bytes, using: SymmetricKey(data: key)))
    }
}

public struct PairingV2Offer: Codable, Equatable {
    public let schema: String
    public let offerId: String
    public let issuedAtMillis: Int64
    public let expiresAtMillis: Int64
    public let macId: String
    public let displayName: String
    public let helperId: String
    public let publicKeyFingerprint: String
    public let host: String?
    public let port: Int?
    public let offerNonce: String
    public let offerSecretHex: String

    public init(
        schema: String = PairingV2.schema,
        offerId: String,
        issuedAtMillis: Int64,
        expiresAtMillis: Int64,
        macId: String,
        displayName: String,
        helperId: String,
        publicKeyFingerprint: String,
        host: String? = nil,
        port: Int? = nil,
        offerNonce: String,
        offerSecretHex: String
    ) {
        self.schema = schema
        self.offerId = offerId
        self.issuedAtMillis = issuedAtMillis
        self.expiresAtMillis = expiresAtMillis
        self.macId = macId
        self.displayName = displayName
        self.helperId = helperId
        self.publicKeyFingerprint = publicKeyFingerprint
        self.host = host
        self.port = port
        self.offerNonce = offerNonce
        self.offerSecretHex = offerSecretHex
    }

    public func validate(nowMillis: Int64) throws {
        guard schema == PairingV2.schema else { throw ReactiveValidationError("unsupported pairing schema") }
        guard offerId.isPairingEntropyToken else { throw ReactiveValidationError("offerId invalid") }
        try requireToken("macId", macId)
        try requireToken("helperId", helperId)
        guard offerNonce.isPairingEntropyToken else { throw ReactiveValidationError("offerNonce invalid") }
        guard !displayName.isEmpty, Data(displayName.utf8).count <= 96 else { throw ReactiveValidationError("displayName invalid") }
        guard (32...128).contains(publicKeyFingerprint.count) else { throw ReactiveValidationError("publicKeyFingerprint length invalid") }
        guard expiresAtMillis - issuedAtMillis == PairingV2.lifetimeMillis else { throw ReactiveValidationError("pairing offer lifetime invalid") }
        guard nowMillis >= issuedAtMillis, nowMillis <= expiresAtMillis else { throw ReactiveValidationError("pairing offer expired") }
        guard Data(hex: offerSecretHex)?.count == 32 else { throw ReactiveValidationError("pairing offer secret invalid") }
        guard host == nil || (!host!.isEmpty && Data(host!.utf8).count <= 253) else { throw ReactiveValidationError("pairing host invalid") }
        guard port == nil || (1...65_535).contains(port!) else { throw ReactiveValidationError("pairing port invalid") }
    }

    public func deepLinkPayload() throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let payload = String(decoding: try encoder.encode(self), as: UTF8.self)
        var components = URLComponents()
        components.scheme = "codecks"
        components.host = "helper-pair"
        components.queryItems = [URLQueryItem(name: "payload", value: payload)]
        guard let value = components.string else { throw ReactiveValidationError("pairing deep link invalid") }
        return value
    }
}

public struct PairingV2Begin: Codable, Equatable, Sendable {
    public let pairingType: String
    public let offerId: String
    public let deviceId: String
    public let deviceNonce: String
    public let proof: String

    public init(pairingType: String = "pairing_begin", offerId: String, deviceId: String, deviceNonce: String, proof: String) {
        self.pairingType = pairingType
        self.offerId = offerId
        self.deviceId = deviceId
        self.deviceNonce = deviceNonce
        self.proof = proof
    }
}

public struct PairingV2PendingView: Codable, Equatable {
    public let offerId: String
    public let deviceId: String
    public let matchingCode: String
    public let expiresAtMillis: Int64
}

public struct PairingV2Finish: Codable, Equatable {
    public let pairingType: String
    public let offerId: String
    public let phoneProof: String

    public init(pairingType: String = "pairing_finish", offerId: String, phoneProof: String) {
        self.pairingType = pairingType
        self.offerId = offerId
        self.phoneProof = phoneProof
    }
}

public final class PairingV2OfferState: @unchecked Sendable {
    private struct StoredOffer { let offer: PairingV2Offer; var secret: Data; var attempts: Int }
    private struct Pending {
        let offerId: String
        let deviceId: String
        let transcript: String
        let matchingCode: String
        let expiresAtMillis: Int64
        var credential: Data
        var macConfirmed: Bool

        mutating func zeroize() { credential.resetBytes(in: credential.startIndex..<credential.endIndex) }
    }
    private let lock = NSLock()
    private var offers: [String: StoredOffer] = [:]
    private var consumed: [String: Int64] = [:]
    private var pending: [String: Pending] = [:]
    private var beginEvents: [Int64] = []

    public init() {}

    public func insert(_ offer: PairingV2Offer, nowMillis: Int64) throws {
        try offer.validate(nowMillis: nowMillis)
        guard let secret = Data(hex: offer.offerSecretHex) else { throw ReactiveValidationError("pairing offer secret invalid") }
        lock.lock(); defer { lock.unlock() }
        purgeExpired(nowMillis)
        guard offers.count < PairingV2.maximumOffers else { throw ReactiveValidationError("pairing offer limit reached") }
        guard offers[offer.offerId] == nil, consumed[offer.offerId] == nil else { throw ReactiveValidationError("pairing offer replayed") }
        offers[offer.offerId] = StoredOffer(offer: offer, secret: secret, attempts: 0)
    }

    public func begin(_ request: PairingV2Begin, nowMillis: Int64) throws -> PairingV2PendingView {
        lock.lock(); defer { lock.unlock() }
        purgeExpired(nowMillis)
        beginEvents = beginEvents.filter { nowMillis - $0 < 60_000 }
        guard beginEvents.count < PairingV2.maximumBeginsPerMinute else { throw ReactiveValidationError("pairing rate limit reached") }
        beginEvents.append(nowMillis)
        guard consumed[request.offerId] == nil, var stored = offers[request.offerId] else { throw ReactiveValidationError("pairing offer unavailable") }
        stored.attempts += 1
        offers[request.offerId] = stored
        guard stored.attempts <= PairingV2.maximumAttemptsPerOffer else {
            offers.removeValue(forKey: request.offerId); markConsumed(request.offerId, nowMillis: nowMillis)
            throw ReactiveValidationError("pairing offer attempt limit reached")
        }
        guard request.deviceId.isPairingEntropyToken else { throw ReactiveValidationError("deviceId invalid") }
        guard request.deviceNonce.isPairingEntropyToken else { throw ReactiveValidationError("deviceNonce invalid") }
        let transcript = PairingV2.transcript(offer: stored.offer, deviceId: request.deviceId, deviceNonce: request.deviceNonce)
        guard constantTimeEquals(request.proof, PairingV2.beginProof(secret: stored.secret, transcript: transcript)) else {
            throw ReactiveValidationError("pairing offer authentication failed")
        }
        let credential = PairingV2.deriveCredential(secret: stored.secret, transcript: transcript)
        guard pending.count < PairingV2.maximumPending else { throw ReactiveValidationError("pairing confirmation limit reached") }
        let result = Pending(
            offerId: request.offerId, deviceId: request.deviceId, transcript: transcript,
            matchingCode: PairingV2.matchingCode(credential: credential, transcript: transcript),
            expiresAtMillis: stored.offer.expiresAtMillis, credential: credential, macConfirmed: false
        )
        offers.removeValue(forKey: request.offerId)
        markConsumed(request.offerId, nowMillis: nowMillis)
        pending[request.offerId] = result
        return PairingV2PendingView(offerId: result.offerId, deviceId: result.deviceId, matchingCode: result.matchingCode, expiresAtMillis: result.expiresAtMillis)
    }

    public func confirmOnMac(offerId: String, matchingCode: String, nowMillis: Int64) throws {
        lock.lock(); defer { lock.unlock() }
        purgeExpired(nowMillis)
        guard var value = pending[offerId], constantTimeEquals(value.matchingCode, matchingCode) else {
            throw ReactiveValidationError("pairing confirmation mismatch")
        }
        value.macConfirmed = true
        pending[offerId] = value
    }

    public func consumeConfirmed(offerId: String, phoneProof: String, nowMillis: Int64) throws -> PairingV2Consumed {
        lock.lock(); defer { lock.unlock() }
        purgeExpired(nowMillis)
        guard let value = pending[offerId], value.macConfirmed else { throw ReactiveValidationError("pairing confirmation required") }
        let expected = PairingV2.confirmationProof(credential: value.credential, transcript: value.transcript)
        guard constantTimeEquals(phoneProof, expected) else { throw ReactiveValidationError("pairing confirmation mismatch") }
        pending.removeValue(forKey: offerId)
        return PairingV2Consumed(offerId: value.offerId, deviceId: value.deviceId, transcript: value.transcript, credential: value.credential)
    }

    public func pendingConfirmation(offerId: String, nowMillis: Int64) -> PairingV2PendingView? {
        lock.lock(); defer { lock.unlock() }
        purgeExpired(nowMillis)
        return pending[offerId].map { PairingV2PendingView(offerId: $0.offerId, deviceId: $0.deviceId, matchingCode: $0.matchingCode, expiresAtMillis: $0.expiresAtMillis) }
    }

    public func cancel(offerId: String, nowMillis: Int64) {
        lock.lock(); defer { lock.unlock() }
        if var offer = offers.removeValue(forKey: offerId) {
            offer.secret.resetBytes(in: offer.secret.startIndex..<offer.secret.endIndex)
        }
        if pending[offerId] != nil { pending[offerId]?.zeroize(); pending.removeValue(forKey: offerId) }
        markConsumed(offerId, nowMillis: nowMillis)
    }

    public func isActive(offerId: String, nowMillis: Int64) -> Bool {
        lock.lock(); defer { lock.unlock() }
        purgeExpired(nowMillis)
        return offers[offerId] != nil || pending[offerId] != nil
    }

    private func purgeExpired(_ nowMillis: Int64) {
        offers = offers.filter { $0.value.offer.expiresAtMillis >= nowMillis }
        let expiredPending = pending.filter { $0.value.expiresAtMillis < nowMillis }.map(\.key)
        for key in expiredPending {
            pending[key]?.zeroize()
            pending.removeValue(forKey: key)
        }
        consumed = consumed.filter { nowMillis - $0.value <= PairingV2.lifetimeMillis }
    }

    private func markConsumed(_ offerId: String, nowMillis: Int64) {
        if consumed.count >= PairingV2.maximumConsumed, let oldest = consumed.min(by: { $0.value < $1.value })?.key {
            consumed.removeValue(forKey: oldest)
        }
        consumed[offerId] = nowMillis
    }
}

public struct PairingV2Consumed: Equatable {
    public let offerId: String
    public let deviceId: String
    public let transcript: String
    public var credential: Data

    public mutating func zeroize() { credential.resetBytes(in: credential.startIndex..<credential.endIndex) }
}

extension Data {
    init?(hex: String) {
        guard !hex.isEmpty, hex.count.isMultiple(of: 2) else { return nil }
        var bytes: [UInt8] = []
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else { return nil }
            bytes.append(byte); index = next
        }
        self = Data(bytes)
    }
}

private extension String {
    var isPairingEntropyToken: Bool {
        let bytes = Array(utf8)
        guard bytes.count == 22 && bytes.allSatisfy({
            (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || $0 == 45 || $0 == 95
        }) else { return false }
        let padded = replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/") + "=="
        guard let decoded = Data(base64Encoded: padded), decoded.count == 16 else { return false }
        return decoded.base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") == self
    }
}
