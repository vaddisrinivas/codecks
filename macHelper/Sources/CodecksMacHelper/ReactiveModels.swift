import Foundation

public struct HelperIdentityPin: Codable, Equatable {
    public var helperId: String
    public var publicKeyFingerprint: String
    public var issuedAtMillis: Int64
    public var trustState: HelperTrustState

    public init(helperId: String, publicKeyFingerprint: String, issuedAtMillis: Int64, trustState: HelperTrustState) {
        self.helperId = helperId
        self.publicKeyFingerprint = publicKeyFingerprint
        self.issuedAtMillis = issuedAtMillis
        self.trustState = trustState
    }

    public func validate(allowRevoked: Bool = false) throws {
        try requireToken("helperId", helperId)
        guard (32...128).contains(publicKeyFingerprint.count) else {
            throw ReactiveValidationError("publicKeyFingerprint length invalid")
        }
        guard issuedAtMillis >= 0 else {
            throw ReactiveValidationError("issuedAtMillis must not be negative")
        }
        if !allowRevoked && trustState == .revoked {
            throw ReactiveValidationError("helper identity revoked")
        }
    }
}

public struct ReactivePairingRecord: Codable, Equatable {
    public var deviceId: String
    public var helperIdentity: HelperIdentityPin
    public var credentialId: String
    public var createdAtMillis: Int64
    public var lastUsedAtMillis: Int64
    public var revokedAtMillis: Int64?

    public init(deviceId: String, helperIdentity: HelperIdentityPin, credentialId: String, createdAtMillis: Int64, lastUsedAtMillis: Int64, revokedAtMillis: Int64? = nil) {
        self.deviceId = deviceId
        self.helperIdentity = helperIdentity
        self.credentialId = credentialId
        self.createdAtMillis = createdAtMillis
        self.lastUsedAtMillis = lastUsedAtMillis
        self.revokedAtMillis = revokedAtMillis
    }

    public var isRevoked: Bool {
        revokedAtMillis != nil || helperIdentity.trustState == .revoked
    }

    public func validate() throws {
        try requireToken("deviceId", deviceId)
        try helperIdentity.validate(allowRevoked: true)
        try requireToken("credentialId", credentialId)
        guard createdAtMillis >= 0 else { throw ReactiveValidationError("createdAtMillis must not be negative") }
        guard lastUsedAtMillis >= createdAtMillis else { throw ReactiveValidationError("lastUsedAtMillis before createdAtMillis") }
        if let revokedAtMillis {
            guard revokedAtMillis >= createdAtMillis else { throw ReactiveValidationError("revokedAtMillis before createdAtMillis") }
            guard helperIdentity.trustState == .revoked else { throw ReactiveValidationError("revokedAtMillis requires revoked helper identity") }
        }
    }
}

public struct ReactiveHello: Codable, Equatable {
    public var schema: String
    public var minMajor: Int
    public var maxMajor: Int
    public var deviceId: String
    public var clientNonce: String
    public var clientTimeMillis: Int64

    public init(schema: String = ReactiveConstants.schema, minMajor: Int = ReactiveConstants.minMajor, maxMajor: Int = ReactiveConstants.currentMajor, deviceId: String, clientNonce: String, clientTimeMillis: Int64) {
        self.schema = schema
        self.minMajor = minMajor
        self.maxMajor = maxMajor
        self.deviceId = deviceId
        self.clientNonce = clientNonce
        self.clientTimeMillis = clientTimeMillis
    }
}

public struct ReactiveChallenge: Codable, Equatable {
    public var schema: String
    public var selectedMajor: Int
    public var sessionId: String
    public var serverNonce: String
    public var expiresAtMillis: Int64
    public var macId: String
    public var helperIdentity: HelperIdentityPin
    public var verificationCode: String
}

public struct ReactiveProof: Codable, Equatable {
    public var schema: String
    public var sessionId: String
    public var proof: String
    public var pinAcknowledgement: String

    public init(schema: String = ReactiveConstants.schema, sessionId: String, proof: String, pinAcknowledgement: String) {
        self.schema = schema
        self.sessionId = sessionId
        self.proof = proof
        self.pinAcknowledgement = pinAcknowledgement
    }
}

public struct ReactiveAuthResult: Codable, Equatable {
    public var schema: String
    public var sessionId: String
    public var accepted: Bool
    public var expiresAtMillis: Int64
    public var serverProof: String
    public var code: String?
    public var pinnedHelperIdentity: HelperIdentityPin?
}

public struct ReactiveRequestEnvelope: Codable, Equatable {
    public var schema: String
    public var sessionId: String
    public var sequence: Int64
    public var requestId: String
    public var deadlineMillis: Int64
    public var bodyJson: String
    public var authTag: String

    public init(schema: String = ReactiveConstants.schema, sessionId: String, sequence: Int64, requestId: String, deadlineMillis: Int64, bodyJson: String, authTag: String) {
        self.schema = schema
        self.sessionId = sessionId
        self.sequence = sequence
        self.requestId = requestId
        self.deadlineMillis = deadlineMillis
        self.bodyJson = bodyJson
        self.authTag = authTag
    }
}

public struct ReactiveResponseEnvelope: Codable, Equatable {
    public var schema: String = ReactiveConstants.schema
    public var sessionId: String
    public var sequence: Int64
    public var requestId: String
    public var status: ReceiptStatus
    public var code: String?
    public var retryable: Bool
    public var bodyJson: String?
    public var authTag: String
}

public struct ReactiveHelperCapabilities: Codable, Equatable {
    public var schema: String = ReactiveConstants.schema
    public var helperId: String
    public var protocolMajor: Int
    public var values: [String]
}

public struct ReactiveHelperBasicState: Codable, Equatable {
    public var schema: String = ReactiveConstants.schema
    public var macId: String
    public var snapshotRevision: Int64
    public var capturedAtMillis: Int64
    public var freshnessMillis: Int64
    public var provenance: StateProvenance
    public var frontAppBundleId: String?
    public var frontAppName: String?
    public var capabilities: [String]
    public var stale: Bool
}

public struct HelperRequestBody: Codable, Equatable {
    public var type: String
    public var actionId: String?
}
