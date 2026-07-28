import ApplicationServices
import AppKit
import Foundation

public struct SpotlightSearchActionHandler: ReactiveHelperActionHandler {
    private let runner: CommandRunning

    public init(runner: CommandRunning = ProcessCommandRunner()) {
        self.runner = runner
    }

    public func execute(_ request: ReactiveExecuteRequest, nowMillis: Int64) throws -> ReactiveHelperActionOutcome {
        guard request.actionId == "spotlight.search" else {
            return .denied(code: .unsupportedCapability)
        }
        guard let query = request.arguments["query"], query.isSafeSpotlightQuery else {
            return .denied(code: .preconditionFailed)
        }
        let maxResults = min(max(Int(request.arguments["maxResults"] ?? "8") ?? 8, 1), 20)
        let command = AppleShortcutsCommand(
            executable: "/usr/bin/mdfind",
            arguments: ["-name", query],
            timeoutMillis: min(max(request.timeoutMillis, 500), 10_000),
            maxOutputBytes: 64 * 1024
        )
        let result = try runner.run(command)
        if result.timedOut {
            return .failed(code: .timeout, retryable: true)
        }
        if result.exitCode != 0 {
            return .failed(code: .preconditionFailed, retryable: false)
        }
        let matches = String(decoding: result.stdout, as: UTF8.self)
            .split(separator: "\n")
            .prefix(maxResults)
            .count
        return .completed(resultCode: "spotlight_results_\(matches)", undoToken: nil)
    }
}

public struct MonitorBrightnessActionHandler: ReactiveHelperActionHandler {
    private let executableExists: (String) -> Bool
    private let runner: CommandRunning

    public init(
        executableExists: @escaping (String) -> Bool = { FileManager.default.isExecutableFile(atPath: $0) },
        runner: CommandRunning = ProcessCommandRunner()
    ) {
        self.executableExists = executableExists
        self.runner = runner
    }

    public func execute(_ request: ReactiveExecuteRequest, nowMillis: Int64) throws -> ReactiveHelperActionOutcome {
        guard request.actionId == "monitor_brightness.set" else {
            return .denied(code: .unsupportedCapability)
        }
        guard
            let adapterId = request.arguments["adapterId"], adapterId.isSafeAdapterId,
            let displayId = request.arguments["displayId"], displayId.isSafeSingleLine(max: 96),
            let percentRaw = request.arguments["percent"],
            let percent = Int(percentRaw),
            (0...100).contains(percent)
        else {
            return .denied(code: .preconditionFailed)
        }
        guard adapterId == "betterdisplaycli" else {
            return .denied(code: .unsupportedCapability)
        }
        let candidates = ["/opt/homebrew/bin/betterdisplaycli", "/usr/local/bin/betterdisplaycli"]
        guard let executable = candidates.first(where: executableExists) else {
            return .failed(code: .preconditionFailed, retryable: false)
        }
        let result = try runner.run(
            AppleShortcutsCommand(
                executable: executable,
                arguments: [
                    "set",
                    "-namelike=\(displayId)",
                    "-brightness=\(percent)%"
                ],
                timeoutMillis: min(max(request.timeoutMillis, 500), 10_000),
                maxOutputBytes: 16 * 1024
            )
        )
        if result.timedOut {
            return .failed(code: .timeout, retryable: true)
        }
        if result.exitCode != 0 {
            return .failed(code: .preconditionFailed, retryable: false)
        }
        return .completed(resultCode: "monitor_brightness_set", undoToken: nil)
    }
}

public protocol AccessibilitySnapshotting {
    func discover(frontAppBundleId: String, maxActions: Int) throws -> Int
}

public struct SystemAccessibilitySnapshotter: AccessibilitySnapshotting {
    public init() {}

    public func discover(frontAppBundleId: String, maxActions: Int) throws -> Int {
        guard AXIsProcessTrusted() else {
            throw ReactiveValidationError("accessibility permission required")
        }
        guard let app = NSRunningApplication.runningApplications(withBundleIdentifier: frontAppBundleId).first else {
            return 0
        }
        let root = AXUIElementCreateApplication(app.processIdentifier)
        var traversal = AccessibilityTraversal(maxActions: maxActions, maxNodes: 200)
        return traversal.countActions(root)
    }
}

private struct AccessibilityTraversal {
    let maxActions: Int
    let maxNodes: Int
    private var visitedNodes = 0
    private var actionCount = 0

    init(maxActions: Int, maxNodes: Int) {
        self.maxActions = maxActions
        self.maxNodes = maxNodes
    }

    mutating func countActions(_ element: AXUIElement) -> Int {
        visit(element)
        return min(actionCount, maxActions)
    }

    private mutating func visit(_ element: AXUIElement) {
        guard visitedNodes < maxNodes, actionCount < maxActions else { return }
        visitedNodes += 1

        var actionNames: CFArray?
        if AXUIElementCopyActionNames(element, &actionNames) == .success,
           let names = actionNames as? [String] {
            actionCount += names.count
            if actionCount >= maxActions { return }
        }

        var childrenValue: CFTypeRef?
        guard AXUIElementCopyAttributeValue(element, kAXChildrenAttribute as CFString, &childrenValue) == .success,
              let children = childrenValue as? [AXUIElement] else {
            return
        }
        for child in children {
            visit(child)
            if actionCount >= maxActions { return }
        }
    }
}

public struct AccessibilityDiscoveryActionHandler: ReactiveHelperActionHandler {
    private let snapshotter: AccessibilitySnapshotting

    public init(snapshotter: AccessibilitySnapshotting = SystemAccessibilitySnapshotter()) {
        self.snapshotter = snapshotter
    }

    public func execute(_ request: ReactiveExecuteRequest, nowMillis: Int64) throws -> ReactiveHelperActionOutcome {
        guard request.actionId == "accessibility.discover" else {
            return .denied(code: .unsupportedCapability)
        }
        guard let bundleId = request.arguments["frontAppBundleId"], bundleId.isSafeBundleId else {
            return .denied(code: .preconditionFailed)
        }
        let maxActions = min(max(Int(request.arguments["maxActions"] ?? "8") ?? 8, 1), 20)
        do {
            let count = try snapshotter.discover(frontAppBundleId: bundleId, maxActions: maxActions)
            return .completed(resultCode: "accessibility_actions_\(min(count, maxActions))", undoToken: nil)
        } catch {
            return .failed(code: .preconditionFailed, retryable: false)
        }
    }
}

private extension String {
    var isSafeSpotlightQuery: Bool {
        isSafeSingleLine(max: 96) &&
            !contains("..") &&
            !contains("/") &&
            !contains("\\") &&
            !contains("~") &&
            !contains("$") &&
            !contains("`") &&
            !contains(";") &&
            !contains("|") &&
            !contains("&") &&
            !contains("<") &&
            !contains(">") &&
            !contains("'") &&
            !contains("\"")
    }

    var isSafeAdapterId: Bool {
        range(of: "^[A-Za-z0-9_.-]{1,64}$", options: .regularExpression) != nil
    }

    var isSafeBundleId: Bool {
        range(of: "^[A-Za-z0-9][A-Za-z0-9_.-]{1,127}$", options: .regularExpression) != nil &&
            !contains("..")
    }

    func isSafeSingleLine(max: Int) -> Bool {
        !isEmpty &&
            count <= max &&
            !unicodeScalars.contains { scalar in
                CharacterSet.controlCharacters.contains(scalar) || scalar.value == 0
            }
    }
}
