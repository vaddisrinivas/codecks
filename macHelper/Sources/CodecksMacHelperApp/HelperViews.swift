import CodecksMacHelper
import SwiftUI

struct HelperMenuView: View {
    @ObservedObject var model: HelperAppModel
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                StatusHeader(snapshot: model.snapshot)
                Divider()
                PermissionRow(title: "This app: Accessibility", granted: model.snapshot.accessibilityGranted) {
                    model.requestAccessibility()
                }
                Divider()
                Button("Open setup…") { openWindow(id: "setup") }
                    .keyboardShortcut(",")
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                Button("Check again") { model.refresh() }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                Button("Quit Codecks Mac Helper") { NSApplication.shared.terminate(nil) }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .padding(16)
        }
        .frame(minWidth: 300, idealWidth: 360, maxWidth: 440, minHeight: 220, maxHeight: 620)
        .onAppear { model.refresh() }
    }
}

struct HelperSetupView: View {
    @ObservedObject var model: HelperAppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Codecks Mac Helper").font(.largeTitle.bold())
                    Text("Connect Codecks to this Mac without sending commands through a cloud service.")
                        .foregroundStyle(.secondary)
                }
                StatusSection(model: model)
                PermissionsSection(model: model)
                LegacySection(snapshot: model.snapshot)
                TroubleshootingSection(model: model)
            }
            .padding(28)
        }
        .background(Color(nsColor: .windowBackgroundColor))
        .onAppear { model.refresh() }
    }
}

private struct StatusHeader: View {
    let snapshot: HelperSystemSnapshot

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: snapshot.isReady ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                .foregroundStyle(snapshot.isReady ? Color.green : Color.orange)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(HelperStatusModel(snapshot: snapshot).title).font(.headline)
                Text(serviceDetail(snapshot.service)).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
        }
        .accessibilityElement(children: .combine)
    }
}

private struct StatusSection: View {
    @ObservedObject var model: HelperAppModel

    var body: some View {
        SetupSection(title: "Helper status", symbol: "server.rack") {
            StatusLine(label: "Service", value: serviceDetail(model.snapshot.service))
            StatusLine(label: "Launch at login", value: loginDetail(model.snapshot.launchAtLogin))
            StatusLine(label: "Configuration", value: configurationDetail(model.snapshot.configuration))
            StatusLine(label: "Local listener", value: listenerDetail(model.snapshot.listener))
            ViewThatFits(in: .horizontal) {
                HStack {
                    checkedLabel
                    Spacer()
                    checkButton
                }
                VStack(alignment: .leading, spacing: 8) { checkedLabel; checkButton }
            }
        }
    }

    private var checkedLabel: some View {
        Text("Checked \(model.lastChecked.formatted(date: .omitted, time: .shortened))")
            .font(.caption).foregroundStyle(.secondary)
    }

    private var checkButton: some View {
        Button("Check again") { model.refresh() }
            .controlSize(.large)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
    }
}

private struct PermissionsSection: View {
    @ObservedObject var model: HelperAppModel

    var body: some View {
        SetupSection(title: "Mac permissions", symbol: "hand.raised") {
            Text("Some Mac-control features need Accessibility. macOS tracks permission per executable; this check applies to this menu-bar app. A legacy background helper may appear separately.")
                .foregroundStyle(.secondary)
            PermissionRow(title: "This app: Accessibility", granted: model.snapshot.accessibilityGranted) {
                model.requestAccessibility()
            }
        }
    }
}

private struct LegacySection: View {
    let snapshot: HelperSystemSnapshot

    var body: some View {
        SetupSection(title: "Pairing", symbol: "iphone.and.arrow.forward") {
            if snapshot.legacyPairing == .globalSharedSecret {
                Label("Legacy pairing detected", systemImage: "exclamationmark.shield")
                    .font(.headline).foregroundStyle(.orange)
                Text("This installation uses one shared secret for every phone. Keep existing setups only while migration is in progress. Do not share the config file or use it to pair a new phone.")
                    .foregroundStyle(.secondary)
            } else {
                Text("New phone pairing is not available in this build. The helper will add a safer per-phone pairing flow before consumer release.")
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct TroubleshootingSection: View {
    @ObservedObject var model: HelperAppModel

    var body: some View {
        SetupSection(title: "Troubleshooting", symbol: "wrench.and.screwdriver") {
            Text("If setup remains incomplete, confirm the helper was installed with the repository’s install script, then check Accessibility above.")
                .foregroundStyle(.secondary)
            ViewThatFits(in: .horizontal) {
                HStack { folderButton; diagnosticsButton }
                VStack(alignment: .leading, spacing: 8) { folderButton; diagnosticsButton }
            }
            if let notice = model.notice {
                Text(notice).font(.caption).foregroundStyle(.secondary)
                    .accessibilityLabel("Status: \(notice)")
            }
            Text("Diagnostics never include the pairing secret or config contents.")
                .font(.caption).foregroundStyle(.secondary)
        }
    }

    private var folderButton: some View {
        Button("Open helper folder") { model.openInstallFolder() }
            .controlSize(.large)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
    }

    private var diagnosticsButton: some View {
        Button("Copy redacted diagnostics") { model.copyRedactedDiagnostics() }
            .controlSize(.large)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
    }
}

private struct SetupSection<Content: View>: View {
    let title: String
    let symbol: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label(title, systemImage: symbol).font(.title3.bold())
            content
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct PermissionRow: View {
    let title: String
    let granted: Bool
    var action: (() -> Void)? = nil

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) {
                permissionLabel
                Spacer()
                permissionState
                permissionButton
            }
            VStack(alignment: .leading, spacing: 8) {
                permissionLabel
                HStack(spacing: 12) {
                    permissionState
                    permissionButton
                }
            }
        }
        .frame(minHeight: 44)
    }

    private var permissionLabel: some View {
        Label(title, systemImage: granted ? "checkmark.circle.fill" : "circle")
            .foregroundStyle(granted ? Color.green : Color.primary)
    }

    private var permissionState: some View {
        Text(granted ? "Granted" : "Needed").foregroundStyle(.secondary)
    }

    @ViewBuilder private var permissionButton: some View {
        if !granted, let action {
            Button("Request access", action: action).controlSize(.large)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
                .accessibilityLabel("Request \(title) access")
        }
    }
}

private struct StatusLine: View {
    let label: String
    let value: String

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack { Text(label); Spacer(); Text(value).foregroundStyle(.secondary) }
            VStack(alignment: .leading, spacing: 4) {
                Text(label)
                Text(value).foregroundStyle(.secondary)
            }
        }
            .frame(minHeight: 44)
    }
}

private func serviceDetail(_ state: HelperServiceState) -> String {
    switch state {
    case .notInstalled: "Not installed"
    case .disabled: "Disabled"
    case .unloaded: "Configured, not loaded"
    case .stopped: "Loaded, stopped"
    case .running: "Running"
    case .unknown: "Checking…"
    }
}

private func loginDetail(_ state: HelperLaunchAtLoginState) -> String {
    switch state {
    case .notConfigured: "Not configured"
    case .configured: "Configured"
    case .malformed: "Needs repair"
    }
}

private func configurationDetail(_ state: HelperConfigurationState) -> String {
    switch state {
    case .missing: "Missing"
    case .valid: "Valid"
    case .invalid: "Needs repair"
    }
}

private func listenerDetail(_ state: HelperListenerState) -> String {
    switch state {
    case .unknown: "Unknown"
    case .verified: "Verified helper process"
    case .wrongOwner: "Port used by another process"
    case .unreachable: "Not reachable"
    }
}
