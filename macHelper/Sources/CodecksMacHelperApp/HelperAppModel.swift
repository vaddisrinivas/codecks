import AppKit
@preconcurrency import ApplicationServices
import CodecksMacHelper
import SwiftUI

nonisolated(unsafe) private let accessibilityPromptKey = kAXTrustedCheckOptionPrompt

@MainActor
final class HelperAppModel: ObservableObject {
    @Published private(set) var snapshot = HelperSystemSnapshot(
        service: .unknown,
        configuration: .missing,
        launchAtLogin: .notConfigured,
        listener: .unknown,
        legacyPairing: .none,
        accessibilityGranted: false
    )
    @Published private(set) var lastChecked = Date()
    @Published var notice: String?

    private let reader = HelperSystemStatusReader()

    var menuBarSymbol: String { HelperStatusModel(snapshot: snapshot).symbol }

    func refresh() {
        let reader = reader
        Task {
            let updated = await Task.detached(priority: .utility) { reader.read() }.value
            snapshot = updated
            lastChecked = Date()
        }
    }

    func requestAccessibility() {
        let key = accessibilityPromptKey.takeUnretainedValue() as String
        _ = AXIsProcessTrustedWithOptions([key: true] as CFDictionary)
        notice = "Follow the macOS prompt, then check again."
    }

    func openInstallFolder() {
        let url = FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent("Library/Application Support/CodecksMacHelper", isDirectory: true)
        NSWorkspace.shared.open(url)
    }

    func copyRedactedDiagnostics() {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(RedactedHelperDiagnostics().text(for: snapshot), forType: .string)
        notice = "Redacted diagnostics copied."
    }
}
