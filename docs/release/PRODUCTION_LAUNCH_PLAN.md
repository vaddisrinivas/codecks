# Codecks production launch plan

Updated: August 8, 2026

## Release decision

Current release is `v0.1.36`; commit `b6fc0ce` is the protected local-only
reference baseline. The planned Google Play commercial transition adds optional
account, configuration sync, verified purchases, typed rollout controls, and
dark ad infrastructure without making account/payment/network access necessary
for core local control.

All commercial surfaces ship production-disabled: account, cloud sync, Play
Billing, premium enforcement, and ads. Building and internally validating them
does not approve activation. Each needs a later explicit owner decision and
separate staged rollout.

The commercial plan supersedes the prior implementation schedule, not the
baseline behavior or its evidence. Deferred Reactive work remains separate:
native Mac helper completion, pinned helper pairing, full helper transport and
unified live Mac state, provider/receipt/undo completion, full iOS, DeskDock,
Shortcuts, Spotlight/SFTP, brightness, Accessibility discovery, and complete
cross-platform validation.

## Production-dark execution contract

- `CommercialExecutionPolicy.PRODUCTION_DARK` is a compile-time root deny.
- Commercial access is a monotonic conjunction. Remote state, preferences,
  entitlements, cached values, navigation, intents, and backend responses may
  subtract access but cannot override build/owner denial.
- Account, sync, Billing, premium enforcement, Explore network behavior, UMP,
  Mobile Ads, and commercial operational config expose no UI and perform no
  startup construction or network request.
- Public `playRelease` contains no internal override parser or verifier.

## Artifact matrix

| Artifact | Contract |
| --- | --- |
| `ossRelease` | `app.codecks`; current signer; local GitHub APK; commercial SDK dependencies/components/transports absent. Generic `androidx.credentials` remains for local SSH credential handling. |
| `playRelease` | `app.codecks`; compatible Play app-signing lineage; exact production AAB; commercial capability compiled but immutable production-dark. |
| `playInternal` | `app.codecks.internal`; separate internal signer/backend/data; only artifact permitted to exercise test overrides and commercial E2E. |

The Play upload certificate authenticates uploads; it is not the app-signing
certificate and cannot prove update compatibility. Both public releases remain
unshrunk.

The canonical implementation order, acceptance criteria, ad placement policy,
signing migration, and release gates are in
[`tasks/plan.md`](../../tasks/plan.md). The executable checklist is
[`tasks/todo.md`](../../tasks/todo.md).

## Completed for public beta

- [x] Product centered on Deck, Trackpad, Keyboard, Clipboard, Automations, Settings, editing, and optional AI drafting.
- [x] Restricted lock-screen Trackpad access is opt-in, requires an existing Bluetooth HID connection, and exposes pointer controls only.
- [x] Shipped, experimental, infrastructure, and deferred capabilities are separated in the feature guide.
- [x] Consistent Codecks dark-green visual system across core screens.
- [x] Local-only defaults; no server initialization, account, billing, analytics, ads, or cloud sync.
- [x] Public application ID and semantic version established.
- [x] Personal names, workstation paths, device serials, screenshots, and old recovery history excluded from public source.
- [x] Android backup and cleartext traffic disabled.
- [x] Optional exported components off by default; system-facing components permission protected.
- [x] Immutable `PendingIntent` use and signed internal destination routing verified.
- [x] API keys and SSH private-key material protected by Android Keystore-backed encryption.
- [x] Public privacy, security, contribution, and release-signing documentation added.
- [x] CI runs privacy scan, unit tests, lint, and debug build on every change.
- [x] Tag/manual workflow rebuilds and publishes signed APK/checksum from public source.
- [x] `v0.1.25` release workflow publishes a single signed APK with checksum.

## GA gates

| Ticket | Gate | Acceptance |
| --- | --- | --- |
| GA-01 | Physical device matrix | Core flows pass on Samsung phone, non-Samsung phone, tablet, and one DeX setup across Android 12–16. |
| GA-02 | macOS matrix | SSH and HID flows pass on the two latest macOS releases, Intel and Apple Silicon where available. |
| GA-03 | Accessibility | TalkBack order/labels, 200% font scale, switch access, contrast, and touch targets pass. |
| GA-04 | Reliability | At least 20 testers, seven days, no P0/P1 issue, and crash-free sessions at or above 99.5%. |
| GA-05 | Automation safety | Adversarial command suite passes; every enable path requires current successful test evidence. |
| GA-06 | Pairing UX | First-time Mac SSH and Bluetooth HID setup succeeds for at least 80% of moderated testers without developer help. |
| GA-07 | DeX QA | Resize, rotate, keyboard, mouse, focus, and window restore pass at 1280×720 and 1920×1080. |
| GA-08 | Release operations | Key backup verified, rollback procedure rehearsed, security-advisory intake tested, release checksum verified on a clean machine. |
| GA-09 | Store decision | Either remain GitHub-only with documented sideload support, or complete Play listing, Data Safety, screenshots, policy review, and staged rollout. |
| GA-10 | AI draft reliability | Versioned strict schemas pass every provider contract test; at least 100 representative prompts achieve 99% parse success, 95% safe semantic-validity, and zero generated actions bypass review or deterministic policy checks. |

## Commercial GA priorities

1. Prove the existing production signer can be imported into Play App Signing.
2. Freeze immutable production-dark policy, monotonic gate algebra, and the
   internal-test boundary.
3. Split `ossRelease`, `playRelease`, and `playInternal` at Gradle/source-set
   level and audit every commercial initializer.
4. Keep local flags separate; add typed commercial capability, owner policy,
   emergency deny, rollout, entitlement, consent, and preference contracts.
5. Add optional sign-in and explicit DTO allowlist-only backup/restore. Snapshot
   v1 excludes raw commands, shell steps, credentials, hosts, clipboard, and
   execution proof.
6. Make account deletion operational before any account-creating test track.
7. Implement backend-authoritative entitlement/RTDN state machines before the
   Billing client; request Integrity only for a bound sensitive transaction.
8. Isolate UMP and Mobile Ads with no launch initialization.
9. Complete privacy, deletion, Data Safety, accessibility, reliability, and
   staged testing gates.
10. Test commercial E2E in `playInternal`; prove the exact `playRelease` artifact
   inert under adversarial state and cold-start network capture.
11. Launch production with account, sync, Billing, premium enforcement, and ads
   all dark. Evaluate each independently only after explicit approval.

## Exact-artifact admission

The admitted record contains Git SHA, version, AAB SHA-256, app-signing lineage,
Gradle inputs, dependency/merged-manifest scans, initializer audit, cold-start
network capture, migration evidence, and evidence-bundle digest. Production
promotion reuses that exact AAB. Any rebuild resets admission.

Mandatory adversarial gates cover corrupt/stale flags and caches, clock skew,
crafted intents/deep links/restored navigation, internal-override replay,
snapshot fuzz/secret canaries/raw-command rejection, auth replay/revocation,
the complete Billing/RTDN lifecycle, and zero ad requests on every forbidden
surface. Passing local tests never substitutes for Play-track, physical-phone,
real-Mac, or human acceptance evidence.
