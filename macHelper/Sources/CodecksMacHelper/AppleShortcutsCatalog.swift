import Foundation

public struct AppleShortcutDescriptor: Equatable, Sendable {
    public var name: String

    public init(name: String) {
        self.name = name
    }
}

public enum AppleShortcutsCatalogError: Error, Equatable {
    case unavailable
    case timedOut
    case outputLimitExceeded
    case commandFailed
    case invalidOutput
}

public struct AppleShortcutsCatalogImporter {
    public static let maxShortcuts = 256

    public init() {}

    public func parse(_ data: Data) throws -> [AppleShortcutDescriptor] {
        guard let output = String(data: data, encoding: .utf8) else {
            throw AppleShortcutsCatalogError.invalidOutput
        }
        var seen = Set<String>()
        var shortcuts: [AppleShortcutDescriptor] = []
        for rawLine in output.split(whereSeparator: \.isNewline) {
            let name = rawLine.trimmingCharacters(in: .whitespaces)
            guard name.isSafeImportedShortcutName else {
                throw AppleShortcutsCatalogError.invalidOutput
            }
            if seen.insert(name).inserted {
                shortcuts.append(AppleShortcutDescriptor(name: name))
            }
            if shortcuts.count > Self.maxShortcuts {
                throw AppleShortcutsCatalogError.outputLimitExceeded
            }
        }
        return shortcuts
    }
}

public struct AppleShortcutsCatalogReader {
    private let runner: CommandRunning
    private let importer: AppleShortcutsCatalogImporter
    private let executableAvailable: () -> Bool

    public init(
        runner: CommandRunning = ProcessCommandRunner(),
        importer: AppleShortcutsCatalogImporter = AppleShortcutsCatalogImporter(),
        executableAvailable: @escaping () -> Bool = {
            FileManager.default.isExecutableFile(atPath: "/usr/bin/shortcuts")
        }
    ) {
        self.runner = runner
        self.importer = importer
        self.executableAvailable = executableAvailable
    }

    public func load() throws -> [AppleShortcutDescriptor] {
        guard executableAvailable() else {
            throw AppleShortcutsCatalogError.unavailable
        }
        let command = AppleShortcutsCommand(
            executable: "/usr/bin/shortcuts",
            arguments: ["list"],
            timeoutMillis: 10_000,
            maxOutputBytes: 64 * 1024
        )
        let result = try runner.run(command)
        guard !result.timedOut else { throw AppleShortcutsCatalogError.timedOut }
        guard !result.outputLimitExceeded else {
            throw AppleShortcutsCatalogError.outputLimitExceeded
        }
        guard result.exitCode == 0 else {
            throw AppleShortcutsCatalogError.commandFailed
        }
        return try importer.parse(result.stdout)
    }
}

private extension String {
    var isSafeImportedShortcutName: Bool {
        !trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            count <= 120 &&
            !unicodeScalars.contains { scalar in
                CharacterSet.controlCharacters.contains(scalar) || scalar.value == 0
            }
    }
}
