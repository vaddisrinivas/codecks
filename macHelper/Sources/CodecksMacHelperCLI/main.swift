import CodecksMacHelper
import Foundation

let identity = HelperIdentityPin(
    helperId: "codecks-mac-helper",
    publicKeyFingerprint: "0000000000000000000000000000000000000000000000000000000000000000",
    issuedAtMillis: nowMillis(),
    trustState: .unverified
)

_ = ReactiveSessionCoordinator(
    macId: Host.current().localizedName ?? "mac",
    helperIdentity: identity,
    pairingStore: FilePairingStore(),
    actionHandlers: [
        "apple_shortcuts.run": AppleShortcutsActionHandler(),
        "spotlight.search": SpotlightSearchActionHandler(),
        "monitor_brightness.set": MonitorBrightnessActionHandler(),
        "accessibility.discover": AccessibilityDiscoveryActionHandler()
    ]
)

print("codecks-mac-helper scaffold ready")
