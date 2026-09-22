import Foundation
import XCTest
@testable import CodecksMacHelper

final class HelperSystemStatusTests: XCTestCase {
    func testReadyRequiresValidConfigRunningProcessAndReachableListener() throws {
        let home = try fixtureHome(validConfig: true, validPlist: true)
        let ready = reader(home: home, process: .init(loaded: true, running: true, disabled: false), listener: .verified).read()
        let stopped = reader(home: home, process: .init(loaded: true, running: false, disabled: false), listener: .verified).read()
        let unhealthy = reader(home: home, process: .init(loaded: true, running: true, disabled: false), listener: .unreachable).read()

        XCTAssertTrue(ready.isReady)
        XCTAssertEqual(ready.service, .running)
        XCTAssertFalse(stopped.isReady)
        XCTAssertEqual(stopped.service, .stopped)
        XCTAssertFalse(unhealthy.isReady)
    }

    func testWrongListenerOwnerNeverReportsReady() throws {
        let home = try fixtureHome(validConfig: true, validPlist: true)
        let snapshot = reader(home: home, process: .init(loaded: true, running: true, disabled: false), listener: .wrongOwner).read()

        XCTAssertFalse(snapshot.isReady)
        XCTAssertEqual(HelperStatusModel(snapshot: snapshot).title, "Setup needed")
        XCTAssertEqual(ListenerOwnerParser.state("p42\ncpython3\n"), .wrongOwner)
        XCTAssertEqual(ListenerOwnerParser.state("p42\nccodecks-mac-helper\n"), .verified)
    }

    func testRunningWithUnknownListenerStaysChecking() throws {
        let home = try fixtureHome(validConfig: true, validPlist: true)
        let snapshot = reader(home: home, process: .init(loaded: true, running: true, disabled: false), listener: .unknown).read()

        XCTAssertFalse(snapshot.isReady)
        XCTAssertEqual(HelperStatusModel(snapshot: snapshot).title, "Checking…")
    }

    func testRunningWithInvalidConfigNeedsSetup() throws {
        let home = try fixtureHome(validConfig: false, validPlist: true)
        let snapshot = reader(home: home, process: .init(loaded: true, running: true, disabled: false)).read()

        XCTAssertEqual(snapshot.configuration, .invalid)
        XCTAssertEqual(HelperStatusModel(snapshot: snapshot).title, "Setup needed")
    }

    func testRunningWithMissingConfigNeedsSetup() throws {
        let home = try fixtureHome(validConfig: true, validPlist: true)
        try FileManager.default.removeItem(at: home.appendingPathComponent("Library/Application Support/CodecksMacHelper/helper.json"))
        let snapshot = reader(home: home, process: .init(loaded: true, running: true, disabled: false)).read()

        XCTAssertEqual(snapshot.configuration, .missing)
        XCTAssertEqual(HelperStatusModel(snapshot: snapshot).title, "Setup needed")
    }

    func testLoadedStoppedDisabledAndUnloadedAreDistinct() throws {
        let home = try fixtureHome(validConfig: true, validPlist: true)
        XCTAssertEqual(reader(home: home, process: .init(loaded: true, running: false, disabled: false)).read().service, .stopped)
        XCTAssertEqual(reader(home: home, process: .init(loaded: false, running: false, disabled: true)).read().service, .disabled)
        XCTAssertEqual(reader(home: home, process: .init(loaded: false, running: false, disabled: false)).read().service, .unloaded)
    }

    func testLaunchParserRequiresRunningStateAndPositivePID() {
        XCTAssertTrue(LaunchAgentOutputParser.parseService("state = running\npid = 42", disabled: false).running)
        XCTAssertFalse(LaunchAgentOutputParser.parseService("state = waiting\npid = 42", disabled: false).running)
        XCTAssertFalse(LaunchAgentOutputParser.parseService("state = running", disabled: false).running)
        XCTAssertTrue(LaunchAgentOutputParser.isDisabled(#""app.codecks.mac-helper" => true"#, label: "app.codecks.mac-helper"))
    }

    func testMalformedConfigAndPlistFailClosed() throws {
        let home = try fixtureHome(validConfig: false, validPlist: false)
        let snapshot = reader(home: home, process: .init(loaded: true, running: true, disabled: false), listener: .verified).read()

        XCTAssertEqual(snapshot.configuration, .invalid)
        XCTAssertEqual(snapshot.launchAtLogin, .malformed)
        XCTAssertEqual(snapshot.legacyPairing, .unreadable)
        XCTAssertEqual(snapshot.listener, .unknown)
        XCTAssertFalse(snapshot.isReady)
    }

    func testMissingInstallAndConfigAreTruthful() {
        let home = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let snapshot = reader(home: home, process: .init(loaded: false, running: false, disabled: false)).read()

        XCTAssertEqual(snapshot.service, .notInstalled)
        XCTAssertEqual(snapshot.configuration, .missing)
        XCTAssertEqual(snapshot.launchAtLogin, .notConfigured)
        XCTAssertEqual(snapshot.legacyPairing, .none)
    }

    func testDiagnosticsRedactSecretAndStatusModelUsesSharedReadiness() throws {
        let home = try fixtureHome(validConfig: true, validPlist: true)
        let snapshot = reader(home: home, process: .init(loaded: true, running: true, disabled: false), listener: .verified).read()
        let diagnostics = RedactedHelperDiagnostics().text(for: snapshot)

        XCTAssertEqual(HelperStatusModel(snapshot: snapshot).title, "Ready")
        XCTAssertFalse(diagnostics.contains(String(repeating: "ab", count: 32)))
        XCTAssertTrue(diagnostics.contains("Secrets: redacted"))
    }

    private func reader(
        home: URL,
        process: LaunchAgentProbe,
        listener: HelperListenerState = .unknown
    ) -> HelperSystemStatusReader {
        HelperSystemStatusReader(
            homeDirectory: home,
            launchAgentProbe: { process },
            listenerProbe: { _ in listener },
            accessibilityIsGranted: { false }
        )
    }

    private func fixtureHome(validConfig: Bool, validPlist: Bool) throws -> URL {
        let home = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let agents = home.appendingPathComponent("Library/LaunchAgents")
        let support = home.appendingPathComponent("Library/Application Support/CodecksMacHelper")
        try FileManager.default.createDirectory(at: agents, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: support, withIntermediateDirectories: true)
        let plist = validPlist
            ? #"<?xml version="1.0" encoding="UTF-8"?><plist version="1.0"><dict><key>Label</key><string>app.codecks.mac-helper</string><key>RunAtLoad</key><true/></dict></plist>"#
            : "not plist"
        try Data(plist.utf8).write(to: agents.appendingPathComponent("app.codecks.mac-helper.plist"))
        let config = validConfig
            ? #"{"port":47321,"macId":"mac","helperId":"helper","publicKeyFingerprint":""# + String(repeating: "a", count: 64) + #"","sharedSecretHex":""# + String(repeating: "ab", count: 32) + #""}"#
            : "not json"
        try Data(config.utf8).write(to: support.appendingPathComponent("helper.json"))
        return home
    }
}
