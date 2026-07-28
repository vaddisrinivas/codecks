import XCTest
@testable import CodecksMacHelper

final class AppleShortcutsHandlerTests: XCTestCase {
    func testBuildsShortcutsArgvWithoutShell() throws {
        let runner = FakeCommandRunner(
            result: CommandResult(exitCode: 0, stdout: Data("ok".utf8), stderr: Data(), timedOut: false)
        )
        let handler = AppleShortcutsActionHandler(runner: runner)

        let outcome = try handler.execute(
            ReactiveExecuteRequest(
                type: "execute",
                actionId: "apple_shortcuts.run",
                actionRevision: "rev-1",
                operationId: "op-1",
                idempotencyKey: "idem-1",
                timeoutMillis: 5_000,
                cancellationToken: "op-1",
                arguments: [
                    "shortcutName": "Daily Standup",
                    "inputPath": "/Users/me/Library/Caches/codecks/input.json"
                ]
            ),
            nowMillis: 1_000
        )

        XCTAssertEqual(outcome, .completed(resultCode: "apple_shortcut_completed", undoToken: nil))
        XCTAssertEqual(runner.commands.single!.executable, "/usr/bin/shortcuts")
        XCTAssertEqual(
            runner.commands.single!.arguments,
            ["run", "Daily Standup", "--input-path", "/Users/me/Library/Caches/codecks/input.json"]
        )
    }

    func testRejectsUnsafeShortcutNameAndInputPath() throws {
        let runner = FakeCommandRunner(
            result: CommandResult(exitCode: 0, stdout: Data(), stderr: Data(), timedOut: false)
        )
        let handler = AppleShortcutsActionHandler(runner: runner)

        let badName = try handler.execute(request(name: "Daily\nStandup"), nowMillis: 1_000)
        let badPath = try handler.execute(request(name: "Daily Standup", inputPath: "../input.json"), nowMillis: 1_000)

        XCTAssertEqual(badName, .denied(code: .preconditionFailed))
        XCTAssertEqual(badPath, .denied(code: .preconditionFailed))
        XCTAssertTrue(runner.commands.isEmpty)
    }

    func testTimeoutAndNonzeroExitFailWithoutReceiptSuccess() throws {
        let timeout = AppleShortcutsActionHandler(
            runner: FakeCommandRunner(result: CommandResult(exitCode: 0, stdout: Data(), stderr: Data(), timedOut: true))
        )
        let failed = AppleShortcutsActionHandler(
            runner: FakeCommandRunner(result: CommandResult(exitCode: 1, stdout: Data(), stderr: Data("no".utf8), timedOut: false))
        )

        XCTAssertEqual(try timeout.execute(request(name: "Daily Standup"), nowMillis: 1_000), .failed(code: .timeout, retryable: true))
        XCTAssertEqual(try failed.execute(request(name: "Daily Standup"), nowMillis: 1_000), .failed(code: .preconditionFailed, retryable: false))
    }

    private func request(name: String, inputPath: String? = nil) -> ReactiveExecuteRequest {
        ReactiveExecuteRequest(
            type: "execute",
            actionId: "apple_shortcuts.run",
            actionRevision: "rev-1",
            operationId: "op-1",
            idempotencyKey: "idem-1",
            timeoutMillis: 5_000,
            cancellationToken: "op-1",
            arguments: [
                "shortcutName": name,
                "inputPath": inputPath
            ].compactMapValues { $0 }
        )
    }
}

private final class FakeCommandRunner: CommandRunning {
    var commands: [AppleShortcutsCommand] = []
    private let result: CommandResult

    init(result: CommandResult) {
        self.result = result
    }

    func run(_ command: AppleShortcutsCommand) throws -> CommandResult {
        commands.append(command)
        return result
    }
}

private extension Array {
    var single: Element? {
        count == 1 ? self[0] : nil
    }
}
