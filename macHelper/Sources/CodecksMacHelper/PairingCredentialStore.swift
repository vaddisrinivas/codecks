import Foundation
import Security

public enum PairingCredentialAvailability: Equatable {
    case ready(applicationIdentifier: String, teamIdentifier: String)
    case notReady(code: String)
}

public protocol PairingCredentialStore {
    var availability: PairingCredentialAvailability { get }
    func save(_ credential: Data, deviceId: String) throws
    func load(deviceId: String) throws -> Data?
    func delete(deviceId: String) throws
}

public final class KeychainPairingCredentialStore: PairingCredentialStore {
    public static let service = "app.codecks.mac-helper.pairing.v2"
    private let applicationIdentifier: String?
    private let teamIdentifier: String?

    public convenience init() {
        self.init(
            applicationIdentifier: (Self.currentEntitlement("com.apple.application-identifier") ?? Self.currentEntitlement("application-identifier")) as? String,
            teamIdentifier: Self.currentEntitlement("com.apple.developer.team-identifier") as? String
        )
    }

    public init(applicationIdentifier: String?, teamIdentifier: String?) {
        self.applicationIdentifier = applicationIdentifier
        self.teamIdentifier = teamIdentifier
    }

    public var availability: PairingCredentialAvailability {
        guard let applicationIdentifier, !applicationIdentifier.isEmpty,
              let teamIdentifier, !teamIdentifier.isEmpty,
              applicationIdentifier.hasPrefix(teamIdentifier + ".") else {
            return .notReady(code: "pairing_signing_identity_not_ready")
        }
        return .ready(applicationIdentifier: applicationIdentifier, teamIdentifier: teamIdentifier)
    }

    public func save(_ credential: Data, deviceId: String) throws {
        try validate(deviceId: deviceId, credential: credential)
        let query = try baseQuery(deviceId: deviceId)
        let updateStatus = SecItemUpdate(query as CFDictionary, [kSecValueData as String: credential] as CFDictionary)
        if updateStatus == errSecItemNotFound {
            var add = query
            add[kSecValueData as String] = credential
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(add as CFDictionary, nil) == errSecSuccess else {
                throw ReactiveValidationError("pairing credential store failed")
            }
        } else if updateStatus != errSecSuccess {
            throw ReactiveValidationError("pairing credential store failed")
        }
    }

    public func load(deviceId: String) throws -> Data? {
        try validateDeviceId(deviceId)
        var query = try baseQuery(deviceId: deviceId)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let value = result as? Data, value.count == 32 else {
            throw ReactiveValidationError("pairing credential read failed")
        }
        return value
    }

    public func delete(deviceId: String) throws {
        try validateDeviceId(deviceId)
        let status = SecItemDelete(try baseQuery(deviceId: deviceId) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw ReactiveValidationError("pairing credential delete failed")
        }
    }

    private func baseQuery(deviceId: String) throws -> [String: Any] {
        guard case .ready = availability else {
            throw ReactiveValidationError("pairing_credentials_not_ready")
        }
        return [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: deviceId,
            kSecAttrSynchronizable as String: false,
        ]
    }

    private func validate(deviceId: String, credential: Data) throws {
        try validateDeviceId(deviceId)
        guard credential.count == 32 else { throw ReactiveValidationError("pairing credential invalid") }
    }

    private func validateDeviceId(_ deviceId: String) throws {
        guard deviceId.data(using: .utf8)?.count == 22, deviceId.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-" || $0 == "_") }) else {
            throw ReactiveValidationError("pairing device id invalid")
        }
    }

    private static func currentEntitlement(_ name: String) -> Any? {
        guard let task = SecTaskCreateFromSelf(nil) else { return nil }
        return SecTaskCopyValueForEntitlement(task, name as CFString, nil)
    }
}

public final class InMemoryPairingCredentialStore: PairingCredentialStore {
    public var availability: PairingCredentialAvailability = .ready(applicationIdentifier: "TEST.test.app", teamIdentifier: "TEST")
    private var values: [String: Data] = [:]
    public init() {}
    public func save(_ credential: Data, deviceId: String) throws { values[deviceId] = credential }
    public func load(deviceId: String) throws -> Data? { values[deviceId] }
    public func delete(deviceId: String) throws { values.removeValue(forKey: deviceId) }
}
