import Darwin
import Foundation

public struct AppleShortcutsCommand: Equatable, Sendable {
    public var executable: String
    public var arguments: [String]
    public var timeoutMillis: Int64
    public var maxOutputBytes: Int
}

public struct CommandResult: Equatable, Sendable {
    public var exitCode: Int32
    public var stdout: Data
    public var stderr: Data
    public var timedOut: Bool
    public var outputLimitExceeded: Bool

    public init(
        exitCode: Int32,
        stdout: Data,
        stderr: Data,
        timedOut: Bool,
        outputLimitExceeded: Bool = false
    ) {
        self.exitCode = exitCode
        self.stdout = stdout
        self.stderr = stderr
        self.timedOut = timedOut
        self.outputLimitExceeded = outputLimitExceeded
    }
}

public protocol CommandRunning {
    func run(_ command: AppleShortcutsCommand) throws -> CommandResult
}

public final class ProcessCommandRunner: CommandRunning {
    public init() {}

    public func run(_ command: AppleShortcutsCommand) throws -> CommandResult {
        guard command.executable.hasPrefix("/"),
              command.timeoutMillis > 0,
              command.maxOutputBytes > 0
        else {
            throw ReactiveValidationError("invalid bounded command")
        }
        let process = Process()
        let stdout = Pipe()
        let stderr = Pipe()
        let stdoutCollector = BoundedPipeCollector(limit: command.maxOutputBytes)
        let stderrCollector = BoundedPipeCollector(limit: command.maxOutputBytes)
        let readers = DispatchGroup()
        process.executableURL = URL(fileURLWithPath: command.executable)
        process.arguments = command.arguments
        process.standardInput = FileHandle.nullDevice
        process.standardOutput = stdout
        process.standardError = stderr
        let semaphore = DispatchSemaphore(value: 0)
        process.terminationHandler = { _ in semaphore.signal() }

        try process.run()
        readers.enter()
        DispatchQueue.global(qos: .utility).async {
            stdoutCollector.drain(stdout.fileHandleForReading)
            readers.leave()
        }
        readers.enter()
        DispatchQueue.global(qos: .utility).async {
            stderrCollector.drain(stderr.fileHandleForReading)
            readers.leave()
        }
        let deadline = DispatchTime.now() + .milliseconds(Int(command.timeoutMillis))
        let finished = process.waitUntilExitOrTimeout(deadline: deadline, semaphore: semaphore)
        if !finished {
            process.terminate()
            if semaphore.wait(timeout: .now() + .milliseconds(500)) != .success, process.isRunning {
                kill(process.processIdentifier, SIGKILL)
            }
        }
        process.waitUntilExit()
        readers.wait()

        return CommandResult(
            exitCode: process.terminationStatus,
            stdout: stdoutCollector.data,
            stderr: stderrCollector.data,
            timedOut: !finished,
            outputLimitExceeded: stdoutCollector.didTruncate || stderrCollector.didTruncate
        )
    }
}

public struct AppleShortcutsActionHandler: ReactiveHelperActionHandler {
    private let runner: CommandRunning
    private let executableAvailable: () -> Bool

    public init(
        runner: CommandRunning = ProcessCommandRunner(),
        executableAvailable: @escaping () -> Bool = {
            FileManager.default.isExecutableFile(atPath: "/usr/bin/shortcuts")
        }
    ) {
        self.runner = runner
        self.executableAvailable = executableAvailable
    }

    public func execute(_ request: ReactiveExecuteRequest, nowMillis: Int64) throws -> ReactiveHelperActionOutcome {
        guard request.type == "execute", request.actionId == "apple_shortcuts.run" else {
            return .denied(code: .unsupportedCapability)
        }
        guard Set(request.arguments.keys).isSubset(of: ["shortcutName", "inputPath"]),
              let shortcutName = request.arguments["shortcutName"],
              shortcutName.isSafeShortcutName
        else {
            return .denied(code: .preconditionFailed)
        }
        guard executableAvailable() else {
            return .denied(code: .unsupportedCapability)
        }
        let inputPath = request.arguments["inputPath"]
        if let inputPath, !inputPath.isSafeAbsolutePath {
            return .denied(code: .preconditionFailed)
        }

        let command = AppleShortcutsCommand(
            executable: "/usr/bin/shortcuts",
            arguments: buildArguments(shortcutName: shortcutName, inputPath: inputPath),
            timeoutMillis: min(max(request.timeoutMillis, 500), 30_000),
            maxOutputBytes: 64 * 1024
        )
        let result: CommandResult
        do {
            result = try runner.run(command)
        } catch {
            return .failed(code: .internalError, retryable: false)
        }
        if result.timedOut {
            return .failed(code: .timeout, retryable: false)
        }
        if result.exitCode != 0 {
            return .failed(code: .preconditionFailed, retryable: false)
        }
        return .completed(
            resultCode: result.outputLimitExceeded
                ? "apple_shortcut_completed_output_truncated"
                : "apple_shortcut_completed",
            undoToken: nil
        )
    }

    private func buildArguments(shortcutName: String, inputPath: String?) -> [String] {
        var arguments = ["run", shortcutName]
        if let inputPath {
            arguments += ["--input-path", inputPath]
        }
        return arguments
    }
}

private extension Process {
    func waitUntilExitOrTimeout(deadline: DispatchTime, semaphore: DispatchSemaphore) -> Bool {
        if semaphore.wait(timeout: deadline) == .success {
            return true
        }
        return !isRunning
    }
}

private final class BoundedPipeCollector: @unchecked Sendable {
    private let limit: Int
    private(set) var data = Data()
    private(set) var didTruncate = false

    init(limit: Int) {
        self.limit = limit
    }

    func drain(_ handle: FileHandle) {
        while true {
            let chunk = handle.availableData
            if chunk.isEmpty { break }
            let remaining = max(0, limit - data.count)
            if remaining > 0 {
                data.append(chunk.prefix(remaining))
            }
            if chunk.count > remaining {
                didTruncate = true
            }
        }
    }
}

private extension String {
    var isSafeShortcutName: Bool {
        !isEmpty &&
            count <= 120 &&
            !unicodeScalars.contains { scalar in
                CharacterSet.controlCharacters.contains(scalar) || scalar.value == 0
            }
    }

    var isSafeAbsolutePath: Bool {
        hasPrefix("/") &&
            count <= 512 &&
            !split(separator: "/", omittingEmptySubsequences: false).contains { $0 == ".." } &&
            !unicodeScalars.contains { scalar in
                CharacterSet.controlCharacters.contains(scalar) || scalar.value == 0
            } &&
            !contains("`") &&
            !contains(";") &&
            !contains("|") &&
            !contains("&") &&
            !contains("<") &&
            !contains(">")
    }
}
