import ApplicationServices
import Foundation

public enum HelperServiceState: String, Equatable, Sendable {
    case notInstalled
    case disabled
    case unloaded
    case stopped
    case running
    case unknown
}

public enum HelperConfigurationState: String, Equatable, Sendable {
    case missing
    case valid
    case invalid
}

public enum HelperLaunchAtLoginState: String, Equatable, Sendable {
    case notConfigured
    case configured
    case malformed
}

public enum HelperListenerState: String, Equatable, Sendable {
    case unknown
    case verified
    case wrongOwner
    case unreachable
}

public enum LegacyPairingState: String, Equatable, Sendable {
    case none
    case globalSharedSecret
    case unreadable
}

public struct HelperSystemSnapshot: Equatable, Sendable {
    public var service: HelperServiceState
    public var configuration: HelperConfigurationState
    public var launchAtLogin: HelperLaunchAtLoginState
    public var listener: HelperListenerState
    public var legacyPairing: LegacyPairingState
    public var accessibilityGranted: Bool

    public init(
        service: HelperServiceState,
        configuration: HelperConfigurationState,
        launchAtLogin: HelperLaunchAtLoginState,
        listener: HelperListenerState,
        legacyPairing: LegacyPairingState,
        accessibilityGranted: Bool
    ) {
        self.service = service
        self.configuration = configuration
        self.launchAtLogin = launchAtLogin
        self.listener = listener
        self.legacyPairing = legacyPairing
        self.accessibilityGranted = accessibilityGranted
    }

    public var isReady: Bool {
        configuration == .valid && service == .running && listener == .verified
    }
}

public struct HelperStatusModel: Equatable, Sendable {
    public let snapshot: HelperSystemSnapshot

    public init(snapshot: HelperSystemSnapshot) { self.snapshot = snapshot }

    public var title: String {
        if snapshot.isReady { return "Ready" }
        if snapshot.configuration == .valid && snapshot.service == .running && snapshot.listener == .unknown { return "Checking…" }
        return "Setup needed"
    }
    public var symbol: String {
        snapshot.isReady ? "rectangle.connected.to.line.below" : "rectangle.badge.exclamationmark"
    }
}

public struct LaunchAgentProbe: Equatable, Sendable {
    public var loaded: Bool
    public var running: Bool
    public var disabled: Bool

    public init(loaded: Bool, running: Bool, disabled: Bool) {
        self.loaded = loaded
        self.running = running
        self.disabled = disabled
    }
}

public enum LaunchAgentOutputParser {
    public static func parseService(_ output: String, disabled: Bool) -> LaunchAgentProbe {
        let loaded = output.contains("state =") || output.contains("pid =")
        let stateRunning = output.range(of: #"state\s*=\s*running"#, options: .regularExpression) != nil
        let hasPositivePID = output.range(of: #"pid\s*=\s*[1-9][0-9]*"#, options: .regularExpression) != nil
        return LaunchAgentProbe(loaded: loaded, running: stateRunning && hasPositivePID, disabled: disabled)
    }

    public static func isDisabled(_ output: String, label: String) -> Bool {
        output.range(
            of: #""# + NSRegularExpression.escapedPattern(for: label) + #""\s*=>\s*true"#,
            options: .regularExpression
        ) != nil
    }
}

public struct HelperSystemStatusReader: Sendable {
    public static let launchAgentLabel = "app.codecks.mac-helper"

    private let homeDirectory: URL
    private let launchAgentProbe: @Sendable () -> LaunchAgentProbe
    private let listenerProbe: @Sendable (UInt16) -> HelperListenerState
    private let accessibilityIsGranted: @Sendable () -> Bool

    public init(
        homeDirectory: URL = FileManager.default.homeDirectoryForCurrentUser,
        launchAgentProbe: (@Sendable () -> LaunchAgentProbe)? = nil,
        listenerProbe: (@Sendable (UInt16) -> HelperListenerState)? = nil,
        accessibilityIsGranted: @escaping @Sendable () -> Bool = { AXIsProcessTrusted() }
    ) {
        self.homeDirectory = homeDirectory
        self.launchAgentProbe = launchAgentProbe ?? Self.systemLaunchAgentProbe
        self.listenerProbe = listenerProbe ?? Self.systemListenerProbe
        self.accessibilityIsGranted = accessibilityIsGranted
    }

    public func read() -> HelperSystemSnapshot {
        let plist = homeDirectory.appendingPathComponent("Library/LaunchAgents/\(Self.launchAgentLabel).plist")
        let configURL = homeDirectory.appendingPathComponent("Library/Application Support/CodecksMacHelper/helper.json")
        let login = inspectLaunchAtLogin(at: plist)
        let config = inspectConfig(at: configURL)
        let process = launchAgentProbe()
        let service = serviceState(login: login, process: process)
        let listener: HelperListenerState = service == .running && config.state == .valid && config.port != nil
            ? listenerProbe(config.port!)
            : .unknown
        return HelperSystemSnapshot(
            service: service,
            configuration: config.state,
            launchAtLogin: login,
            listener: listener,
            legacyPairing: config.legacyPairing,
            accessibilityGranted: accessibilityIsGranted()
        )
    }

    private func serviceState(login: HelperLaunchAtLoginState, process: LaunchAgentProbe) -> HelperServiceState {
        guard login != .notConfigured else { return .notInstalled }
        if process.disabled { return .disabled }
        guard process.loaded else { return .unloaded }
        return process.running ? .running : .stopped
    }

    private func inspectLaunchAtLogin(at url: URL) -> HelperLaunchAtLoginState {
        guard FileManager.default.fileExists(atPath: url.path) else { return .notConfigured }
        guard
            let data = try? Data(contentsOf: url),
            let object = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any],
            object["Label"] as? String == Self.launchAgentLabel,
            object["RunAtLoad"] as? Bool == true
        else { return .malformed }
        return .configured
    }

    private func inspectConfig(at url: URL) -> (state: HelperConfigurationState, legacyPairing: LegacyPairingState, port: UInt16?) {
        guard FileManager.default.fileExists(atPath: url.path) else { return (.missing, .none, nil) }
        guard
            let data = try? Data(contentsOf: url),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return (.invalid, .unreadable, nil) }
        let legacy: LegacyPairingState = object["sharedSecretHex"] == nil ? .none : .globalSharedSecret
        guard let config = try? ReactiveMacHelperRuntimeConfig.environment(["CODECKS_HELPER_CONFIG": url.path]) else {
            return (.invalid, legacy, nil)
        }
        return (.valid, legacy, config.port)
    }

    private static func systemLaunchAgentProbe() -> LaunchAgentProbe {
        let runner = ProcessCommandRunner()
        let service = try? runner.run(AppleShortcutsCommand(
            executable: "/bin/launchctl",
            arguments: ["print", "gui/\(getuid())/\(launchAgentLabel)"],
            timeoutMillis: 1_000,
            maxOutputBytes: 64 * 1024
        ))
        let disabled = try? runner.run(AppleShortcutsCommand(
            executable: "/bin/launchctl",
            arguments: ["print-disabled", "gui/\(getuid())"],
            timeoutMillis: 1_000,
            maxOutputBytes: 64 * 1024
        ))
        let disabledOutput = disabled.flatMap { $0.timedOut ? nil : String(data: $0.stdout, encoding: .utf8) } ?? ""
        let isDisabled = LaunchAgentOutputParser.isDisabled(disabledOutput, label: launchAgentLabel)
        guard let service, !service.timedOut, service.exitCode == 0 else {
            return LaunchAgentProbe(loaded: false, running: false, disabled: isDisabled)
        }
        return LaunchAgentOutputParser.parseService(String(decoding: service.stdout, as: UTF8.self), disabled: isDisabled)
    }

    private static func systemListenerProbe(port: UInt16) -> HelperListenerState {
        let runner = ProcessCommandRunner()
        guard let result = try? runner.run(AppleShortcutsCommand(
            executable: "/usr/sbin/lsof",
            arguments: ["+c", "0", "-nP", "-a", "-iTCP:\(port)", "-sTCP:LISTEN", "-Fpc"],
            timeoutMillis: 1_000,
            maxOutputBytes: 16 * 1024
        )), !result.timedOut, !result.outputLimitExceeded else { return .unknown }
        guard result.exitCode == 0 else { return .unreachable }
        return ListenerOwnerParser.state(String(decoding: result.stdout, as: UTF8.self))
    }
}

public enum ListenerOwnerParser {
    public static func state(_ lsofFields: String) -> HelperListenerState {
        let commands = lsofFields.split(whereSeparator: \.isNewline)
            .filter { $0.first == "c" }
            .map { String($0.dropFirst()) }
        guard !commands.isEmpty else { return .unreachable }
        return commands.allSatisfy { $0 == "codecks-mac-helper" } ? .verified : .wrongOwner
    }
}

public struct RedactedHelperDiagnostics {
    public init() {}

    public func text(for snapshot: HelperSystemSnapshot) -> String {
        [
            "Codecks Mac Helper diagnostics",
            "Ready: \(snapshot.isReady)",
            "Service: \(snapshot.service.rawValue)",
            "Listener: \(snapshot.listener.rawValue)",
            "Configuration: \(snapshot.configuration.rawValue)",
            "Launch at login: \(snapshot.launchAtLogin.rawValue)",
            "Legacy pairing: \(snapshot.legacyPairing.rawValue)",
            "Setup app Accessibility: \(snapshot.accessibilityGranted ? "granted" : "needed")",
            "Secrets: redacted"
        ].joined(separator: "\n")
    }
}
