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
    @Published private(set) var pairingDeepLink: String?
    @Published private(set) var pairingCode: String?
    @Published private(set) var pairingMacName: String?
    @Published private(set) var pairedDevices: [ReactivePairingRecord] = []

    private let reader = HelperSystemStatusReader()
    private var hostedHelper: HostedHelper?

    init() {
        do {
            hostedHelper = try HostedHelper()
        } catch {
            notice = "Service not started. Stop the older background helper, then reopen this app."
        }
    }

    var menuBarSymbol: String { HelperStatusModel(snapshot: snapshot).symbol }

    func refresh() {
        let reader = reader
        Task {
            let updated = await Task.detached(priority: .utility) { reader.read() }.value
            snapshot = updated
            lastChecked = Date()
            pairedDevices = (try? hostedHelper?.records.all())?.filter { !$0.isRevoked } ?? []
            if let offerId = hostedHelper?.activeOfferId,
               let pending = hostedHelper?.controller.pendingConfirmation(offerId: offerId, nowMillis: nowMillis()) {
                pairingCode = pending.matchingCode
            }
        }
    }

    func createPairingOffer() {
        do {
            guard let hostedHelper else { throw ReactiveValidationError("pairing_service_not_ready") }
            hostedHelper.cancelActiveOffer()
            let offer = try hostedHelper.createOffer()
            pairingDeepLink = try offer.deepLinkPayload()
            pairingMacName = offer.displayName
            pairingCode = nil
            notice = "Scan the QR code with Codecks. The offer expires in two minutes."
        } catch {
            notice = "Pairing not ready. A signed Codecks Mac Helper is required."
        }
    }

    func refreshPairingConfirmation() {
        guard let hostedHelper, let offerId = hostedHelper.activeOfferId else { return }
        let now = nowMillis()
        if hostedHelper.controller.isCompleted(offerId: offerId, nowMillis: now) || !hostedHelper.controller.isActive(offerId: offerId, nowMillis: now) {
            pairingDeepLink = nil; pairingCode = nil; pairingMacName = nil; hostedHelper.activeOfferId = nil
            return
        }
        pairingCode = hostedHelper.controller.pendingConfirmation(offerId: offerId, nowMillis: now)?.matchingCode
    }

    func confirmPairingCode() {
        do {
            guard let hostedHelper, let offerId = hostedHelper.activeOfferId, let pairingCode else {
                throw ReactiveValidationError("pairing_confirmation_not_ready")
            }
            try hostedHelper.controller.confirmOnMac(offerId: offerId, matchingCode: pairingCode, nowMillis: nowMillis())
            notice = "Mac confirmation saved. Confirm the same code on your phone."
        } catch {
            notice = "Pairing confirmation failed. Create a new offer."
        }
    }

    func cancelPairing() {
        pairingDeepLink = nil
        pairingCode = nil
        pairingMacName = nil
        hostedHelper?.cancelActiveOffer()
        notice = "Pairing cancelled."
    }

    func revoke(deviceId: String) {
        do {
            try hostedHelper?.controller.revoke(deviceId: deviceId, atMillis: nowMillis())
            pairedDevices = (try hostedHelper?.records.all())?.filter { !$0.isRevoked } ?? []
            notice = "Phone access revoked."
        } catch {
            notice = "Could not revoke phone access."
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

private final class HostedHelper {
    let controller: PairingV2Controller
    let server: ReactiveTcpHelperServer
    let config: ReactiveMacHelperRuntimeConfig
    let records: FilePairingStore
    var activeOfferId: String?

    init() throws {
        config = try ReactiveMacHelperRuntimeConfig.environment()
        let existingListener = HelperSystemStatusReader().read().listener
        guard existingListener != .verified && existingListener != .wrongOwner else {
            throw ReactiveValidationError("helper_listener_already_owned")
        }
        records = FilePairingStore()
        let credentials = KeychainPairingCredentialStore()
        let coordinator = config.makeCoordinator(pairingStore: records, credentialStore: credentials)
        controller = PairingV2Controller(
            credentials: credentials, records: records, helperIdentity: config.helperIdentity,
            invalidateSessions: coordinator.evictSessions(deviceId:)
        )
        server = ReactiveTcpHelperServer(
            port: config.port,
            handler: ReactiveFramedTransportService(coordinator: coordinator, secret: config.sharedSecret, pairingController: controller)
        )
        try server.start()
    }

    func createOffer() throws -> PairingV2Offer {
        let offer = try PairingV2OfferFactory.make(
            macId: config.macId,
            displayName: Host.current().localizedName ?? config.macId,
            helperIdentity: config.helperIdentity,
            host: Host.current().name ?? Host.current().localizedName,
            port: Int(config.port)
        )
        try controller.registerOffer(offer, nowMillis: offer.issuedAtMillis)
        activeOfferId = offer.offerId
        return offer
    }

    func cancelActiveOffer() {
        if let activeOfferId { controller.cancel(offerId: activeOfferId, nowMillis: nowMillis()) }
        activeOfferId = nil
    }
}
