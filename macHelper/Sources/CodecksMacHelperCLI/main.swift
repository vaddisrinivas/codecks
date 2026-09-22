import CodecksMacHelper
import Foundation
import Security

let args = Array(CommandLine.arguments.dropFirst())

do {
    switch args.first {
    case "serve":
        let config = try ReactiveMacHelperRuntimeConfig.environment()
        let records = FilePairingStore()
        let credentials = KeychainPairingCredentialStore()
        let coordinator = config.makeCoordinator(pairingStore: records, credentialStore: credentials)
        let pairing = PairingV2Controller(
            credentials: credentials,
            records: records,
            helperIdentity: config.helperIdentity,
            invalidateSessions: coordinator.evictSessions(deviceId:)
        )
        let framed = ReactiveFramedTransportService(coordinator: coordinator, secret: config.sharedSecret, pairingController: pairing)
        let server = ReactiveTcpHelperServer(port: config.port, handler: framed)
        try server.start()
        print("codecks-mac-helper serving on port \(config.port)")
        RunLoop.main.run()
    case "check-config":
        _ = try ReactiveMacHelperRuntimeConfig.environment()
        print("codecks-mac-helper config ok")
    case "print-pairing-json":
        guard args.dropFirst().first == "--unsafe-legacy" else {
            throw ReactiveValidationError("legacy pairing export disabled; pass --unsafe-legacy only for an already-known legacy phone")
        }
        let config = try ReactiveMacHelperRuntimeConfig.environment()
        let host = args.dropFirst(2).first
        FileHandle.standardError.write(Data("warning: legacy export contains the permanent global secret\n".utf8))
        print(try config.pairingPayloadJson(host: host))
    case "_keychain-probe":
        var deviceBytes = Data(count: 16)
        var credential = Data(count: 32)
        guard deviceBytes.withUnsafeMutableBytes({ SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }) == errSecSuccess,
              credential.withUnsafeMutableBytes({ SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }) == errSecSuccess else {
            throw ReactiveValidationError("keychain probe entropy failed")
        }
        let deviceId = deviceBytes.base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        let credentials = KeychainPairingCredentialStore()
        guard case .ready = credentials.availability else { throw ReactiveValidationError("pairing_credentials_not_ready") }
        try credentials.save(credential, deviceId: deviceId)
        defer { try? credentials.delete(deviceId: deviceId) }
        guard try credentials.load(deviceId: deviceId) == credential else {
            throw ReactiveValidationError("keychain probe mismatch")
        }
        try credentials.delete(deviceId: deviceId)
        print("KEYCHAIN_PROBE_PASS")
    default:
        print("usage: codecks-mac-helper serve | check-config | print-pairing-json --unsafe-legacy [host]")
        exit(2)
    }
} catch {
    FileHandle.standardError.write(Data("codecks-mac-helper: \(error)\n".utf8))
    exit(1)
}
