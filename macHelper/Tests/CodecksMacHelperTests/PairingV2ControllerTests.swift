import Foundation
import XCTest
@testable import CodecksMacHelper

final class PairingV2ControllerTests: XCTestCase {
    private let identity = HelperIdentityPin(helperId: "helper", publicKeyFingerprint: String(repeating: "a", count: 64), issuedAtMillis: 1, trustState: .verified)
    private let deviceId = "CCCCCCCCCCCCCCCCCCCCCg"

    func testFinishPersistsOnlyAfterBothConfirmationsAndIsIdempotent() throws {
        let credentials = SpyCredentialStore()
        let records = SpyPairingStore()
        var evicted: [String] = []
        let controller = PairingV2Controller(credentials: credentials, records: records, helperIdentity: identity) { evicted.append($0) }
        let fixture = try begin(controller)
        XCTAssertNil(try credentials.load(deviceId: deviceId))
        XCTAssertThrowsError(try controller.finish(offerId: fixture.offer.offerId, phoneProof: fixture.phoneProof, nowMillis: 2_000))
        try controller.confirmOnMac(offerId: fixture.offer.offerId, matchingCode: fixture.view.matchingCode, nowMillis: 2_000)
        let accepted = try controller.finish(offerId: fixture.offer.offerId, phoneProof: fixture.phoneProof, nowMillis: 2_000)
        XCTAssertTrue(controller.isCompleted(offerId: fixture.offer.offerId, nowMillis: 2_000))
        XCTAssertFalse(controller.isActive(offerId: fixture.offer.offerId, nowMillis: 2_000))
        XCTAssertEqual(accepted, try controller.finish(offerId: fixture.offer.offerId, phoneProof: fixture.phoneProof, nowMillis: 2_001))
        XCTAssertEqual(records.saved?.credentialId, "pairing-v2:\(deviceId)")
        XCTAssertEqual(try credentials.load(deviceId: deviceId)?.count, 32)
        try controller.revoke(deviceId: deviceId, atMillis: 3_000)
        XCTAssertNil(try credentials.load(deviceId: deviceId))
        XCTAssertEqual(records.revoked, deviceId)
        XCTAssertEqual(evicted, [deviceId])
    }

    func testCancelTombstonesOfferAndExpiryInvalidatesIt() throws {
        let controller = PairingV2Controller(credentials: SpyCredentialStore(), records: SpyPairingStore(), helperIdentity: identity)
        let fixture = try begin(controller)
        controller.cancel(offerId: fixture.offer.offerId, nowMillis: 1_200)
        XCTAssertFalse(controller.isActive(offerId: fixture.offer.offerId, nowMillis: 1_200))
        XCTAssertThrowsError(try controller.begin(PairingV2Begin(offerId: fixture.offer.offerId, deviceId: deviceId, deviceNonce: "DDDDDDDDDDDDDDDDDDDDDw", proof: "bad"), nowMillis: 1_201))

        let next = PairingV2Offer(
            offerId: "EEEEEEEEEEEEEEEEEEEEEA", issuedAtMillis: 2_000, expiresAtMillis: 122_000,
            macId: "mac", displayName: "Desk Mac", helperId: identity.helperId,
            publicKeyFingerprint: identity.publicKeyFingerprint, offerNonce: "FFFFFFFFFFFFFFFFFFFFFQ",
            offerSecretHex: String(repeating: "01", count: 32)
        )
        try controller.registerOffer(next, nowMillis: 2_000)
        XCTAssertFalse(controller.isActive(offerId: next.offerId, nowMillis: 122_001))
    }

    func testRecordFailureRestoresExistingCredential() throws {
        let credentials = SpyCredentialStore()
        let previous = Data(repeating: 9, count: 32)
        try credentials.save(previous, deviceId: deviceId)
        let records = SpyPairingStore(); records.failSave = true
        let controller = PairingV2Controller(credentials: credentials, records: records, helperIdentity: identity)
        let fixture = try begin(controller)
        try controller.confirmOnMac(offerId: fixture.offer.offerId, matchingCode: fixture.view.matchingCode, nowMillis: 2_000)
        XCTAssertThrowsError(try controller.finish(offerId: fixture.offer.offerId, phoneProof: fixture.phoneProof, nowMillis: 2_000))
        XCTAssertEqual(try credentials.load(deviceId: deviceId), previous)
    }

    func testKeychainAvailabilityRequiresExactEntitledGroupAndSigningIdentity() {
        XCTAssertEqual(KeychainPairingCredentialStore(applicationIdentifier: nil, teamIdentifier: nil).availability, .notReady(code: "pairing_signing_identity_not_ready"))
        XCTAssertEqual(
            KeychainPairingCredentialStore(applicationIdentifier: "TEAM.app.codecks.helper", teamIdentifier: "TEAM").availability,
            .ready(applicationIdentifier: "TEAM.app.codecks.helper", teamIdentifier: "TEAM")
        )
        XCTAssertEqual(
            KeychainPairingCredentialStore(applicationIdentifier: "OTHER.app.codecks.helper", teamIdentifier: "TEAM").availability,
            .notReady(code: "pairing_signing_identity_not_ready")
        )
    }

    private func begin(_ controller: PairingV2Controller) throws -> (offer: PairingV2Offer, view: PairingV2PendingView, phoneProof: String) {
        let secret = Data(0..<32)
        let offer = PairingV2Offer(
            offerId: "AAAAAAAAAAAAAAAAAAAAAA", issuedAtMillis: 1_000, expiresAtMillis: 121_000,
            macId: "mac", displayName: "Desk Mac", helperId: identity.helperId,
            publicKeyFingerprint: identity.publicKeyFingerprint, host: "127.0.0.1", port: 47_321,
            offerNonce: "BBBBBBBBBBBBBBBBBBBBBQ", offerSecretHex: secret.map { String(format: "%02x", $0) }.joined()
        )
        try controller.registerOffer(offer, nowMillis: 1_000)
        let nonce = "DDDDDDDDDDDDDDDDDDDDDw"
        let transcript = PairingV2.transcript(offer: offer, deviceId: deviceId, deviceNonce: nonce)
        let view = try controller.begin(PairingV2Begin(offerId: offer.offerId, deviceId: deviceId, deviceNonce: nonce, proof: PairingV2.beginProof(secret: secret, transcript: transcript)), nowMillis: 1_100)
        let credential = PairingV2.deriveCredential(secret: secret, transcript: transcript)
        return (offer, view, PairingV2.confirmationProof(credential: credential, transcript: transcript))
    }
}

private final class SpyCredentialStore: PairingCredentialStore {
    let availability: PairingCredentialAvailability = .ready(applicationIdentifier: "TEST.test", teamIdentifier: "TEST")
    private var values: [String: Data] = [:]
    func save(_ credential: Data, deviceId: String) throws { values[deviceId] = credential }
    func load(deviceId: String) throws -> Data? { values[deviceId] }
    func delete(deviceId: String) throws { values.removeValue(forKey: deviceId) }
}

private final class SpyPairingStore: PairingStore {
    var saved: ReactivePairingRecord?
    var revoked: String?
    var failSave = false
    func all() throws -> [ReactivePairingRecord] { saved.map { [$0] } ?? [] }
    func load(deviceId: String) throws -> ReactivePairingRecord? { saved?.deviceId == deviceId ? saved : nil }
    func save(_ record: ReactivePairingRecord) throws {
        if failSave { throw ReactiveValidationError("injected") }
        saved = record
    }
    func revoke(deviceId: String, atMillis: Int64) throws { revoked = deviceId }
}
