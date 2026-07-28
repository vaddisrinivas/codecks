import Foundation

public protocol PairingStore {
    func load(deviceId: String) throws -> ReactivePairingRecord?
    func save(_ record: ReactivePairingRecord) throws
    func revoke(deviceId: String, atMillis: Int64) throws
}

public final class FilePairingStore: PairingStore {
    private let root: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(root: URL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("CodecksMacHelper", isDirectory: true)) {
        self.root = root
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    }

    public func load(deviceId: String) throws -> ReactivePairingRecord? {
        let url = recordURL(deviceId: deviceId)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let record = try decoder.decode(ReactivePairingRecord.self, from: Data(contentsOf: url))
        try record.validate()
        return record
    }

    public func save(_ record: ReactivePairingRecord) throws {
        try record.validate()
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try encoder.encode(record).write(to: recordURL(deviceId: record.deviceId), options: [.atomic])
    }

    public func revoke(deviceId: String, atMillis: Int64) throws {
        guard var record = try load(deviceId: deviceId) else { return }
        record.revokedAtMillis = atMillis
        record.helperIdentity.trustState = .revoked
        try save(record)
    }

    private func recordURL(deviceId: String) -> URL {
        let safeName = deviceId.filter { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" }
        return root.appendingPathComponent("\(safeName).pairing.json")
    }
}

public final class InMemoryPairingStore: PairingStore {
    private var records: [String: ReactivePairingRecord] = [:]

    public init(records: [ReactivePairingRecord] = []) {
        for record in records {
            self.records[record.deviceId] = record
        }
    }

    public func load(deviceId: String) throws -> ReactivePairingRecord? {
        records[deviceId]
    }

    public func save(_ record: ReactivePairingRecord) throws {
        records[record.deviceId] = record
    }

    public func revoke(deviceId: String, atMillis: Int64) throws {
        guard var record = records[deviceId] else { return }
        record.revokedAtMillis = atMillis
        record.helperIdentity.trustState = .revoked
        records[deviceId] = record
    }
}
