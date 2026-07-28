import Foundation
import XCTest
@testable import CodecksMacHelper

final class ReactiveMacHelperRuntimeTests: XCTestCase {
    func testEnvironmentConfigUsesPinnedIdentityAndSecret() throws {
        let config = try ReactiveMacHelperRuntimeConfig.environment([
            "CODECKS_HELPER_PORT": "47322",
            "CODECKS_HELPER_MAC_ID": "mac-fixture",
            "CODECKS_HELPER_ID": "helper-fixture",
            "CODECKS_HELPER_FINGERPRINT": String(repeating: "a", count: 64),
            "CODECKS_HELPER_SECRET_HEX": String(repeating: "01", count: 32),
            "CODECKS_HELPER_ISSUED_AT_MILLIS": "1234"
        ])

        XCTAssertEqual(config.port, 47322)
        XCTAssertEqual(config.macId, "mac-fixture")
        XCTAssertEqual(config.helperIdentity.helperId, "helper-fixture")
        XCTAssertEqual(config.helperIdentity.publicKeyFingerprint, String(repeating: "a", count: 64))
        XCTAssertEqual(config.helperIdentity.trustState, .verified)
        XCTAssertEqual(config.sharedSecret.count, 32)
    }

    func testFileConfigFallbackKeepsSecretOutOfLaunchdEnvironment() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("codecks-helper-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let configUrl = directory.appendingPathComponent("helper.json")
        try Data(
            """
            {
              "port": 47323,
              "macId": "mac-from-file",
              "helperId": "helper-from-file",
              "publicKeyFingerprint": "\(String(repeating: "b", count: 64))",
              "issuedAtMillis": 5678,
              "sharedSecretHex": "\(String(repeating: "02", count: 32))"
            }
            """.utf8
        ).write(to: configUrl)

        let config = try ReactiveMacHelperRuntimeConfig.environment([
            "CODECKS_HELPER_CONFIG": configUrl.path
        ])

        XCTAssertEqual(config.port, 47323)
        XCTAssertEqual(config.macId, "mac-from-file")
        XCTAssertEqual(config.helperIdentity.helperId, "helper-from-file")
        XCTAssertEqual(config.sharedSecret, Data(repeating: 0x02, count: 32))
    }

    func testMissingSecretFailsClosed() {
        XCTAssertThrowsError(
            try ReactiveMacHelperRuntimeConfig.environment([
                "CODECKS_HELPER_CONFIG": "/tmp/codecks-helper-missing-\(UUID().uuidString).json",
                "CODECKS_HELPER_MAC_ID": "mac-fixture",
                "CODECKS_HELPER_ID": "helper-fixture",
                "CODECKS_HELPER_FINGERPRINT": String(repeating: "a", count: 64)
            ])
        )
    }

    func testPairingPayloadJsonContainsPinnedIdentityAndSecret() throws {
        let config = try ReactiveMacHelperRuntimeConfig.environment([
            "CODECKS_HELPER_PORT": "47322",
            "CODECKS_HELPER_MAC_ID": "mac-fixture",
            "CODECKS_HELPER_ID": "helper-fixture",
            "CODECKS_HELPER_FINGERPRINT": String(repeating: "a", count: 64),
            "CODECKS_HELPER_SECRET_HEX": String(repeating: "01", count: 32),
            "CODECKS_HELPER_ISSUED_AT_MILLIS": "1234"
        ])

        let data = try XCTUnwrap(config.pairingPayloadJson(host: "192.168.1.20").data(using: .utf8))
        let payload = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(payload["macId"] as? String, "mac-fixture")
        XCTAssertEqual(payload["helperId"] as? String, "helper-fixture")
        XCTAssertEqual(payload["publicKeyFingerprint"] as? String, String(repeating: "a", count: 64))
        XCTAssertEqual(payload["sharedSecretHex"] as? String, String(repeating: "01", count: 32))
        XCTAssertEqual(payload["host"] as? String, "192.168.1.20")
        XCTAssertEqual(payload["port"] as? Int, 47322)
    }
}
