import Foundation

public enum ReactiveConstants {
    public static let schema = "reactive.v1"
    public static let minMajor = 1
    public static let currentMajor = 1
    public static let maxBodyBytes = 64 * 1024
    public static let maxTokenBytes = 128
    public static let replayWindowMillis: Int64 = 5 * 60 * 1000
}

public enum ReactiveErrorCode: String, Codable, Equatable {
    case unsupportedProtocol = "unsupported_protocol"
    case authenticationRequired = "authentication_required"
    case pinMismatch = "pin_mismatch"
    case replayDetected = "replay_detected"
    case clockSkew = "clock_skew"
    case unsupportedCapability = "unsupported_capability"
    case preconditionFailed = "precondition_failed"
    case timeout = "timeout"
    case cancelled = "cancelled"
    case partialFailure = "partial_failure"
    case internalError = "internal"
}

public enum ReceiptStatus: String, Codable, Equatable {
    case accepted
    case completed
    case partialFailure = "partial_failure"
    case timeout
    case cancelled
    case failed
    case denied
}

public enum HelperTrustState: String, Codable, Equatable {
    case unverified
    case verified
    case revoked
}

public enum StateProvenance: String, Codable, Equatable {
    case helper
}

public func nowMillis() -> Int64 {
    Int64(Date().timeIntervalSince1970 * 1000)
}

func requireToken(_ name: String, _ value: String) throws {
    guard !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
        throw ReactiveValidationError("\(name) must not be blank")
    }
    guard value.data(using: .utf8)?.count ?? 0 <= ReactiveConstants.maxTokenBytes else {
        throw ReactiveValidationError("\(name) exceeds \(ReactiveConstants.maxTokenBytes) bytes")
    }
}

func canonicalFields(_ values: String...) -> String {
    values.joined(separator: "\u{0000}")
}

public struct ReactiveValidationError: Error, Equatable, CustomStringConvertible {
    public let description: String

    public init(_ description: String) {
        self.description = description
    }
}
