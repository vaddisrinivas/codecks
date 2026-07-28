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
    pairingStore: FilePairingStore()
)

print("codecks-mac-helper scaffold ready")
