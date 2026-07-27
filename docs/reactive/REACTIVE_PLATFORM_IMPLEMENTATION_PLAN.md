> Repository-local copy added on July 27, 2026.
>
> Historical scope note:
>
> - this blueprint was originally prepared against `v0.1.19`;
> - current implementation work must also read
>   `docs/product/DESK_VALUE_LOCKSCREEN_AND_REACTIVE_EXPANSION_PLAN.md`;
> - when the two documents conflict on current repository truth, release policy,
>   or sequencing, the newer addendum controls.

# Codecks Reactive Platform — End-to-End Implementation Plan

Status: implementation blueprint; the bounded v0.1.23 shared/protocol/client
scaffolds are implemented, while the remaining platform phases are deferred.

Prepared from:

- the complete Reactive Trackpad / KMP / Mac-helper specification;
- the live `v0.1.19` repository at `2d73fe4fc1b3504f475d7e5a774010e4d3fd189d`;
- the active Smart/release hardening task;
- current Codecks product, safety, privacy, release, Trackpad, SSH, HID, and Smart code;
- current official Kotlin, Android, and Apple platform documentation.

This document is intentionally explicit. An implementation agent must execute one phase at a time, copy the named patterns, run the named discovery and verification gates, and stop instead of inventing missing APIs.

---

## 1. Required outcome

Build one local-first Codecks platform with:

1. the existing Android controller;
2. a native macOS helper;
3. a shared Kotlin Multiplatform core for Android and iOS;
4. a later iOS controller;
5. a typed Reactive Trackpad with four strict layers:
   - Mac state;
   - pure control providers;
   - execution and undo;
   - Trackpad UI.

Final data flow:

```text
Mac helper events ───────────────┐
                                ├─> Unified MacStateRepository
Bundled SSH probes (fallback) ──┘            │
                                             ▼
                                  typed MacStateSnapshot
                                             │
                                             ▼
                                 pure Reactive providers
                                             │
                                             ▼
                           capability + safety + preference policy
                                             │
                                             ▼
                              deterministic ranking (max 6)
                                             │
                                             ▼
                          Trackpad HUD + 4 visible controls + More
                                             │
                                             ▼
                              explicit user execution request
                                             │
                  ┌──────────────┼───────────────┐
                  ▼              ▼               ▼
             Android HID    Mac helper API   bundled SSH fallback
                  └──────────────┼───────────────┘
                                 ▼
                         typed result + exact receipt
                                 │
                                 ▼
                         optional time-bound undo
```

The Bluetooth HID pointer path remains independent:

```text
MotionEvent
  -> RawTrackpadView
  -> TrackpadGestureEngine
  -> MouseViewModel
  -> HidRepository
  -> HidController queue
  -> Bluetooth HID report
```

Nothing in Mac state, helper networking, SSH, providers, Smart, receipts, or UI ranking may block that path.

---

## 2. Product boundaries

Keep the existing product nouns and responsibilities:

| Surface | Responsibility |
|---|---|
| Deck | Deliberate persistent buttons |
| Trackpad | Pointer plus temporary reactive controls |
| Keyboard | Writing, snippets, and text transforms |
| Clipboard | Phone/Mac clipboard exchange and optional history |
| Rules | Saved, tested automations |
| AI Builder | Drafts buttons, Rules, and profiles |
| Settings | Macs, permissions, profiles, and customization |

Rules:

- Reactive controls are temporary. They do not silently become Deck buttons.
- Four controls are visible. Up to six may be ranked; extras go in **More**.
- Undo occupies the first visible slot while valid.
- Dangerous controls normally remain in **More**.
- Unsupported controls are hidden, not disabled dead buttons.
- Smart is optional, default-off, deterministic, and proposal-only.
- AI Builder may draft a profile but must save it disabled or unassigned until review.
- Use the user-facing terms and statuses in `DESIGN.md`.

---

## 3. Non-goals for the first production slice

Do not include these in the Reactive MVP:

- OCR;
- continuous text-selection polling;
- semantic UI-element cursor snapping;
- meeting controls;
- calendar controls;
- AI text transforms;
- automatic profile creation;
- automatic gesture reassignment;
- LLM ranking;
- cloud accounts, cloud relay, analytics, or hosted databases;
- removal of SSH;
- removal of Android Bluetooth HID;
- a Kotlin/Native macOS product binary;
- Intel Mac abandonment without a separate product decision;
- one identical binary or one identical UI for all platforms.

---

## 4. Phase -1: mandatory hardening checkpoint

### 4.1 Current repository truth

Snapshot at plan creation:

- branch: `main`;
- HEAD: `2d73fe4`, tag `v0.1.19`;
- upstream: `origin/main`;
- worktree: heavily dirty;
- tracked delta: approximately `+812/-652`;
- Smart phases 1–6 are described as complete but remain uncommitted;
- Smart phases 7–8 remain in progress;
- physical phone/Mac release evidence is not complete;
- `MainActivity.kt`, `HomeScreen.kt`, `app/build.gradle.kts`, release workflow, Smart files, and Trackpad instrumentation source sets are actively changing.

Do not create Reactive code in this checkout.

### 4.2 Hardening defect that must be resolved

The Smart ledger claims an opaque Mac ID. Live code still creates:

```kotlin
private fun targetId(host: String, user: String, port: Int = 22): String =
    "mac_${user}_${host}_${port}"
```

Source:

- `app/src/main/java/io/codecks/data/ConnectionRepository.kt:702-747`;
- persisted at `ConnectionRepository.kt:726-738`;
- exposed as `DeviceId` by `LocalDeviceRepository.kt:21-56`;
- passed into Smart as `SmartMacId`.

This is not opaque. It embeds endpoint data.

Hardening must:

1. generate a cryptographically random persistent target ID for every new Mac;
2. migrate existing endpoint-derived IDs without losing the selected Mac, host trust, Deck target references, Rules, run history, or Smart suppression;
3. keep endpoint fields separate from identity;
4. update every foreign-key/reference consumer;
5. add migration, round-trip, collision, selection-preservation, and privacy tests;
6. prove encoded Smart data contains no username, host, IP, port, or host key;
7. update the hardening ledger honestly.

### 4.3 Exit gate

The platform baseline exists only when all are true:

- active hardening task has stopped;
- changes are checkpointed in focused commits;
- worktree is clean;
- random target-ID migration is committed and tested;
- reviewed free-form command and bundled command paths remain separate;
- dangerous confirmation remains per revision and per run;
- moved Trackpad instrumentation tests are included in an actual runnable source set;
- progress ledger matches Git truth;
- unit, lint, architecture, release-surface, secret, AI, and Mac action checks pass;
- physical checks are labeled unverified unless actually run;
- exact baseline commit is recorded as `PLATFORM_BASELINE_COMMIT`.

Required read-only audit:

```bash
git status --short
git branch --show-current
git rev-parse HEAD
git log -10 --oneline --decorate
git diff --check
./gradlew :app:tasks --all
```

Then run the tasks that actually exist. Do not assume `testDebugUnitTest`; the current checkout sets `testBuildType = "release"`.

Release-signing commands must use the private agent environment wrapper. Never print signing values:

```bash
run-with-agent-env.sh \
  ./gradlew --no-daemon :app:testReleaseUnitTest :app:lintDebug :app:check
```

No Reactive agent may “clean,” reset, stash, commit, or absorb the hardening owner’s changes.

---

## 5. Locked architecture decisions

Create ADRs for these decisions in Phase 0. An agent may not silently reverse them.

### ADR-001: incremental migration

- Keep `:app` as the Android application.
- Add one KMP library module, `:shared`.
- Move only portable protocol/reactive logic into `:shared`.
- Do not convert the Android application module into KMP.
- Do not rewrite existing Android UI before the shared core is proven.

### ADR-002: native Mac helper

- Build the macOS helper in Swift with AppKit/SwiftUI and Apple frameworks.
- Produce a universal Apple Silicon + Intel app while Intel remains supported.
- Do not use `macosX64`; Kotlin 2.3.20+ deprecates it.
- Do not make the production helper depend on a Kotlin/Native runtime.
- Keep protocol parity through canonical schemas and cross-language fixtures.

### ADR-003: native mobile shells

- Android keeps Jetpack Compose.
- iOS starts as a thin SwiftUI shell consuming the KMP core.
- Sharing mobile UI with Compose Multiplatform is a later measured decision, not a prerequisite.
- Share models, protocol, state machine, provider engine, policy, profiles, serialization, and tests first.

### ADR-004: hybrid transport

Android:

- HID: pointer, keyboard shortcuts, media;
- helper: events, native Mac state/actions, receipts;
- SSH: existing Deck/Rules and bounded fallback probes/actions.

iOS:

- helper protocol: pointer, keyboard, state, and actions.

Mac:

- helper exposes typed operations;
- helper never exposes a raw remote shell endpoint.

### ADR-005: schema-first protocol

- Canonical wire schema and fixtures live under `protocol/`.
- KMP and Swift each implement the schema.
- Both languages must decode the same golden fixtures and reject the same invalid fixtures.
- Unknown optional fields are ignored.
- Unknown required message types fail safely.

### ADR-006: one identity model

- Mac identity is a random persistent ID.
- Bonjour name, hostname, IP, SSH endpoint, and display name are mutable attributes, never identity.
- Pairing identity is a persistent P-256 public key plus random installation ID.
- Android keys live in Android Keystore.
- Apple keys/secrets live in Keychain.

### ADR-007: one capability taxonomy

- Define canonical `CodecksCapability` in shared code.
- Existing `Capability`, `SmartCapability`, and transport capabilities receive explicit adapters.
- Do not create unrelated capability enums with similar names and no mapping.

### ADR-008: distinct action surfaces, shared references

- Keep `codecks_actions.json` as the permanent Deck/Rules catalog.
- Add `reactive_actions.json` for reactive metadata and executor references.
- Reactive entries refer to existing catalog IDs, typed HID operations, or typed helper action IDs.
- Reactive JSON must not duplicate arbitrary shell command bodies.
- Mac probe assets use a separate signed/hashed manifest.

### ADR-009: helper-primary state, SSH fallback

- Helper events are primary when authenticated and capable.
- SSH probes are bounded fallback for Android.
- State repository exposes one merged snapshot.
- Each field retains source, freshness, and availability.
- UI never decides which backend to query.

### ADR-010: state is not authorization

- A visible control is never authorization.
- Executor rechecks current capability, expiry, action revision, safety, and confirmation.
- Smart ranking never adds authorization.
- Helper repeats server-side authorization checks.

---

## 6. Allowed platform APIs

Agents must read the linked official documentation before implementation.

### Kotlin / KMP

Allowed:

- `org.jetbrains.kotlin.multiplatform`;
- `com.android.kotlin.multiplatform.library`;
- explicit `iosArm64()` and `iosSimulatorArm64()` targets;
- the KMP default source-set hierarchy;
- `kotlinx.serialization-json`;
- Kotlin Coroutines `Flow` / `StateFlow`.

Current compatibility:

- repository Kotlin plugin: `2.3.21`;
- repository AGP: `9.2.0`;
- official compatibility table permits this combination;
- use the new Android-KMP library plugin and its `kotlin { android { ... } }` block.

Do not:

- use removed `ios()` target shortcuts;
- use legacy target presets;
- add `macosX64`;
- migrate `:app` to `com.android.kotlin.multiplatform.library`;
- guess test-task names.

References:

- https://developer.android.com/kotlin/multiplatform/plugin
- https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
- https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html
- https://kotlinlang.org/docs/native-target-support.html
- https://kotlinlang.org/docs/serialization.html

### macOS networking and lifecycle

Allowed:

- `NWListener`;
- `NWConnection`;
- `NWBrowser` where the Mac app needs browsing;
- Network framework TLS;
- Bonjour service `_codecks._tcp`;
- `SMAppService.mainApp` for opt-in launch at login;
- Keychain Services.

Do not:

- use unauthenticated TCP;
- hardcode a port;
- put secrets in Bonjour TXT records;
- use hostname/IP as identity;
- register a privileged daemon for MVP;
- use undocumented private frameworks.

References:

- https://developer.apple.com/documentation/network/nwlistener
- https://developer.apple.com/documentation/network/tls
- https://developer.apple.com/documentation/technotes/tn3151-choosing-the-right-networking-api
- https://developer.apple.com/documentation/servicemanagement/smappservice/register%28%29
- https://developer.apple.com/documentation/security/keychain-services

### macOS state and control

Allowed:

- `NSWorkspace.shared.notificationCenter`;
- `NSWorkspace.didActivateApplicationNotification`;
- `NSRunningApplication`;
- Accessibility `AXUIElement` APIs after explicit permission;
- `AXIsProcessTrustedWithOptions`;
- `NSScreen`;
- `CGEvent` / `CGEventGetLocation`;
- `CGWarpMouseCursorPosition`;
- `NSPasteboard`;
- ScreenCaptureKit in the later screenshot phase.

Do not:

- continuously scrape the screen for basic state;
- query selected text without explicit user intent;
- assume every app implements Accessibility attributes;
- treat permission denial as an error loop;
- use AppleScript keystrokes for passwords or secrets;
- ship screenshot capture before permission UX and deletion boundaries exist.

References:

- https://developer.apple.com/documentation/appkit/nsworkspace/didactivateapplicationnotification
- https://developer.apple.com/documentation/applicationservices/1462060-axuielementcopyattributevalues
- https://developer.apple.com/documentation/coregraphics/cgwarpmousecursorposition%28_%3A%29
- https://developer.apple.com/documentation/appkit/nspasteboard
- https://developer.apple.com/documentation/screencapturekit/capturing-screen-content-in-macos

### Android discovery and key storage

Allowed:

- `NsdManager` DNS-SD discovery;
- Android Keystore P-256 signing keys;
- existing `HidRepository` and `HidController`.

Do not:

- use Bluetooth service names as trusted identity;
- store private pairing keys in DataStore;
- schedule periodic discovery polling;
- assume Android Bluetooth HID can be ported to iOS.

References:

- https://developer.android.com/develop/connectivity/wifi/use-nsd
- https://developer.android.com/reference/android/net/nsd/NsdManager
- https://developer.android.com/privacy-and-security/keystore

### iOS local networking

Allowed:

- Network framework browsing and connections;
- `NSLocalNetworkUsageDescription`;
- `NSBonjourServices` containing `_codecks._tcp`;
- Keychain;
- real-device validation.

Do not:

- treat simulator discovery as physical proof;
- browse every Bonjour service type;
- request multicast entitlement for ordinary declared Bonjour browsing;
- assume unlimited background socket execution.

Reference:

- https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy

---

## 7. Target repository structure

Create only after Phase -1 exits:

```text
codecks/
├── app/                              existing Android application
│   └── src/
│       ├── main/java/io/codecks/
│       │   ├── data/macstate/
│       │   ├── data/reactive/
│       │   ├── ui/mouse/reactive/
│       │   └── platform/helper/
│       ├── main/assets/
│       │   ├── mac_state/
│       │   ├── mac_state_manifest.json
│       │   └── reactive_actions.json
│       ├── test/
│       └── debugAndroidTest/
│
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/io/codecks/shared/
│       │   ├── identity/
│       │   ├── capability/
│       │   ├── protocol/
│       │   ├── macstate/
│       │   ├── reactive/
│       │   ├── safety/
│       │   ├── receipts/
│       │   └── profiles/
│       ├── commonTest/
│       ├── androidMain/
│       ├── androidHostTest/
│       ├── iosMain/
│       └── iosTest/
│
├── protocol/
│   ├── README.md
│   ├── schema/
│   │   ├── envelope.schema.json
│   │   ├── messages.schema.json
│   │   └── reactive-actions.schema.json
│   ├── fixtures/valid/
│   ├── fixtures/invalid/
│   └── compatibility/
│
├── macHelper/
│   ├── CodecksHelper.xcodeproj
│   ├── CodecksHelper/
│   │   ├── App/
│   │   ├── Networking/
│   │   ├── Pairing/
│   │   ├── State/
│   │   ├── Actions/
│   │   ├── Permissions/
│   │   ├── Persistence/
│   │   └── UI/
│   └── CodecksHelperTests/
│
├── iosApp/                           created in the iOS phase
│   ├── Codecks.xcodeproj
│   ├── Codecks/
│   └── CodecksTests/
│
├── scripts/
│   ├── verify_mac_actions.py
│   ├── verify_reactive_contracts.py
│   └── verify_protocol_fixtures.py
│
└── docs/reactive/
    ├── REACTIVE_PLATFORM_IMPLEMENTATION_PLAN.md
    ├── REACTIVE_IMPLEMENTATION_PROGRESS.md
    ├── REACTIVE_DECISIONS.md
    ├── REACTIVE_THREAT_MODEL.md
    ├── REACTIVE_PRIVACY_MODEL.md
    └── REACTIVE_PHYSICAL_TEST_MATRIX.md
```

`docs/reactive/REACTIVE_PLATFORM_IMPLEMENTATION_PLAN.md` is copied from this plan only after the hardening checkpoint.

---

## 8. Canonical shared models

### 8.1 Identity

```kotlin
@JvmInline
value class MacId(val value: String)

@JvmInline
value class ControlId(val value: String)

@JvmInline
value class ActionRevision(val value: String)

@JvmInline
value class ReceiptId(val value: String)

@JvmInline
value class ProtocolMessageId(val value: String)
```

Validation:

- nonblank;
- maximum 128 UTF-8 bytes;
- IDs generated by Codecks use lowercase UUID strings;
- external IDs are never used in a file path or shell command without a typed validator;
- display names are not IDs.

### 8.2 Capability

```kotlin
enum class CodecksCapability {
    PointerInput,
    KeyboardInput,
    MediaInput,
    MacCommand,
    FrontAppRead,
    ActiveWindowRead,
    WindowWrite,
    DisplayRead,
    CursorRead,
    CursorWrite,
    ClipboardRead,
    ClipboardWrite,
    SelectionRead,
    ScreenshotCapture,
    ScreenshotManage,
    MeetingControl,
}

enum class CapabilityAvailability {
    Available,
    PermissionNeeded,
    Unsupported,
    Offline,
    Unknown,
}

data class CapabilityState(
    val capability: CodecksCapability,
    val availability: CapabilityAvailability,
    val reasonCode: String? = null,
)
```

Adapters:

- `CodecksCapability.MacCommand` ↔ existing `Capability("ssh")`;
- `PointerInput`, `KeyboardInput`, `MediaInput` ↔ HID/helper transport;
- explicit Smart adapter, never an implicit enum-name conversion.

### 8.3 Observed state

Use per-field freshness. Do not use one timestamp to imply every field is fresh.

```kotlin
enum class StateSource {
    Helper,
    SshProbe,
    LocalCache,
}

enum class ObservationStatus {
    Fresh,
    Stale,
    PermissionNeeded,
    Unsupported,
    Unavailable,
}

data class Observed<T>(
    val value: T?,
    val status: ObservationStatus,
    val observedAtMillis: Long?,
    val source: StateSource?,
    val warningCode: String? = null,
)

data class MacStateSnapshot(
    val macId: MacId,
    val snapshotRevision: Long,
    val capturedAtMillis: Long,
    val frontApp: Observed<MacApplication>,
    val activeWindow: Observed<MacWindow>,
    val displays: Observed<List<MacDisplay>>,
    val cursor: Observed<MacCursorState>,
    val selection: Observed<MacSelection>,
    val clipboard: Observed<MacClipboardMetadata>,
    val media: Observed<MacMediaState>,
    val system: Observed<MacSystemState>,
    val meeting: Observed<MacMeetingState>,
    val latestScreenshot: Observed<MacScreenshotState>,
    val capabilities: Set<CapabilityState>,
) {
    fun isBasicStateExpired(nowMillis: Long, maxAgeMillis: Long = 3_000): Boolean =
        nowMillis - capturedAtMillis > maxAgeMillis
}
```

Privacy:

- `MacWindow.title` is memory-only;
- clipboard model contains kind, byte-size bucket, safe preview only when user enabled it, and changed time;
- selection text is memory-only and explicit;
- screenshot paths are memory-only receipts;
- no raw state fields enter ordinary logs.

### 8.4 App and selection

```kotlin
enum class MacAppKind {
    Terminal,
    Browser,
    Finder,
    CodeEditor,
    Meeting,
    Calendar,
    Media,
    Mail,
    Messages,
    Generic,
}

sealed interface MacSelection {
    data object None : MacSelection
    data class Text(val preview: String?, val private: Boolean) : MacSelection
    data class File(val path: String?, val name: String?, val isDirectory: Boolean) : MacSelection
    data class Url(val url: String) : MacSelection
    data class Image(val path: String?) : MacSelection
    data class Unknown(val role: String?) : MacSelection
}
```

Classify raw app identity once, outside UI/providers. Prefer bundle ID; app-name tokens are fallback.

### 8.5 Reactive control

```kotlin
data class ReactiveControl(
    val id: ControlId,
    val title: String,
    val subtitle: String?,
    val icon: ReactiveIcon,
    val action: ReactiveAction,
    val source: ReactiveControlSource,
    val basePriority: Int,
    val reason: String,
    val requiredCapabilities: Set<CodecksCapability>,
    val risk: ReactiveRisk,
    val reversible: Boolean,
    val stateRevision: Long,
    val actionRevision: ActionRevision,
    val expiresAtMillis: Long,
)

enum class ReactiveRisk {
    Safe,
    Review,
    Dangerous,
    Private,
}
```

Never put a command string in a UI model.

### 8.6 Typed action

```kotlin
sealed interface ReactiveAction {
    data class ExistingCatalog(val actionId: String) : ReactiveAction
    data class Hid(val command: SharedHidCommand) : ReactiveAction
    data class Helper(
        val actionId: String,
        val arguments: Map<String, String> = emptyMap(),
    ) : ReactiveAction
    data class BundledSshFallback(
        val bundleId: String,
        val arguments: Map<String, String> = emptyMap(),
    ) : ReactiveAction
    data class Composite(val actions: List<ReactiveAction>) : ReactiveAction
    data class ChangeMode(val mode: ReactiveTrackpadMode) : ReactiveAction
}
```

`SharedHidCommand` maps exhaustively to existing Android `HidCommand` and helper input commands. Never use `Hid(command: String)`.

### 8.7 Provider

```kotlin
interface ReactiveControlProvider {
    val id: String

    fun controls(
        state: MacStateSnapshot,
        context: ReactiveTrackpadContext,
        nowMillis: Long,
    ): List<ReactiveControl>
}
```

Provider rules:

- pure and deterministic;
- no I/O;
- no repository calls;
- no clock reads; use `nowMillis`;
- no randomness;
- no LLM;
- no persistence;
- no UI types;
- no shell/helper command construction.

### 8.8 Execution result and receipt

```kotlin
sealed interface ReactiveActionResult {
    data class Succeeded(val messageCode: String) : ReactiveActionResult
    data class Failed(val errorCode: String, val retryable: Boolean) : ReactiveActionResult
    data class RequiresConfirmation(
        val actionRevision: ActionRevision,
        val title: String,
        val body: String,
    ) : ReactiveActionResult
    data class RequiresReview(
        val actionRevision: ActionRevision,
        val reason: String,
    ) : ReactiveActionResult
    data class Unsupported(val reasonCode: String) : ReactiveActionResult
    data object Expired : ReactiveActionResult
}

data class ReactiveAuthorization(
    val confirmedActionRevision: ActionRevision? = null,
    val reviewedActionRevision: ActionRevision? = null,
)

data class ReactiveActionReceipt(
    val id: ReceiptId,
    val controlId: ControlId,
    val actionRevision: ActionRevision,
    val completedAtMillis: Long,
    val result: ReactiveActionResult.Succeeded,
    val undo: ReactiveUndoAction?,
    val expiresAtMillis: Long?,
    val metadata: Map<String, String> = emptyMap(),
)
```

No receipt for failure, review, confirmation, unsupported, timeout, cancellation, or unknown completion.

---

## 9. Wire protocol

### 9.1 Framing

- TLS 1.3 over TCP.
- Four-byte unsigned big-endian length.
- UTF-8 JSON payload.
- Control frame maximum: 256 KiB.
- Reject zero length.
- Reject oversized length before allocation.
- Read with deadline.
- No unbounded buffering.
- No screenshot bytes in control frames during MVP.

### 9.2 Envelope

```json
{
  "schemaVersion": 1,
  "messageType": "hello",
  "messageId": "uuid",
  "correlationId": null,
  "sessionId": null,
  "sequence": 0,
  "sentAtMillis": 0,
  "deadlineAtMillis": 0,
  "payload": {}
}
```

Rules:

- validate envelope before payload;
- reject unknown `schemaVersion`;
- ignore unknown optional payload fields;
- reject unknown required `messageType`;
- `messageId` is unique per sender;
- operational messages require authenticated `sessionId`;
- sequence must increase strictly;
- expired deadlines fail without execution;
- cache completed request IDs for replay-safe idempotency;
- never execute the same request twice after reconnect.

### 9.3 Required messages

Handshake:

- `Hello`;
- `HelloAck`;
- `AuthenticateChallenge`;
- `AuthenticateResponse`;
- `AuthenticateResult`.

Pairing:

- `PairingStart`;
- `PairingTranscript`;
- `PairingUserConfirmed`;
- `PairingComplete`;
- `PairingRejected`.

State:

- `SubscribeState`;
- `UnsubscribeState`;
- `StateSnapshot`;
- `StateDelta`;
- `CapabilityChanged`;
- `PermissionChanged`.

Execution:

- `ExecuteControl`;
- `ExecutionProgress`;
- `ExecutionResult`;
- `UndoReceipt`;
- `ExecuteUndo`;
- `CancelExecution`.

Session:

- `Ping`;
- `Pong`;
- `ResyncRequired`;
- `Error`;
- `Goodbye`.

### 9.4 Negotiation

`Hello` carries:

- minimum and maximum protocol versions;
- client installation ID;
- platform;
- app version;
- supported compression: `none` for MVP;
- supported message features.

`HelloAck` carries:

- selected protocol version;
- helper installation ID;
- helper display name;
- helper app version;
- capabilities;
- permission states;
- current state revision.

If no version overlaps:

- close session;
- show **Update Codecks on this phone or Mac**;
- do not fall back to an unversioned protocol.

### 9.5 Pairing and authentication

Use P-256 keys because Android Keystore and Apple Security/CryptoKit support them.

First pairing:

1. User selects **Pair new phone** on Mac helper.
2. Helper opens a two-minute pairing window.
3. Android/iOS discovers `_codecks._tcp`.
4. Client connects using TLS in explicit pairing mode.
5. Client records helper certificate SPKI fingerprint.
6. Client and helper exchange:
   - installation IDs;
   - P-256 public signing keys;
   - 256-bit random nonces;
   - protocol version range.
7. Both compute a canonical SHA-256 transcript.
8. Both display the same six-digit short authentication string derived from the transcript.
9. User confirms the match on both devices.
10. Helper stores client public key, display name, permissions, and pairing time in Keychain-backed storage.
11. Client pins helper SPKI fingerprint and stores helper public identity.
12. Pairing window closes.

Future authentication:

1. TLS client pins helper SPKI.
2. Helper sends a fresh 256-bit challenge and session ID.
3. Client signs:
   `protocolVersion || sessionId || challenge || helperFingerprint`.
4. Helper verifies the stored client public key.
5. Helper returns authenticated permissions and capabilities.
6. Both reset sequence counters for the new session.

Security rules:

- no permanent shared password;
- no bearer token in DataStore/UserDefaults;
- no pairing material in Bonjour;
- five failed attempts closes pairing;
- one active pairing attempt at a time;
- revoke per phone;
- rotate helper identity only with explicit warning that phones must re-pair;
- log only stable error codes, never keys, transcript, IP, hostname, selected text, or clipboard.

Phase 2 must include a security review of certificate creation, transcript canonicalization, and signature verification before production code uses them.

---

## 10. Mac-state repository contract

Shared domain contract:

```kotlin
interface MacStateRepository {
    val state: StateFlow<MacStateSnapshot?>
    val connection: StateFlow<MacStateConnectionState>

    suspend fun refreshBasic(): MacStateRefreshResult
    suspend fun refreshDisplays(): MacStateRefreshResult
    suspend fun refreshClipboardMetadata(): MacStateRefreshResult
    suspend fun refreshMedia(): MacStateRefreshResult
    suspend fun inspectSelection(): MacStateRefreshResult

    fun start(visibility: TrackpadVisibility)
    fun stop()
}
```

Backend policy:

1. authenticated helper with capability;
2. bundled SSH probe;
3. previous cache marked stale;
4. unavailable.

Refresh policy for SSH fallback:

| State | Refresh |
|---|---:|
| Front app/window | 750–1,500 ms while Trackpad visible |
| Displays | connect + every 30 s |
| Clipboard metadata | every 2 s only when enabled |
| Media | every 2 s only in relevant apps/mode |
| Selection | explicit action only |
| Cursor | cursor mode only |
| Screenshot | after Codecks screenshot action |
| Volume/mute | after action + every 3 s |

Merge rules:

- merge only the fields returned by a successful probe/event;
- retain previous valid value when a field fails;
- mark retained value stale;
- reject lower `snapshotRevision` from the same helper session;
- force full resync after sequence gap;
- never merge state from two different `MacId`s;
- clear private selection on screen exit, Mac switch, timeout, or app background;
- maximum probe output: 64 KiB;
- unknown schema: reject entire probe;
- truncated output: reject entire probe;
- parser failure: keep prior state stale and emit safe error code.

### Required SSH gateway

Current `ConnectionRepository` lacks bundled stdin + raw stdout.

Add a narrow API after hardening:

```kotlin
suspend fun runBundledCommandRawWithInputOnTarget(
    targetId: String,
    command: String,
    stdin: String,
): Result<String>
```

Requirements:

- select exact target;
- require target ready and pinned host key;
- command must be a fixed bundled transport such as `osascript -l JavaScript`;
- apply `RawCommandPolicy.requireAllowed`;
- bound stdin size;
- use existing timeout;
- bound stdout/stderr;
- reject truncation;
- return raw stdout, not 240-character summary;
- never weaken `runCommandWithInput`;
- fakes and exhaustive implementers updated;
- transport tests prove raw arbitrary callers cannot reach it.

Probe assets:

```text
app/src/main/assets/mac_state/
  basic_state.jxa
  display_state.jxa
  finder_selection.jxa
  browser_state.jxa
  media_state.jxa
  cursor_state.jxa
```

Each:

- read-only;
- `schemaVersion`;
- structured JSON on success/failure;
- no selected-text copy side effect;
- no persistence;
- no secret access;
- listed with SHA-256 in `mac_state_manifest.json`;
- compiled/validated by Mac verifier.

---

## 11. Reactive engine

Processing order:

1. reject wrong Mac;
2. call all providers;
3. validate provider output;
4. remove expired controls;
5. remove missing capabilities;
6. remove hidden controls;
7. apply profile;
8. merge duplicate IDs;
9. apply safety policy;
10. calculate deterministic score;
11. sort by score descending, then control ID ascending;
12. return maximum six;
13. UI shows first four, with Undo forced to first when valid.

Score:

```text
base provider priority
+ current mode                   40
+ exact front-app provider       30
+ exact selection match          30
+ app/Mac profile pin            25
+ recent successful use          10
+ clipboard changed              12
+ valid undo                    100
- dangerous                      25
- state older than 3 s           20
- recent failure                 configurable bounded penalty
```

Missing capability removes a control. It is not merely a score penalty.

Duplicate merge:

- same ID must have identical action revision, risk, and capability requirements;
- mismatch is a provider contract error and removes the control;
- keep highest base priority;
- combine unique reason codes;
- never merge two different actions under one ID.

MVP providers:

1. Browser;
2. Terminal;
3. Finder;
4. Media;
5. Generic Window;
6. Undo.

Do not add app-name branches to `MouseScreen.kt`.

---

## 12. Action registry, execution, and undo

### Reactive registry

Example shape:

```json
{
  "schemaVersion": 1,
  "actions": [
    {
      "id": "browser.back",
      "executor": "hid",
      "operation": "BrowserBack",
      "risk": "Safe",
      "requiredCapabilities": ["KeyboardInput"],
      "undoFactory": null
    },
    {
      "id": "finder.new_folder",
      "executor": "helper",
      "operation": "finder.newFolder",
      "risk": "Safe",
      "requiredCapabilities": ["MacCommand"],
      "undoFactory": "closeCreatedFinderWindow"
    }
  ]
}
```

Contract verifier checks:

- unique IDs;
- known executor;
- known typed operation;
- canonical capability names;
- risk parity with helper registry;
- dangerous action has confirmation title/body;
- undo factory exists on both client/helper;
- no shell body;
- no path interpolation template;
- action revision matches canonical SHA-256 function;
- existing catalog reference exists.

### Executor order

1. Load current control by `ControlId`.
2. Verify not expired.
3. Verify state revision is not older than allowed for action.
4. Re-resolve capabilities.
5. Re-resolve registry entry.
6. Recalculate action revision.
7. Apply hard block policy.
8. Return `RequiresReview` if review revision missing.
9. Return `RequiresConfirmation` if dangerous confirmation revision missing.
10. Capture only the pre-state required by the undo factory.
11. Execute through one adapter.
12. Await typed completion with deadline.
13. Refresh affected state.
14. Create exact receipt only on confirmed success.
15. Store receipt in memory.
16. Return result.

Adapters:

- existing catalog → `ActionRunner`;
- Android HID → `HidRepository`;
- helper → typed protocol;
- SSH fallback → fixed bundled registry;
- mode → local ViewModel state;
- composite → ordered, bounded, explicit compensation policy.

### Initial undo

Allowed:

- mute → restore prior mute/volume;
- browser new tab → close created tab when helper can identify it, otherwise no undo claim;
- terminal new tab → close created tab when helper can identify it;
- screenshot → reveal/copy/delete exact Codecks-created path;
- focus timer → stop exact Codecks-created focus session;
- fullscreen → restore prior fullscreen state when window identity is exact.

Not allowed initially:

- arbitrary commands;
- sending messages;
- purchases;
- moving unknown files;
- deleting files not created by Codecks;
- security settings;
- guessed “last window” operations.

Screenshot deletion:

- canonicalize URL/path on helper;
- path must equal receipt path;
- receipt must be unexpired;
- file must carry Codecks creation metadata or reside in the per-install Codecks screenshot directory;
- reject symlinks escaping directory;
- reject `..`;
- user confirmation required;
- delete is performed by helper, never constructed by phone shell text.

---

## 13. Trackpad integration

Current live route:

```text
MouseRoute
 -> MainActivity
 -> TrackpadHostScreen
 -> MouseDestination
 -> MouseViewModel
 -> MouseScreen
 -> Trackpad
 -> RawTrackpadTouchLayer
```

Current problem:

- `TrackpadHostScreen` renders the Trackpad content only while HID is connected.

Target:

- render a Trackpad shell whenever Trackpad route is open;
- keep pointer surface disabled when no pointer transport is ready;
- show Mac state/reactive controls when helper or SSH command transport is ready;
- show direct setup state when neither transport is ready;
- never call helper/SSH from `MouseViewModel.move`, `scroll`, click, gesture callbacks, or raw touch layer.

Create a separate `ReactiveTrackpadViewModel`.

```kotlin
data class ReactiveTrackpadUiState(
    val macState: MacStateSnapshot? = null,
    val controls: List<ReactiveControlUi> = emptyList(),
    val mode: ReactiveTrackpadMode = ReactiveTrackpadMode.Pointer,
    val connectionLabel: String = "Setup needed",
    val lastResult: ReactiveActionResult? = null,
    val pendingConfirmation: ReactiveConfirmationUi? = null,
    val loading: Boolean = false,
)
```

ViewModel owns:

- repository lifecycle based on route visibility;
- current snapshot;
- provider engine;
- mode;
- receipts;
- pending action;
- confirmation;
- result feedback;
- profile/preferences;
- state refresh requests.

ViewModel does not:

- move pointer;
- send HID packets directly;
- construct shell commands;
- execute inside providers;
- mutate Smart;
- show Compose UI.

UI:

```text
┌──────────────────────────────────────┐
│ Chrome · Pull request #42       More│
│ Studio Mac · Pointer · Ready         │
├──────────────────────────────────────┤
│ Back │ Forward │ Reload │ New tab    │
├──────────────────────────────────────┤
│                                      │
│       unchanged pointer region       │
│                                      │
└──────────────────────────────────────┘
```

Rules:

- HUD and strip occupy explicit fixed bounds;
- raw pointer surface receives only the remaining bounds;
- no transparent full-screen clickable overlay;
- 48 dp minimum targets;
- orientation preserves mode and pending confirmation;
- control list updates do not move pointer origin mid-gesture;
- defer visual list replacement while active pointer count > 0;
- `More` opens existing bottom-sheet grammar;
- replace misleading Deck-backed “Dynamic” tray deliberately;
- keep Custom Deck actions distinct;
- use test tags:
  - `reactive-hud`;
  - `reactive-control-strip`;
  - `reactive-control-<id>`;
  - `reactive-more`;
  - `reactive-result`;
  - `reactive-confirmation`.

---

## 14. Evidence levels

Every progress entry must label evidence:

| Level | Meaning |
|---|---|
| E0 | Static/source/compile only |
| E1 | Pure unit and schema contract tests |
| E2 | Loopback client/helper interoperability |
| E3 | Signed local Mac helper on real macOS |
| E4 | Physical Android/Mac or iPhone/Mac flow |
| E5 | Exact signed/unshrunk/notarized release artifacts |

Rules:

- fake transport is not E2;
- loopback is not physical LAN;
- simulator is not iPhone proof;
- debug APK is not release proof;
- built helper is not signed/notarized proof;
- physical gate is not complete without saved logs and artifact hashes;
- no release or physical-device execution without user approval.

---

## 15. Phase execution plan

Each phase is one focused branch/commit series. Do not begin the next phase until its exit gate is recorded.

### Phase 0 — checkpoint, documentation, and ADRs

Goal:

- establish clean `PLATFORM_BASELINE_COMMIT`;
- copy this plan into the repo;
- create progress, decisions, threat, privacy, and physical-test documents;
- record all locked decisions.

Read first:

- `DESIGN.md`;
- `docs/product/NEXT_WAVE_CONTROL_AND_SYNC_PLAN.md`;
- `docs/smart/SMART_SYSTEM.md`;
- `docs/smart/SMART_PRIVACY_MODEL.md`;
- `ConnectionRepository.kt`;
- `DefaultActionRunner.kt`;
- `RawCommandPolicy.kt`;
- `MouseScreen.kt`;
- `MouseViewModel.kt`;
- `TrackpadHostScreen.kt`;
- `HidRepository.kt`;
- `AppModule.kt`;
- root and app Gradle files.

Allowed files:

- `docs/reactive/**`;
- no production code.

Steps:

1. Verify Phase -1 exit gate.
2. Record exact baseline commit and tool versions.
3. Record actual Gradle task names.
4. Create ADR-001 through ADR-010.
5. Create threat model:
   - hostile LAN;
   - spoofed Bonjour;
   - MITM during pairing;
   - replay;
   - stolen phone;
   - compromised paired client;
   - malicious provider data;
   - path traversal;
   - private state leakage;
   - protocol downgrade;
   - helper update compromise.
6. Create privacy data inventory with retention and log rules.
7. Create progress table for Phases 0–16.

Verification:

- doc links resolve;
- baseline clean;
- existing full gates green;
- no app behavior diff.

Stop if:

- worktree is dirty;
- hardening ledger conflicts with Git;
- target-ID migration missing;
- exact test tasks cannot be identified.

### Phase 1 — KMP and project scaffold

Goal:

- add buildable empty shared core and Mac helper test target;
- Android behavior unchanged.

Allowed:

- `settings.gradle.kts`;
- root `build.gradle.kts`;
- new `shared/**`;
- new `protocol/**`;
- new `macHelper/**`;
- progress docs.

Forbidden:

- `MainActivity.kt`;
- `MouseScreen.kt`;
- `ConnectionRepository.kt`;
- HID;
- Smart;
- release workflow.

Steps:

1. Add `org.jetbrains.kotlin.multiplatform` `2.3.21` at root.
2. Add `com.android.kotlin.multiplatform.library` `9.2.0` at root.
3. Include `:shared`.
4. Configure:
   - `android { namespace; compileSdk 37; minSdk 28 }`;
   - `iosArm64()`;
   - `iosSimulatorArm64()`;
   - framework base name `CodecksShared`;
   - common JSON/coroutines dependencies using repository-compatible versions.
5. Enable host tests explicitly; discover exact tasks.
6. Add one `PlatformSmoke` common test.
7. Create Swift helper app with one menu-bar status item and test target.
8. Create protocol schema/fixture directories.

Verification:

- `./gradlew :shared:tasks --all`;
- actual shared host/common tests;
- `./gradlew :app:assembleDebug`;
- Xcode helper unit test;
- `git diff` proves Android behavior unchanged.

Stop if:

- plugin versions force unrelated upgrade;
- new Android-KMP DSL differs from official docs;
- Xcode project generation requires unreviewed third-party tooling.

### Phase 2 — canonical models, codecs, and fixtures

Goal:

- implement identity, capability, state, controls, actions, results, receipts, modes, gestures, and profiles in `commonMain`.

Allowed:

- `shared/src/commonMain/**`;
- `shared/src/commonTest/**`;
- `protocol/schema/**`;
- `protocol/fixtures/**`;
- Swift fixture-only Codable types/tests;
- progress docs.

Forbidden:

- Android UI/data;
- helper native APIs;
- SSH/HID;
- Smart.

Steps:

1. Implement models in sections 8–9.
2. Add canonical JSON configuration:
   - explicit defaults;
   - ignore unknown optional keys;
   - reject missing required keys;
   - no polymorphic class-name leakage.
3. Add canonical action-revision function using SHA-256 over ordered fields.
4. Write valid fixtures for every message/model.
5. Write invalid fixtures:
   - unknown schema;
   - blank ID;
   - oversized string;
   - unknown required enum;
   - negative timestamps;
   - invalid receipt;
   - inconsistent risk/revision.
6. Decode/encode fixtures in Kotlin and Swift tests.
7. Add capability adapter design, but do not edit existing app yet.

Verification:

- E1 common tests;
- Swift fixture tests;
- byte-for-byte canonical revision fixtures;
- no Android/Apple import in `commonMain`.

Stop if:

- Swift and Kotlin canonical bytes differ;
- model requires private state persistence;
- schema cannot express version evolution.

### Phase 3 — protocol state machine and security spike

Goal:

- prove framing, versioning, pairing transcript, P-256 authentication, replay protection, and reconnect semantics in loopback.

Allowed:

- shared protocol/security;
- helper networking/pairing test code;
- protocol fixtures;
- threat/decision docs.

Forbidden:

- Mac state APIs;
- actions;
- Trackpad UI;
- release workflow.

Steps:

1. Implement bounded frame codec.
2. Implement envelope validation.
3. Implement negotiation.
4. Implement deterministic pairing transcript.
5. Implement short authentication string.
6. Implement Android/JVM test key adapter.
7. Implement Swift Keychain key adapter.
8. Implement challenge/response authentication.
9. Implement sequence and replay cache.
10. Implement heartbeat, deadline, and disconnect.
11. Run hostile cases:
    - wrong fingerprint;
    - wrong key;
    - altered transcript;
    - reused challenge;
    - duplicate message;
    - sequence gap;
    - downgrade;
    - oversized frame;
    - slow partial frame;
    - expired request.
12. Review threat model and ADR before Phase 4.

Verification:

- E1 negative tests;
- E2 Kotlin↔Swift loopback;
- no operational request accepted before authentication.

Stop if:

- self-signed identity creation cannot be made deterministic and pin-able;
- pairing can complete without confirmation on both devices;
- replayed execution reaches a handler.

### Phase 4 — minimal native Mac helper

Goal:

- signed local helper with pairing, status, permissions, and basic state.

Allowed:

- `macHelper/**`;
- protocol fixtures;
- helper docs;
- no Android production edits.

Implement:

- menu-bar app;
- random installation ID;
- persistent TLS/signing identity;
- `_codecks._tcp` advertisement;
- pairing window;
- paired phone list/revoke;
- connection count/status;
- `NSWorkspace` front-app events;
- Accessibility permission status;
- active window title when permitted;
- volume/mute read;
- ping;
- full basic snapshot;
- launch-at-login toggle using `SMAppService.mainApp`.

UI:

- status;
- **Pair new phone**;
- permissions;
- paired phones;
- start at login;
- diagnostics;
- quit.

Verification:

- E3 real Mac;
- deny/allow Accessibility;
- revoke client;
- helper restart preserves identity;
- Bonjour conflict renames display instance without changing identity;
- no state logs contain window title.

Stop if:

- helper requires admin;
- helper opens unauthenticated action endpoint;
- permission denial causes repeated prompts.

### Phase 5 — Android helper client

Goal:

- discover, pair, authenticate, reconnect, and display helper capability status.

Allowed:

- new `app/.../platform/helper/**`;
- manifest Internet permission if absent and privacy ledger;
- Settings Mac connection UI;
- DI;
- shared Android adapters;
- tests.

Forbidden:

- pointer/gesture engine;
- removal of SSH/HID;
- Reactive controls;
- Smart;
- release workflow.

Implement:

- `NsdManager` discovery;
- service resolve;
- Android Keystore P-256 key;
- pairing screen;
- SAS confirmation;
- pinned helper identity;
- session client;
- reconnect with bounded backoff;
- capability status;
- revoke/forget;
- protocol mismatch UX.

Verification:

- E1 repository/state tests;
- E2 loopback;
- E4 physical Android + Mac pairing only, with approval;
- helper offline, restart, IP change, duplicate name, revoke, wrong fingerprint.

Stop if:

- private key becomes exportable;
- discovery name is treated as identity;
- pairing state is persisted without helper key/fingerprint.

### Phase 6 — unified Mac state and SSH fallback

Goal:

- one `MacStateRepository`, helper preferred, SSH fallback.

Allowed:

- shared state contract;
- `data/macstate/**`;
- Mac probe assets/manifest;
- narrow `ConnectionRepository` bundled-stdin raw gateway;
- DI;
- repository tests;
- verifier scripts.

Forbidden:

- `MouseViewModel` pointer methods;
- `MouseScreen`;
- Smart;
- free-form policy weakening.

Implement:

- helper backend;
- SSH backend;
- backend selector;
- merger;
- staleness;
- lifecycle;
- classifier;
- bounded JSON codec;
- probe gateway;
- read-only JXA assets.

Verification:

- valid/missing/unknown/oversized/truncated JSON;
- partial failure retains stale prior field;
- Mac switch never merges;
- helper preferred;
- helper failure falls back;
- both fail yields stale/unavailable without crash;
- Mac verifier compiles probes;
- security test proves arbitrary caller cannot send JXA through safe-template path.

Stop if:

- probe must run on UI thread;
- parser needs summary output;
- selection is polled;
- state fields are persisted.

### Phase 7 — pure Reactive engine and MVP providers

Goal:

- deterministic controls without execution/UI.

Allowed:

- shared reactive engine/providers/policy;
- registry schema/asset;
- pure tests;
- verifier.

Forbidden:

- repositories in providers;
- UI;
- helper/SSH calls;
- Smart;
- LLM.

Implement:

- engine;
- registry;
- policy;
- ranking;
- Browser, Terminal, Finder, Media, Generic Window, Undo providers.

MVP controls:

- Browser: Back, Forward, Reload, New tab.
- Terminal: Paste, Interrupt, Clear, New tab.
- Finder: Back, Downloads, Desktop, New folder.
- Media: Play/pause, Next, Previous, Mute.

Verification:

- every provider/context;
- generic fallback;
- missing capability removal;
- stale penalty;
- deterministic tie;
- duplicate mismatch rejection;
- max six;
- dangerous penalty;
- exact risk/registry parity.

### Phase 8 — executor, receipts, and undo

Goal:

- execute typed controls through existing systems and return exact receipts.

Allowed:

- shared execution contracts;
- Android adapters;
- helper typed action handlers;
- receipt store;
- tests.

Forbidden:

- UI;
- Smart;
- arbitrary protocol shell;
- guessed undo.

Implement:

- executor order in section 12;
- adapters;
- in-memory receipt store;
- expiry;
- mute undo;
- only exact-ID tab undo;
- screenshot receipt contract without screenshot UI;
- mode action.

Verification:

- review and danger are separate;
- confirmation bound to action revision;
- edit invalidates authorization;
- capability rechecked;
- stale control rejected;
- failure has no receipt;
- expired undo absent;
- replay returns prior result without repeat execution;
- helper independently rejects bad revision.

### Phase 9 — Reactive Trackpad ViewModel and Android UI

Goal:

- HUD, four controls, More, confirmation, results; pointer unchanged.

Allowed:

- `ui/mouse/reactive/**`;
- carefully scoped `TrackpadHostScreen.kt`;
- carefully scoped `MouseScreen.kt`;
- route wiring after re-reading current `MainActivity.kt`;
- debug instrumentation tests.

Forbidden:

- raw touch/gesture/HID internals unless a failing regression proves necessity;
- Smart;
- release workflow.

Steps:

1. Add default-off `ReactiveTrackpad` flag. Do not repurpose Smart flags.
2. Add `ReactiveTrackpadViewModel`.
3. Render Trackpad shell independent of HID connection.
4. Add HUD.
5. Add stable strip.
6. Add More sheet.
7. Add confirmation.
8. Add result/undo feedback.
9. Preserve Custom Deck actions.
10. Rename/remove old misleading Dynamic tray only with migration/UI test.

Verification:

- four visible;
- extras in More;
- undo first;
- danger in More;
- pointer bounds stable;
- strip intercepts only visible bounds;
- no recomposition during active gesture moves pointer origin;
- orientation preserves mode;
- helper/SSH timeout does not delay HID callbacks;
- all existing gesture instrumentation passes.

Performance evidence:

- trace input path;
- no helper/SSH stack frame in pointer callbacks;
- no StrictMode network-on-main;
- no dropped-input regression under forced 30-second SSH timeout.

### Phase 10 — MVP physical behavior

Goal:

- prove Browser, Terminal, Finder, and Media on supported real Macs.

No new feature scope.

Matrix:

- Safari and Chrome;
- Terminal and iTerm if supported;
- Finder;
- Music and Spotify if supported;
- one and multiple displays;
- Accessibility allowed/denied;
- helper online/offline;
- SSH fallback;
- HID connected/disconnected.

Evidence:

- E4 physical Android/Mac with approval;
- action/result logs contain only codes;
- unsupported controls disappear;
- normal Trackpad continues after helper/SSH loss.

Do not mark MVP complete from mocks.

### Phase 11 — modes and rich helper APIs

Implement in separate subphases:

11A. Pointer, Precision, Fast travel.

11B. Window mode:

- next/previous window;
- left/right half;
- maximize/minimize;
- close review;
- quit dangerous.

11C. Cursor:

- active display center;
- active window center;
- corners;
- menu bar;
- Dock;
- next display.

11D. Screenshot:

- full/window/region;
- exact path receipt;
- reveal/copy/open/delete.

Each subphase has its own tests and physical gate. Hide unverified app/version-specific controls.

### Phase 12 — clipboard and explicit text selection

Goal:

- manual, private flows before automation.

Implement:

- clipboard metadata;
- push/pull;
- paste plain text;
- sensitive classifier;
- explicit **Inspect selection**;
- local preview;
- explicit AI-send approval;
- transformed preview;
- explicit insert.

Privacy:

- no content persistence by default;
- encrypted bounded history only after separate opt-in;
- private/sensitive items expire rapidly;
- no content in logs;
- clear controls;
- AI never receives content silently.

Verification:

- permission denial;
- size limits;
- sensitive detection;
- expiry;
- background clearing;
- AI cancel sends zero bytes.

### Phase 13 — profiles and gestures

Resolution:

```text
Mac + app
App
Mac
Global
Defaults
```

Implement:

- pinned/hidden controls;
- default mode;
- pointer/scroll settings;
- explicit gesture bindings;
- versioned persistence;
- migration;
- profile editor;
- AI Builder draft adapter.

Rules:

- no silent profile creation;
- no suggestion overwrites a binding;
- AI saves disabled/unassigned;
- dangerous binding requires explicit review.

### Phase 14 — iOS controller

Goal:

- helper-only controller with shared core.

Implement:

- SwiftUI shell;
- KMP framework integration;
- Local Network permission;
- `_codecks._tcp` declaration;
- Keychain P-256 key;
- pairing/auth;
- state/HUD/controls;
- pointer deltas/click/scroll/keyboard through helper;
- lifecycle reconnect;
- clear background behavior.

Verification:

- common tests;
- Swift tests;
- simulator UI only;
- E4 real iPhone/Mac pairing/input with approval;
- permission reset/rejection;
- background/foreground reconnect;
- phone lock/unlock;
- helper sleep/wake.

Do not claim Android HID parity at transport level; claim product behavior parity.

### Phase 15 — Smart integration

Goal:

- Smart may propose already-policy-filtered reactive controls.

Implement:

- adapter from `MacStateSnapshot.frontApp` to `SmartAppKey`;
- provider emitting reactive `ControlId` references;
- default-off feature gate;
- Trackpad surface support;
- completion feedback adapter.

Rules:

- Reactive policy runs before Smart;
- executor policy runs again after Smart;
- Smart never receives command bodies;
- Smart confidence never authorizes;
- private state is not persisted;
- Smart never moves pointer or executes automatically.

Verification:

- stale, private, unsupported, unreviewed, and dangerous controls remain gated;
- disabled Smart changes no Reactive behavior;
- no new LLM ranking call;
- existing Smart privacy/learning tests pass.

### Phase 16 — release hardening

Android:

- full unit/lint/check;
- managed emulator where available;
- signed/unshrunk APK;
- exact-artifact physical helper and SSH gates;
- checksum provenance;
- no rebuild after gate.

Mac:

- unit/UI tests;
- universal archive;
- Developer ID signing;
- hardened runtime;
- notarization;
- stapling;
- clean-Mac install;
- first-run permission UX;
- update strategy ADR and rollback.

iOS:

- archive;
- signing;
- TestFlight/internal distribution;
- Local Network/Bonjour privacy review;
- real-device matrix.

Cross-platform:

- protocol compatibility matrix: current and previous supported version;
- upgrade/downgrade;
- key rotation;
- revoke;
- offline fallback;
- corrupted persistence recovery;
- secret scan;
- privacy ledger;
- accessibility audit;
- performance budgets;
- exact artifact hashes.

No merge, release, notarization submission, TestFlight upload, or physical-device run without user approval.

---

## 16. Phase-level agent contract

Give an implementation agent exactly one phase:

```markdown
# Agent contract

Repository: this repository checkout
Baseline: `<exact PLATFORM_BASELINE_COMMIT>`
Branch/worktree: `<exact isolated path and branch>`
Mission: implement Phase `<N>` only.

Read first:
- exact files
- exact official documentation

Allowed files:
- exact paths

Forbidden files:
- exact paths

Required contracts:
1. exact behavior
2. exact safety boundary
3. exact compatibility rule

Copy patterns from:
- exact source file and symbol

Tests to add:
1. exact case
2. exact case

Verification:
- discover task names first
- exact commands that exist

Evidence level required:
- E0/E1/E2/E3/E4/E5

Stop and report if:
- symbol/API differs
- baseline is dirty
- dependency change is needed but not listed
- persistence migration can lose data
- command policy would need weakening
- signing/notarization/physical hardware is required
- test task does not exist
- another phase’s file must change

Completion report:
- sources read
- changed files
- tests added
- commands and PASS/FAIL
- tests not run
- evidence level
- blockers
- remaining risk
- progress ledger update
- commit hash
```

Every report must cite sources and exact symbols. “Implemented” without test/evidence is rejected.

---

## 17. Coordination and file ownership

Never run these phases concurrently:

- Phase 5 and Phase 6: DI/connection integration;
- Phase 8 and Phase 9: executor/UI contract;
- Phase 9 and any Trackpad/HID reliability work;
- Phase 15 and Smart hardening;
- Phase 16 and release workflow work.

Single owner required:

- `MainActivity.kt`;
- `MouseScreen.kt`;
- `TrackpadHostScreen.kt`;
- `ConnectionRepository.kt`;
- `AppModule.kt`;
- `app/build.gradle.kts`;
- `.github/workflows/release.yml`;
- action/probe registries;
- protocol schemas.

Before every phase:

```bash
git status --short
git rev-parse HEAD
git log -5 --oneline --decorate
git diff --check
```

If HEAD differs from the phase baseline, rebase/replan before editing. Never overwrite unrelated user changes.

---

## 18. Required test inventory

### Shared model/protocol

- ID validation;
- canonical revision bytes;
- valid/invalid fixtures;
- unknown optional field;
- unknown schema;
- version overlap/no overlap;
- bounded frame;
- partial frame timeout;
- replay;
- sequence gap;
- deadline;
- pairing transcript;
- SAS parity;
- wrong signature/fingerprint/key;
- key rotation/revoke.

### Mac state

- valid JSON;
- missing fields explicit;
- unknown schema;
- oversized/truncated output;
- partial failure retains stale value;
- Mac switch isolation;
- source precedence;
- helper delta gap resync;
- explicit selection only;
- private clearing.

### Providers/engine

- every app kind;
- generic app;
- every mode;
- missing capability removal;
- stale penalty;
- pin/hide;
- duplicate merge;
- duplicate mismatch rejection;
- stable tie order;
- maximum six;
- Undo first;
- no LLM/I/O imports.

### Safety/execution

- hard blocked action;
- review required;
- confirmation required;
- review and confirmation independent;
- confirmation revision mismatch;
- current capability recheck;
- current state revision recheck;
- expired control;
- helper server-side rejection;
- failure has no receipt;
- replay no double execution;
- cancel/timeout;
- composite partial failure;
- screenshot path traversal/symlink.

### Android UI/HID

- four visible;
- overflow;
- HUD update;
- setup status;
- controls do not cover pointer;
- no transparent interception;
- orientation;
- active gesture + control update;
- helper/SSH timeout;
- all existing gesture tests;
- disconnect releases buttons;
- stale queued HID input invalidated.

### Helper

- Bonjour publish/conflict;
- pairing window expiry;
- paired/revoked clients;
- Accessibility deny/allow;
- front-app event;
- window read failure;
- sleep/wake;
- launch-at-login deny/allow;
- Keychain persistence;
- no private logging.

### Physical

- Android + supported Mac versions;
- iPhone + supported Mac versions;
- one/multiple displays;
- app matrix;
- network IP change;
- Mac sleep/wake;
- phone app background/foreground;
- helper update;
- SSH fallback;
- HID fallback;
- Accessibility and Screen Recording denial.

---

## 19. Performance and reliability budgets

Measure; do not merely assert:

- pointer path performs no disk/network access;
- no pointer callback waits on a coroutine job;
- no helper/SSH work on main thread;
- state update to HUD target: under 250 ms p95 for helper events;
- basic SSH refresh never faster than 750 ms;
- one in-flight probe per Mac/topic;
- stale probe result cannot overwrite newer helper revision;
- reconnect uses bounded exponential backoff with jitter;
- control engine completes under 10 ms p95 for 100 candidate controls on supported phone;
- control updates are conflated;
- receipt store is bounded;
- replay cache is bounded and TTL-pruned;
- helper idle CPU/memory recorded on supported Macs;
- all payload, string, list, and output sizes are bounded.

---

## 20. Final definition of done

Platform:

- [ ] Hardening checkpoint is clean and committed.
- [ ] Target IDs are random and migrated.
- [ ] `:shared` builds for Android, iOS device, and iOS simulator.
- [ ] Native universal Mac helper is signed and notarized.
- [ ] Android and iOS authenticate to helper.
- [ ] Protocol is versioned, bounded, replay-safe, and fixture-tested.
- [ ] Helper can revoke individual phones.
- [ ] SSH and Android HID remain functional fallbacks.

Reactive system:

- [ ] One merged typed Mac snapshot exists.
- [ ] Per-field source/freshness is explicit.
- [ ] Providers are pure.
- [ ] Capability/safety/prefs filter before ranking.
- [ ] Ranking is deterministic.
- [ ] Four controls are visible; max six ranked.
- [ ] UI carries IDs/revisions, never command strings.
- [ ] Executor rechecks authorization.
- [ ] Results are typed.
- [ ] Undo uses exact receipts.
- [ ] Failure creates no receipt.
- [ ] Screenshot delete is path-bound and confirmed.
- [ ] Pointer remains independent from helper/SSH.
- [ ] Browser, Terminal, Finder, and Media physical matrix passes.

Privacy/safety:

- [ ] No endpoint-derived identity.
- [ ] No raw shell protocol.
- [ ] No selected-text polling.
- [ ] Clipboard/selection/window titles are not logged.
- [ ] AI transmission is explicit.
- [ ] Smart remains default-off and cannot authorize.
- [ ] Dangerous confirmation is revision-bound and per run.
- [ ] Reviewed free-form and bundled execution remain separate.
- [ ] Unsupported capabilities hide controls.

Release:

- [ ] Unit/contract/integration gates pass.
- [ ] Physical evidence is labeled accurately.
- [ ] Tested artifact equals published artifact.
- [ ] Android signed/unshrunk gate passes.
- [ ] Mac signing/notarization/install gate passes.
- [ ] iOS real-device gate passes.
- [ ] Protocol compatibility and rollback pass.
- [ ] All hashes, logs, and progress evidence are recorded.

The project is not complete because code compiles, mocks pass, a helper launches, or a debug phone works. It is complete only when the applicable evidence level for every shipped capability is recorded and all fallback, privacy, safety, and exact-artifact gates pass.
