import Foundation

public struct AppleShortcutsCommand: Equatable {
    public var executable: String
    public var arguments: [String]
    public var timeoutMillis: Int64
    public var maxOutputBytes: Int
}

public struct CommandResult: Equatable {
    public var exitCode: Int32
    public var stdout: Data
    public var stderr: Data
    public var timedOut: Bool
}

public protocol CommandRunning {
    func run(_ command: AppleShortcutsCommand) throws -> CommandResult
}

public final class ProcessCommandRunner: CommandRunning {
    public init() {}

    public func run(_ command: AppleShortcutsCommand) throws -> CommandResult {
        let process = Process()
        let stdout = Pipe()
        let stderr = Pipe()
        process.executableURL = URL(fileURLWithPath: command.executable)
        process.arguments = command.arguments
        process.standardOutput = stdout
        process.standardError = stderr
        let semaphore = DispatchSemaphore(value: 0)
        process.terminationHandler = { _ in semaphore.signal() }

        try process.run()
        let deadline = DispatchTime.now() + .milliseconds(Int(command.timeoutMillis))
        let finished = process.waitUntilExitOrTimeout(deadline: deadline, semaphore: semaphore)
        if !finished {
            process.terminate()
        }

        let out = stdout.fileHandleForReading.readDataToEndOfFile()
        let err = stderr.fileHandleForReading.readDataToEndOfFile()
        return CommandResult(
            exitCode: process.terminationStatus,
            stdout: out.prefixBytes(command.maxOutputBytes),
            stderr: err.prefixBytes(command.maxOutputBytes),
            timedOut: !finished
        )
    }
}

public struct AppleShortcutsActionHandler: ReactiveHelperActionHandler {
    private let runner: CommandRunning

    public init(runner: CommandRunning = ProcessCommandRunner()) {
        self.runner = runner
    }

    public func execute(_ request: ReactiveExecuteRequest, nowMillis: Int64) throws -> ReactiveHelperActionOutcome {
        guard request.actionId == "apple_shortcuts.run" else {
            return .denied(code: .unsupportedCapability)
        }
        guard let shortcutName = request.arguments["shortcutName"], shortcutName.isSafeShortcutName else {
            return .denied(code: .preconditionFailed)
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
        let result = try runner.run(command)
        if result.timedOut {
            return .failed(code: .timeout, retryable: true)
        }
        if result.exitCode != 0 {
            return .failed(code: .preconditionFailed, retryable: false)
        }
        return .completed(resultCode: "apple_shortcut_completed", undoToken: nil)
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

private extension Data {
    func prefixBytes(_ maxBytes: Int) -> Data {
        guard count > maxBytes else { return self }
        return Data(prefix(maxBytes))
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
            !contains("..") &&
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
