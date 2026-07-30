import XCTest
@testable import CodecksMacHelper

final class AppleShortcutsCatalogTests: XCTestCase {
    func testImporterDeduplicatesAndPreservesOrder() throws {
        let shortcuts = try AppleShortcutsCatalogImporter().parse(
            Data("Daily Standup\nBuild Release\nDaily Standup\n".utf8)
        )

        XCTAssertEqual(shortcuts.map(\.name), ["Daily Standup", "Build Release"])
    }

    func testReaderUsesBoundedListCommand() throws {
        let runner = CatalogRunner(
            result: CommandResult(
                exitCode: 0,
                stdout: Data("Daily Standup\n".utf8),
                stderr: Data(),
                timedOut: false
            )
        )
        let shortcuts = try AppleShortcutsCatalogReader(
            runner: runner,
            executableAvailable: { true }
        ).load()

        XCTAssertEqual(shortcuts.map(\.name), ["Daily Standup"])
        XCTAssertEqual(runner.commands.single?.executable, "/usr/bin/shortcuts")
        XCTAssertEqual(runner.commands.single?.arguments, ["list"])
        XCTAssertEqual(runner.commands.single?.maxOutputBytes, 64 * 1024)
    }

    func testReaderFailsClosedForUnavailableTimeoutAndTruncation() {
        XCTAssertThrowsError(
            try AppleShortcutsCatalogReader(
                runner: CatalogRunner(result: .success),
                executableAvailable: { false }
            ).load()
        )
        XCTAssertThrowsError(
            try AppleShortcutsCatalogReader(
                runner: CatalogRunner(result: .timedOut),
                executableAvailable: { true }
            ).load()
        )
        XCTAssertThrowsError(
            try AppleShortcutsCatalogReader(
                runner: CatalogRunner(result: .truncated),
                executableAvailable: { true }
            ).load()
        )
    }
}

private final class CatalogRunner: CommandRunning {
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

private extension CommandResult {
    static let success = CommandResult(
        exitCode: 0,
        stdout: Data(),
        stderr: Data(),
        timedOut: false
    )
    static let timedOut = CommandResult(
        exitCode: 0,
        stdout: Data(),
        stderr: Data(),
        timedOut: true
    )
    static let truncated = CommandResult(
        exitCode: 0,
        stdout: Data(),
        stderr: Data(),
        timedOut: false,
        outputLimitExceeded: true
    )
}

private extension Array {
    var single: Element? {
        count == 1 ? self[0] : nil
    }
}
