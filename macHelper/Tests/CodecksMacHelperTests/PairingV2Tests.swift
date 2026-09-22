import Foundation
import XCTest
@testable import CodecksMacHelper

final class PairingV2Tests: XCTestCase {
    private let secret = Data(0..<32)

    func testGoldenTranscriptHKDFAndProofs() throws {
        let offer = sampleOffer()
        let transcript = PairingV2.transcript(offer: offer, deviceId: "CCCCCCCCCCCCCCCCCCCCCg", deviceNonce: "DDDDDDDDDDDDDDDDDDDDDw")
        XCTAssertEqual(transcript, "18:codecks.pairing.v2|22:AAAAAAAAAAAAAAAAAAAAAA|4:1000|6:121000|8:desk-mac|8:Desk Mac|18:codecks-mac-helper|64:\(String(repeating: "a", count: 64))|12:192.168.1.20|5:47321|22:BBBBBBBBBBBBBBBBBBBBBQ|22:CCCCCCCCCCCCCCCCCCCCCg|22:DDDDDDDDDDDDDDDDDDDDDw")
        XCTAssertEqual(PairingV2.beginProof(secret: secret, transcript: transcript), "6f21ecfead9128987be11a8388884f23bf24b5f16c1874ea2e1dc00441bc5746")
        let credential = PairingV2.deriveCredential(secret: secret, transcript: transcript)
        XCTAssertEqual(credential.map { String(format: "%02x", $0) }.joined(), "f4544b58892e6462d9d1e6672ce774760beb396d9378e71759ff32dd9f9ec0c5")
        XCTAssertEqual(PairingV2.matchingCode(credential: credential, transcript: transcript), "025776")
        XCTAssertEqual(PairingV2.confirmationProof(credential: credential, transcript: transcript), "b20cfd5c3f55fa8b98a3bbf9dc4e5b757145ba138a710bbecdf243c6b4e37357")
    }

    func testOfferExpiryBoundariesOneUseWrongProofAndConfirmationGating() throws {
        let state = PairingV2OfferState()
        let offer = sampleOffer()
        try state.insert(offer, nowMillis: 1_000)
        let transcript = PairingV2.transcript(offer: offer, deviceId: "CCCCCCCCCCCCCCCCCCCCCg", deviceNonce: "DDDDDDDDDDDDDDDDDDDDDw")
        let valid = PairingV2Begin(
            offerId: offer.offerId, deviceId: "CCCCCCCCCCCCCCCCCCCCCg", deviceNonce: "DDDDDDDDDDDDDDDDDDDDDw",
            proof: PairingV2.beginProof(secret: secret, transcript: transcript)
        )
        XCTAssertThrowsError(try state.begin(PairingV2Begin(offerId: offer.offerId, deviceId: "CCCCCCCCCCCCCCCCCCCCCg", deviceNonce: "DDDDDDDDDDDDDDDDDDDDDw", proof: "bad"), nowMillis: 121_000))
        let pending = try state.begin(valid, nowMillis: 121_000)
        XCTAssertThrowsError(try state.begin(valid, nowMillis: 121_000))
        let credential = PairingV2.deriveCredential(secret: secret, transcript: transcript)
        let phoneProof = PairingV2.confirmationProof(credential: credential, transcript: transcript)
        XCTAssertThrowsError(try state.consumeConfirmed(offerId: offer.offerId, phoneProof: phoneProof, nowMillis: 121_000))
        XCTAssertThrowsError(try state.confirmOnMac(offerId: offer.offerId, matchingCode: "000000", nowMillis: 121_000))
        try state.confirmOnMac(offerId: offer.offerId, matchingCode: pending.matchingCode, nowMillis: 121_000)
        XCTAssertThrowsError(try state.consumeConfirmed(offerId: offer.offerId, phoneProof: "bad", nowMillis: 121_000))
        XCTAssertEqual(try state.consumeConfirmed(offerId: offer.offerId, phoneProof: phoneProof, nowMillis: 121_000).deviceId, "CCCCCCCCCCCCCCCCCCCCCg")
        XCTAssertThrowsError(try state.consumeConfirmed(offerId: offer.offerId, phoneProof: phoneProof, nowMillis: 121_000))

        let expired = sampleOffer(id: String(repeating: "E", count: 21) + "A")
        XCTAssertThrowsError(try state.insert(expired, nowMillis: 121_001))

        let expiresPending = PairingV2OfferState()
        try expiresPending.insert(offer, nowMillis: 1_000)
        _ = try expiresPending.begin(valid, nowMillis: 1_001)
        XCTAssertNil(expiresPending.pendingConfirmation(offerId: offer.offerId, nowMillis: 121_001))
        XCTAssertThrowsError(try expiresPending.confirmOnMac(offerId: offer.offerId, matchingCode: pending.matchingCode, nowMillis: 121_001))
        XCTAssertThrowsError(try expiresPending.consumeConfirmed(offerId: offer.offerId, phoneProof: phoneProof, nowMillis: 121_001))
    }

    func testOfferCountAndAttemptBounds() throws {
        let state = PairingV2OfferState()
        for index in 0..<PairingV2.maximumOffers {
            try state.insert(sampleOffer(id: String(repeating: String(index), count: 21) + "A"), nowMillis: 1_000)
        }
        XCTAssertThrowsError(try state.insert(sampleOffer(id: String(repeating: "Z", count: 21) + "A"), nowMillis: 1_000))
        let offer = sampleOffer(id: String(repeating: "0", count: 21) + "A")
        for _ in 0..<PairingV2.maximumAttemptsPerOffer {
            XCTAssertThrowsError(try state.begin(PairingV2Begin(offerId: offer.offerId, deviceId: "CCCCCCCCCCCCCCCCCCCCCg", deviceNonce: "DDDDDDDDDDDDDDDDDDDDDw", proof: "bad"), nowMillis: 1_001))
        }
        XCTAssertThrowsError(try state.begin(PairingV2Begin(offerId: offer.offerId, deviceId: "CCCCCCCCCCCCCCCCCCCCCg", deviceNonce: "DDDDDDDDDDDDDDDDDDDDDw", proof: "bad"), nowMillis: 1_001))
    }

    func testGlobalBeginRateIsBoundedAtThirtyTwo() {
        let state = PairingV2OfferState()
        let request = PairingV2Begin(
            offerId: "AAAAAAAAAAAAAAAAAAAAAA", deviceId: "CCCCCCCCCCCCCCCCCCCCCg",
            deviceNonce: "DDDDDDDDDDDDDDDDDDDDDw", proof: "bad"
        )
        for _ in 0..<PairingV2.maximumBeginsPerMinute {
            XCTAssertThrowsError(try state.begin(request, nowMillis: 1_000))
        }
        XCTAssertThrowsError(try state.begin(request, nowMillis: 1_000)) { error in
            XCTAssertTrue(String(describing: error).contains("rate limit"))
        }
    }

    func testConcurrentBeginHasExactlyOneWinner() throws {
        let state = PairingV2OfferState()
        let offer = sampleOffer()
        try state.insert(offer, nowMillis: 1_000)
        let transcript = PairingV2.transcript(offer: offer, deviceId: "CCCCCCCCCCCCCCCCCCCCCg", deviceNonce: "DDDDDDDDDDDDDDDDDDDDDw")
        let request = PairingV2Begin(
            offerId: offer.offerId, deviceId: "CCCCCCCCCCCCCCCCCCCCCg", deviceNonce: "DDDDDDDDDDDDDDDDDDDDDw",
            proof: PairingV2.beginProof(secret: secret, transcript: transcript)
        )
        let results = LockedCounter()
        DispatchQueue.concurrentPerform(iterations: 16) { _ in
            if (try? state.begin(request, nowMillis: 1_001)) != nil { results.increment() }
        }
        XCTAssertEqual(results.value, 1)
    }

    private func sampleOffer(id: String = "AAAAAAAAAAAAAAAAAAAAAA") -> PairingV2Offer {
        PairingV2Offer(
            offerId: id, issuedAtMillis: 1_000, expiresAtMillis: 121_000,
            macId: "desk-mac", displayName: "Desk Mac", helperId: "codecks-mac-helper",
            publicKeyFingerprint: String(repeating: "a", count: 64), host: "192.168.1.20", port: 47_321,
            offerNonce: "BBBBBBBBBBBBBBBBBBBBBQ", offerSecretHex: secret.map { String(format: "%02x", $0) }.joined()
        )
    }
}

private final class LockedCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    func increment() { lock.lock(); count += 1; lock.unlock() }
    var value: Int { lock.lock(); defer { lock.unlock() }; return count }
}
