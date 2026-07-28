import XCTest
@testable import CodecksMacHelper

final class MacCapabilityHandlersTests: XCTestCase {
    func testSpotlightUsesMdfindArgvAndCapsResults() throws {
        let runner = RecordingCommandRunner(
            result: CommandResult(
                exitCode: 0,
                stdout: Data("/tmp/a\n/tmp/b\n/tmp/c\n".utf8),
                stderr: Data(),
                timedOut: false
            )
        )
        let handler = SpotlightSearchActionHandler(runner: runner)
        let outcome = try handler.execute(
            ReactiveExecuteRequest(
                type: "execute",
                actionId: "spotlight.search",
                actionRevision: "rev-1",
                operationId: "op-1",
                idempotencyKey: "idem-1",
                timeoutMillis: 5_000,
                cancellationToken: "cancel-1",
                arguments: ["query": "Quarterly Deck", "maxResults": "2"]
            ),
            nowMillis: 1_000
        )

        XCTAssertEqual(
            runner.commands.single(),
            AppleShortcutsCommand(
                executable: "/usr/bin/mdfind",
                arguments: ["-name", "Quarterly Deck"],
                timeoutMillis: 5_000,
                maxOutputBytes: 64 * 1024
            )
        )
        XCTAssertEqual(outcome, .completed(resultCode: "spotlight_results_2", undoToken: nil))
    }

    func testSpotlightRejectsShellShapedQuery() throws {
        let handler = SpotlightSearchActionHandler(runner: RecordingCommandRunner())
        let outcome = try handler.execute(
            request(actionId: "spotlight.search", arguments: ["query": "Deck; rm", "maxResults": "2"]),
            nowMillis: 1_000
        )

        XCTAssertEqual(outcome, .denied(code: .preconditionFailed))
    }

    func testBrightnessDeniesUnsupportedOrUninstalledAdapter() throws {
        let unsupported = MonitorBrightnessActionHandler()
        XCTAssertEqual(
            try unsupported.execute(
                request(
                    actionId: "monitor_brightness.set",
                    arguments: ["adapterId": "bad adapter", "displayId": "main", "percent": "50"]
                ),
                nowMillis: 1_000
            ),
            .denied(code: .preconditionFailed)
        )

        let missing = MonitorBrightnessActionHandler(executableExists: { _ in false })
        XCTAssertEqual(
            try missing.execute(
                request(
                    actionId: "monitor_brightness.set",
                    arguments: ["adapterId": "betterdisplaycli", "displayId": "main", "percent": "50"]
                ),
                nowMillis: 1_000
            ),
            .failed(code: .preconditionFailed, retryable: false)
        )
    }

    func testBrightnessUsesBetterDisplayArgvWithoutShell() throws {
        let runner = RecordingCommandRunner()
        let handler = MonitorBrightnessActionHandler(
            executableExists: { $0 == "/opt/homebrew/bin/betterdisplaycli" },
            runner: runner
        )

        let outcome = try handler.execute(
            request(
                actionId: "monitor_brightness.set",
                arguments: ["adapterId": "betterdisplaycli", "displayId": "Studio Display", "percent": "65"]
            ),
            nowMillis: 1_000
        )

        XCTAssertEqual(
            runner.commands.single(),
            AppleShortcutsCommand(
                executable: "/opt/homebrew/bin/betterdisplaycli",
                arguments: ["set", "-namelike=Studio Display", "-brightness=65%"],
                timeoutMillis: 5_000,
                maxOutputBytes: 16 * 1024
            )
        )
        XCTAssertEqual(outcome, .completed(resultCode: "monitor_brightness_set", undoToken: nil))
    }

    func testBrightnessTimeoutIsRetryable() throws {
        let handler = MonitorBrightnessActionHandler(
            executableExists: { _ in true },
            runner: RecordingCommandRunner(
                result: CommandResult(exitCode: 0, stdout: Data(), stderr: Data(), timedOut: true)
            )
        )

        let outcome = try handler.execute(
            request(
                actionId: "monitor_brightness.set",
                arguments: ["adapterId": "betterdisplaycli", "displayId": "Studio Display", "percent": "65"]
            ),
            nowMillis: 1_000
        )

        XCTAssertEqual(outcome, .failed(code: .timeout, retryable: true))
    }

    func testAccessibilityDiscoveryIsBoundedAndPermissionAware() throws {
        let snapshotter = FakeAccessibilitySnapshotter(count: 99)
        let handler = AccessibilityDiscoveryActionHandler(snapshotter: snapshotter)
        let outcome = try handler.execute(
            request(
                actionId: "accessibility.discover",
                arguments: ["frontAppBundleId": "com.apple.finder", "maxActions": "3"]
            ),
            nowMillis: 1_000
        )

        XCTAssertEqual(snapshotter.requests, [FakeAccessibilityRequest(bundleId: "com.apple.finder", maxActions: 3)])
        XCTAssertEqual(outcome, .completed(resultCode: "accessibility_actions_3", undoToken: nil))

        let denied = AccessibilityDiscoveryActionHandler(snapshotter: FakeAccessibilitySnapshotter(error: ReactiveValidationError("no ax")))
        XCTAssertEqual(
            try denied.execute(
                request(
                    actionId: "accessibility.discover",
                    arguments: ["frontAppBundleId": "com.apple.finder", "maxActions": "3"]
                ),
                nowMillis: 1_000
            ),
            .failed(code: .preconditionFailed, retryable: false)
        )
    }

    private func request(actionId: String, arguments: [String: String]) -> ReactiveExecuteRequest {
        ReactiveExecuteRequest(
            type: "execute",
            actionId: actionId,
            actionRevision: "rev-1",
            operationId: "op-1",
            idempotencyKey: "idem-1",
            timeoutMillis: 5_000,
            cancellationToken: "cancel-1",
            arguments: arguments
        )
    }
}

private final class RecordingCommandRunner: CommandRunning {
    var commands: [AppleShortcutsCommand] = []
    private let result: CommandResult

    init(result: CommandResult = CommandResult(exitCode: 0, stdout: Data(), stderr: Data(), timedOut: false)) {
        self.result = result
    }

    func run(_ command: AppleShortcutsCommand) throws -> CommandResult {
        commands.append(command)
        return result
    }
}

private struct FakeAccessibilityRequest: Equatable {
    var bundleId: String
    var maxActions: Int
}

private final class FakeAccessibilitySnapshotter: AccessibilitySnapshotting {
    var requests: [FakeAccessibilityRequest] = []
    private let count: Int
    private let error: Error?

    init(count: Int = 0, error: Error? = nil) {
        self.count = count
        self.error = error
    }

    func discover(frontAppBundleId: String, maxActions: Int) throws -> Int {
        if let error {
            throw error
        }
        requests.append(FakeAccessibilityRequest(bundleId: frontAppBundleId, maxActions: maxActions))
        return count
    }
}

private extension Array {
    func single(file: StaticString = #filePath, line: UInt = #line) -> Element {
        XCTAssertEqual(count, 1, file: file, line: line)
        return self[0]
    }
}
