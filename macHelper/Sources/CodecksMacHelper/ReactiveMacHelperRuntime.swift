import Foundation

public struct ReactiveMacHelperRuntimeConfig: Equatable {
    public var port: UInt16
    public var macId: String
    public var helperIdentity: HelperIdentityPin
    public var sharedSecret: Data

    public init(port: UInt16, macId: String, helperIdentity: HelperIdentityPin, sharedSecret: Data) throws {
        guard port > 0 else { throw ReactiveValidationError("helper port must be positive") }
        try requireToken("macId", macId)
        try helperIdentity.validate()
        guard sharedSecret.count >= 32 else {
            throw ReactiveValidationError("helper shared secret must contain at least 256 bits")
        }
        self.port = port
        self.macId = macId
        self.helperIdentity = helperIdentity
        self.sharedSecret = sharedSecret
    }

    public static func environment(_ environment: [String: String] = ProcessInfo.processInfo.environment) throws -> ReactiveMacHelperRuntimeConfig {
        let fileConfig = try RuntimeFileConfig.load(
            path: environment["CODECKS_HELPER_CONFIG"] ?? RuntimeFileConfig.defaultPath
        )
        let port = UInt16(environment["CODECKS_HELPER_PORT"] ?? "") ?? fileConfig?.port ?? 47321
        let macId = environment["CODECKS_HELPER_MAC_ID"] ?? fileConfig?.macId ?? Host.current().localizedName ?? "mac"
        let helperId = environment["CODECKS_HELPER_ID"] ?? fileConfig?.helperId ?? "codecks-mac-helper"
        let fingerprint = environment["CODECKS_HELPER_FINGERPRINT"] ?? fileConfig?.publicKeyFingerprint ?? ""
        let secretHex = environment["CODECKS_HELPER_SECRET_HEX"] ?? fileConfig?.sharedSecretHex ?? ""
        return try ReactiveMacHelperRuntimeConfig(
            port: port,
            macId: macId,
            helperIdentity: HelperIdentityPin(
                helperId: helperId,
                publicKeyFingerprint: fingerprint,
                issuedAtMillis: Int64(environment["CODECKS_HELPER_ISSUED_AT_MILLIS"] ?? "") ?? fileConfig?.issuedAtMillis ?? nowMillis(),
                trustState: .verified
            ),
            sharedSecret: try Data(hexEncoded: secretHex)
        )
    }

    public func makeCoordinator(pairingStore: PairingStore = FilePairingStore()) -> ReactiveSessionCoordinator {
        ReactiveSessionCoordinator(
            macId: macId,
            helperIdentity: helperIdentity,
            pairingStore: pairingStore,
            actionHandlers: [
                "apple_shortcuts.run": AppleShortcutsActionHandler(),
                "spotlight.search": SpotlightSearchActionHandler(),
                "monitor_brightness.set": MonitorBrightnessActionHandler(),
                "accessibility.discover": AccessibilityDiscoveryActionHandler()
            ]
        )
    }

    public func pairingPayloadJson(displayName: String? = nil, host: String? = nil) throws -> String {
        var payload: [String: Any] = [
            "macId": macId,
            "displayName": displayName ?? macId,
            "helperId": helperIdentity.helperId,
            "publicKeyFingerprint": helperIdentity.publicKeyFingerprint,
            "sharedSecretHex": sharedSecret.map { String(format: "%02x", $0) }.joined(),
            "port": Int(port)
        ]
        if let host, !host.isEmpty {
            payload["host"] = host
        }
        let data = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
        return String(decoding: data, as: UTF8.self)
    }
}

private struct RuntimeFileConfig: Decodable {
    var port: UInt16?
    var macId: String?
    var helperId: String?
    var publicKeyFingerprint: String?
    var issuedAtMillis: Int64?
    var sharedSecretHex: String?

    static let defaultPath = ("~/Library/Application Support/CodecksMacHelper/helper.json" as NSString)
        .expandingTildeInPath

    static func load(path: String) throws -> RuntimeFileConfig? {
        let url = URL(fileURLWithPath: (path as NSString).expandingTildeInPath)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        return try JSONDecoder().decode(RuntimeFileConfig.self, from: Data(contentsOf: url))
    }
}

private extension Data {
    init(hexEncoded value: String) throws {
        let hex = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard hex.count.isMultiple(of: 2), !hex.isEmpty else {
            throw ReactiveValidationError("helper secret hex is invalid")
        }
        var bytes = [UInt8]()
        bytes.reserveCapacity(hex.count / 2)
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else {
                throw ReactiveValidationError("helper secret hex is invalid")
            }
            bytes.append(byte)
            index = next
        }
        self = Data(bytes)
    }
}
