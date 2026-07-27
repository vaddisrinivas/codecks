# Codecks Desk Value, Lockscreen Trackpad, UX, and Reactive Expansion Plan

Status: implementation blueprint; no implementation is claimed by this document.

Prepared: July 27, 2026.

Repository: this repository checkout

Current inspected branch: `codex/ux-polish-desk-launch`

Current inspected HEAD: `c581890` (`Polish deck and keyboard interactions`)

Current public release baseline: `c453da7`, tag `v0.1.21`

Governing Reactive blueprint:

- `docs/reactive/REACTIVE_PLATFORM_IMPLEMENTATION_PLAN.md`

This document is an ordered addendum to that blueprint. It adds:

1. honest README value evidence and tradeoffs;
2. a physical-access-safe lockscreen Trackpad;
3. a structured UX papercut program;
4. Tasker/NFC DeskDock entry;
5. a later probabilistic DeskDock confidence engine;
6. Apple Shortcuts, app shortcut discovery, Spotlight/SFTP, display brightness, and Accessibility integrations.

It does not replace the Reactive architecture. When the documents conflict, the
explicit supersessions in this document control.

---

## 1. Required outcomes

### 1.1 README outcome

Add a bottom-of-README section that:

- explains that phones are commonly already nearby;
- uses dated, reproducible samples instead of invented market averages;
- separates avoidable hardware cost from desk-space opportunity cost;
- uses square inches, not linear inches;
- does not claim Codecks literally returns cash;
- states setup, accuracy, learning, availability, battery, privacy, and ergonomic tradeoffs;
- links every quantitative claim to an evidence note;
- records sample date, sample members, calculation, and limitations.

### 1.2 Lockscreen outcome

When all are true:

- the phone has been unlocked once since boot;
- the user explicitly enabled lockscreen Trackpad;
- Bluetooth permission was already granted;
- the selected HID host is already connected;
- the keyguard is showing;

Codecks may show a restricted surface over keyguard with only:

- pointer movement;
- vertical and horizontal scroll;
- left, right, and middle mouse buttons;
- guaranteed release of held mouse buttons.

It must not expose or initiate:

- HID registration, pairing, reconnect, host selection, or disconnect;
- typed text or any `HidCommand`;
- configured multi-finger shortcut gestures;
- media/consumer controls;
- Deck, custom, or Dynamic/Reactive actions;
- SSH or Mac-helper actions;
- Keyboard, Clipboard, Rules, AI, notifications, history, settings, or connection details;
- external links or arbitrary destinations.

If HID disconnects, the restricted surface releases all held buttons and closes.

### 1.3 UX outcome

Turn “clicks and other things are here and there” into a reproducible ledger:

- every item has exact steps, expected/actual behavior, device/build, evidence, owner, and regression test;
- transient feedback uses one consistent grammar;
- tap, long-press, drag, multi-touch, and back behavior do not conflict;
- visible controls meet target size and accessibility semantics;
- no item is called fixed from appearance alone.

### 1.4 Reactive/integration outcome

Keep the original four layers:

```text
typed Mac state
  -> pure providers
  -> typed executor + exact receipts/undo
  -> Trackpad UI
```

Add later integrations through those layers. No integration may directly inject
shell strings into UI or bypass execution policy.

---

## 2. Current repository truth

Verified at plan creation:

- worktree is clean;
- `c581890` is one commit after `v0.1.21`;
- `README.md` says `v0.1.21`;
- `codecks://trackpad` exists in `IntentDestinationPolicy.kt`;
- the Tasker guide already uses **BT Near + power + face-up**;
- `HidSessionService` is a non-exported `connectedDevice` foreground service;
- `HidState.isConnected` exists and is callback-derived;
- `MainActivity` warms HID before destination routing and has no keyguard policy;
- the public Trackpad deep link currently opens full `MainActivity`;
- `MouseScreen` contains keyboard shortcuts, gesture commands, notification previews,
  custom actions, Dynamic actions, settings, and navigation;
- lockscreen entry code exists:
  - `TrackpadEntryActivity`;
  - `LockscreenTrackpadActivity`;
  - `LockscreenTrackpadViewModel`;
  - `LockscreenTrackpadScreen`;
- widget scaffolding exists:
  - `TrackpadWidgetProvider`;
  - `trackpad_widget_info.xml`;
  - policy tests for widget routing;
- release `isMinifyEnabled` and `isShrinkResources` are both `false`;
- production package `app.codecks` and its data are protected.

Already implemented in `c581890`, but not yet part of the public release:

- Deck feedback polish;
- Keyboard **Send + Enter**;
- explicit **Enter** and **Command + Enter**;
- clear-after-success behavior;
- public `codecks://trackpad` routing;
- Tasker integration documentation;
- lockscreen pointer-only policy scaffolding;
- lockscreen activity and widget entry scaffolding;
- typed protocol codec/receipt-store foundation;
- typed Reactive state/provider/executor/viewmodel foundation.

Do not reimplement these. Verify, release, then build on them.

### 2.1 Immediate execution order from current truth

The next agent should not restart from the original addendum sequence literally.

From the current repository state, the ordered path is:

1. keep the README value/tradeoff copy honest and sourced;
2. finish physical proof of the current deck/keyboard/Trackpad/lockscreen patch;
3. prove the lockscreen boundary on the user’s actual phone;
4. ship the signed unshrunk release only after exact-artifact SSH + HID proof;
5. resume Reactive from the already-landed foundation, starting with:
   - real `MacStateRepository` wiring;
   - Trackpad host/UI integration;
   - feature flag and guarded visibility;
   - physical proof that Reactive never leaks into the lockscreen surface;
6. only then build DeskDock confidence, Apple Shortcuts, importer, Spotlight/SFTP,
   brightness, and Accessibility discovery.

### 2.2 New papercut priorities from live use

The current user report adds three concrete priorities that must stay near the
top of the queue:

1. feedback polish:
   - no permanent `Completed: completed`-style duplicate copy;
   - prefer transient feedback or direct button-state changes;
2. keyboard interaction truth:
   - Send must either submit with Enter semantics or clearly separate those paths;
   - explicit `Enter` must remain nearby;
   - success clears draft; failure preserves it;
   - multi-touch / near-simultaneous action-row behavior must be physically characterized,
     then either supported intentionally or fail predictably;
3. lockscreen + auto-launch:
   - if HID is already connected, Trackpad entry may work while locked;
   - every non-pointer action remains guarded behind unlock;
   - Tasker/DeskDock launch signals stay outside authorization.

---

## 3. Supersessions and invariants

### 3.1 Baseline supersession

The original Reactive plan was prepared against `v0.1.19` and a dirty hardening
worktree. Its facts are historical.

Before Reactive Phase 0:

1. audit current Git and hardening state;
2. verify whether its target-ID migration and other Phase -1 defects still exist;
3. record a new `PLATFORM_BASELINE_COMMIT`;
4. amend the copied plan with current facts;
5. do not blindly redo completed hardening.

### 3.2 Release supersession

The original plan says “signed/minified APK” in Phase 16. That is obsolete.

Production release must remain:

```kotlin
isMinifyEnabled = false
isShrinkResources = false
```

Required release evidence is the exact **signed, unshrunk** APK. Re-enabling
minification or resource shrinking is forbidden without explicit user approval
in the current conversation and physical phone + real Mac SSH proof of that exact
artifact.

### 3.3 Protected phone invariant

Never:

- uninstall or clear `app.codecks`;
- install a differently signed APK over `app.codecks`;
- downgrade it;
- target the physical phone with instrumentation without explicit approval;
- stop or replace the protected release merely to test debug.

An in-place release update requires certificate comparison before installation.

### 3.4 Authorization invariant

State, proximity, a deep link, a widget tap, Tasker, NFC, a visible button, and
Smart ranking are not authorization.

The lockscreen exception authorizes only pointer primitives under the exact policy
in section 1.2.

---

## 4. Allowed APIs and documentation

Implementation agents must open the official source immediately before coding.

### Android lockscreen and lifecycle

Allowed:

- `Activity.setShowWhenLocked(boolean)`;
- `Activity.setTurnScreenOn(boolean)`, initially kept `false`;
- `KeyguardManager.isKeyguardLocked()`;
- `KeyguardManager.isDeviceLocked()`;
- `KeyguardManager.requestDismissKeyguard(...)` only after an explicit user tap;
- `UserManager.isUserUnlocked()`;
- immutable explicit `PendingIntent`;
- `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`;
- existing `BluetoothHidDevice` path.

Sources:

- https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean)
- https://developer.android.com/reference/android/app/Activity#setTurnScreenOn(boolean)
- https://developer.android.com/reference/android/app/KeyguardManager
- https://developer.android.com/privacy-and-security/direct-boot
- https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device
- https://developer.android.com/guide/components/activities/secure-bal

Forbidden:

- `showWhenLocked` on `MainActivity`;
- `SYSTEM_ALERT_WINDOW` as a bypass;
- Accessibility as a keyguard bypass;
- deprecated `KeyguardLock.disableKeyguard`;
- deprecated `FLAG_DISMISS_KEYGUARD`;
- automatic `requestDismissKeyguard`;
- full-screen notification intents;
- Direct Boot storage for selected hosts, SSH data, API keys, or intent tokens;
- trusting intent extras for lock, connection, or authorization state.

### Widgets and entry points

Allowed:

- a normal home-screen AppWidget;
- an ongoing HID notification action;
- an exact public `codecks://trackpad` URI;
- Samsung lockscreen shortcut/widget behavior only after physical proof.

Source:

- https://developer.android.com/develop/ui/views/appwidgets/overview

Do not promise a portable Android lockscreen widget. Standard app widgets are
home-screen surfaces; lockscreen hosting is launcher/OEM dependent.

### Tasker

Allowed:

- **BT Near**, not only **BT Connected**;
- power state;
- face-up orientation;
- optional home Wi-Fi;
- launch-app Data `codecks://trackpad`.

Sources:

- https://tasker.joaoapps.com/userguide/en/help/sh_bt_near.html
- https://tasker.joaoapps.com/userguide/en/help/ah_load_app.html

Tasker launches Codecks. It does not store Codecks SSH credentials, run Mac probes,
or decide authorization.

### Apple Shortcuts

Allowed after helper/state/executor foundations:

- official `shortcuts list`;
- official `shortcuts run`;
- typed helper operations with bounded input/output and deadlines.

Source:

- https://support.apple.com/guide/shortcuts-mac/run-shortcuts-from-the-command-line-apd455c82f02/mac

Do not concatenate imported shortcut names into arbitrary shell text.

### Reactive platform APIs

The KMP, Network framework, Bonjour, Keychain, Accessibility, AppKit, Android NSD,
Android Keystore, and iOS APIs allowed by the original Reactive plan remain
controlling.

---

## 5. README evidence contract

### 5.1 Claims that may be made

Phone placement:

- CHI 2013 “Phoneprioception”:
  - 68% of 650 respondents reported their phone on a table or desk when asked;
  - 83% of 693 had placed it on a table or desk in the prior 24 hours;
  - office subset: 49% of 93 reported table placement in the office.
- Current ownership context:
  - Pew 2025 reports 91% of US adults own a smartphone.

Required caveats:

- placement data is from 2013;
- it is not a current population-representative survey;
- “table or desk” does not specifically mean a computer desk;
- the office subset is small;
- ownership does not prove placement.

Primary sources:

- https://citeseerx.ist.psu.edu/document?doi=16baf1a983217b965bd72b868086126e3e24634c&repid=rep1&type=pdf
- https://www.pewresearch.org/chart/mobile-phone-ownership-2/

### 5.2 Dated manufacturer snapshot

Do not call this a market average or average consumer spend.

July 27, 2026 snapshot:

- 18 visible US Logitech pointing devices:
  - range: `$20.00–$119.99`;
  - arithmetic mean: `$65.55`;
  - median: `$64.99`;
- Razer Gigantus V2:
  - sampled SKU prices: `$15.00` and `$20.00`;
  - mean sampled pad price: `$17.50`;
  - Medium/Large size table: `14.17 × 10.83 in` and `17.72 × 15.73 in`;
  - Medium/Large area: `153.46–278.74 in²`;
- illustrative mouse + pad mean: `$83.05`.

Sources:

- https://www.logitech.com/en-us/shop/c/mice
- https://www.razer.com/gaming-mouse-mats/Razer-Gigantus-V2/RZ02-03330200-R3U1
- https://www.razer.com/gaming-mouse-mats/Razer-Gigantus-V2/RZ02-03330300-R3U1

Limitations:

- manufacturer list prices;
- not sales-weighted;
- not the whole market;
- the pointing-device sample includes different device classes;
- prices and availability change.

### 5.3 Desk-space illustration

July 27, 2026 US IKEA sample:

| Desk | Surface | Price |
|---|---:|---:|
| MICKE | `28.75 × 19.625 in` | `$69.99` |
| KALLAX desk | `43.75 × 15.375 in` | `$79.99` |
| LAGKAPTEN / ALEX desk | `55.125 × 23.625 in` | `$239.99` |

Illustrative sample calculations:

- mean surface area: `846.40 in²`;
- mean price: `$129.99`;
- allocated desk cost: approximately `$0.1536/in²`;
- Medium/Large sample pad area: `153.46–278.74 in²`;
- percentage of illustrative desk surface: `18.1%–32.9%`;
- corresponding surface allocation: approximately `$23.57–$42.81`.

Sources:

- https://www.ikea.com/us/en/cat/desks-computer-desks-20649/

Required formulas:

```text
desk area = desk width × desk depth

desk allocation per square inch
  = desk price / desk area

net area made available
  = mousepad area
  - marginal phone/stand footprint when the phone was not already on the desk

space allocation
  = net area × desk allocation per square inch

conditional avoided hardware purchase
  = selected mouse price + selected mousepad price
```

Never add space allocation to hardware price and call the total “Codecks’ value.”
One is opportunity cost; the other may be avoided cash spend.

### 5.4 Copy target

Place the finished sections after **FOSS Distribution** and before
**Contributing**:

```markdown
## Use the screen already beside your computer

Phones are commonly already within reach. In a 2013 CHI phone-placement study,
68% of 650 respondents had their phone on a table or desk when asked, and 83% of
693 respondents had placed it there during the prior 24 hours.[^phone-placement]
Current Pew data says 91% of US adults own a smartphone.[^phone-ownership]

Codecks turns that existing screen into a Bluetooth trackpad and command surface.
That can avoid another pointer purchase and make mousepad space available for
writing, devices, or simply a less crowded desk.

A July 27, 2026 manufacturer snapshot—not a market-wide average—put 18 visible
Logitech pointing devices at $20.00–$119.99, averaging $65.55. Two sampled
Razer Gigantus V2 SKU pages were $15.00 and $20.00, putting that small
illustrative mouse-plus-pad snapshot around $83.05 before tax and shipping.[^pad-snapshot]
The current Gigantus V2 medium and large size table spans about 153–279 square
inches.[^pad-snapshot]

For scale, three current IKEA computer-desk examples averaged 846 square inches
of surface and $129.99, or about $0.15 per square inch.[^desk-snapshot] The sample
pads represent roughly 18–33% of that illustrative surface, with a $24–$43 space
allocation. This is an opportunity-cost illustration—not cash Codecks promises
to recover. Your result depends on your desk, pointer, pad, phone, and whether the
phone was already there.

## Tradeoffs

- Initial Bluetooth pairing and minor setup are required. Deck and automation
  commands also require a configured Mac connection.
- A touchscreen trackpad does not feel identical to a dedicated mouse. Pointer
  speed, tap thresholds, gestures, and muscle memory may need adjustment.
- Bluetooth, Android background rules, lockscreen behavior, and vendor power
  management can affect availability.
- The phone remains occupied while used as a full-screen trackpad.
- Codecks does not claim ergonomic superiority over a physical mouse. Use
  whichever input method is more comfortable for the task.
```

Footnotes must state sample date, method, and limitations. Do not hide them only
in another document.

---

## 6. Master dependency graph

```text
A0 baseline reconciliation
 ├─> A1 README evidence
 └─> A2 papercut audit/fixes
       └─> L0 lockscreen policy/threat model
            └─> L1 entry router + restricted activity
                 └─> L2 widget/notification/Tasker entry
                      └─> L3 physical proof + signed unshrunk release
                           └─> new PLATFORM_BASELINE_COMMIT
                                └─> R0…R10 original Reactive plan
                                     ├─> D0 minimal Tasker/NFC DeskDock
                                     └─> D1 DeskDock confidence engine
                                          ├─> I1 Apple Shortcuts
                                          ├─> I2 app/keyboard shortcuts
                                          ├─> I3 Spotlight + SFTP
                                          └─> I4 brightness adapters
                                               └─> I5 Accessibility discovery
                                                    └─> F final release/docs
```

`D0` configuration/documentation may be prepared earlier, but it must not be
described as exact zero-touch position detection.

---

## 7. Phase A0 — baseline and document reconciliation

Goal:

- turn historical assumptions into current facts;
- preserve `c581890`;
- decide whether to release that patch before new code;
- copy the governing Reactive plan into the repository;
- correct the no-shrink conflict.

Read first:

- `AGENTS.md`;
- `README.md`;
- `docs/release/RELEASING.md`;
- `docs/release/CODECKS_RELEASE_LEDGER.md`;
- `docs/release/PHYSICAL_SSH_RELEASE_GATE.md`;
- `docs/integrations/TASKER_TRACKPAD_AUTOLAUNCH.md`;
- `docs/product/NEXT_WAVE_CONTROL_AND_SYNC_PLAN.md`;
- the governing Reactive blueprint;
- `app/build.gradle.kts`;
- `app/src/main/AndroidManifest.xml`;
- `IntentDestinationPolicy.kt`;
- `MainActivity.kt`;
- `HidSessionService.kt`;
- `HidRepository.kt`;
- `MouseScreen.kt`;
- `KeyboardScreen.kt`;
- `KeyboardViewModel.kt`.

Allowed files:

- `docs/reactive/**`;
- `docs/product/**`;
- `docs/ux/**`;
- no production code.

Steps:

1. Record:
   - branch;
   - HEAD;
   - upstream;
   - worktree status;
   - tags;
   - current app version;
   - actual Gradle tasks.
2. Compare `c581890` against `v0.1.21`.
3. Verify current patch tests before changing its files.
4. Inspect whether old hardening blockers remain.
5. Copy the original Reactive plan to
   `docs/reactive/REACTIVE_PLATFORM_IMPLEMENTATION_PLAN.md`.
6. Add a current-state header and link this addendum.
7. Change its release wording from “minified” to “unshrunk.”
8. Record `PRE_LOCKSCREEN_BASELINE_COMMIT`.

Verification:

```bash
git status --short
git branch --show-current
git rev-parse HEAD
git log -12 --oneline --decorate
git diff --check
./gradlew :app:tasks --all
```

Then run only discovered tasks. Expected current local gate:

```bash
./gradlew --no-daemon :app:testReleaseUnitTest :app:lintDebug :app:assembleDebug
```

Stop if:

- worktree becomes dirty from unrelated work;
- release signing would be needed without private environment access;
- a current patch test fails;
- the baseline differs from this document and the difference is not reconciled.

Exit:

- current truth recorded;
- plans live in repo;
- current patch is either released or explicitly preserved as the new baseline;
- no production code changed in this phase.

---

## 8. Phase A1 — README value evidence

Goal:

- publish useful quantitative context without deceptive statistics.

New files:

- `docs/marketing/DESK_VALUE_EVIDENCE.md`;
- `docs/marketing/desk_value_snapshot.csv`.

Edit:

- `README.md`.

CSV schema:

```text
captured_on,category,manufacturer,product,variant,price_usd,width_in,depth_in,area_in2,source_url,notes
```

Evidence document:

- source access date;
- inclusion/exclusion rules;
- raw sample rows;
- exact formulas;
- computed mean, median, range;
- limitations;
- README sentence mapped to evidence row/formula;
- refresh owner and next review date.

Implementation:

1. Re-open every source.
2. Record current values. If a value changed, recompute instead of copying this plan.
3. Exclude sale prices unless the row explicitly says promotional.
4. Keep manufacturer categories distinct.
5. Add README copy from section 5.4 with updated values.
6. Fix README’s stale public-version statement in the same docs-only commit.
7. Put full footnotes at the bottom of README.

Tests:

- every footnote ID resolves;
- every URL is HTTPS;
- CSV arithmetic reproduces README values;
- `rg` finds none of:
  - “average market price”;
  - “average consumer spends”;
  - “68% have phones on computer desks”;
  - “Codecks saves you $”;
- Markdown links render;
- `git diff --check`.

Anti-pattern guards:

- no unqualified average;
- no false precision beyond cents for price or one decimal for percentage;
- no counting desk-space allocation as cash;
- no implying the user will discard an existing mouse;
- no hiding all caveats outside README.

Exit:

- evidence file and CSV reproduce every number;
- README is dated, clear, and honest.

---

## 9. Phase A2 — UX papercut audit and scoped fixes

Goal:

- capture and fix existing interaction friction before starting the platform.

New file:

- `docs/ux/PAPER_CUT_AUDIT.md`.

Ledger row:

```text
ID
surface
build/commit
device/window mode
input type
precondition
steps
expected
actual
frequency
severity
video/screenshot/log
suspected owner
test to add
status
commit
physical proof
```

Audit matrix:

| Surface | Required checks |
|---|---|
| Deck | tap, simultaneous touches, long-press, running/success/error state, duplicate status text |
| Trackpad | tap threshold, drag, click, right click, scroll, multi-touch, tray overlap, Back |
| Keyboard | Send, Send + Enter, Enter, Command + Enter, clear-on-success, retry-on-failure, simultaneous modifier keys |
| Navigation | drawer, bottom navigation, Back, More sheet, orientation |
| Connection | reconnect state, stale status, permission missing, Mac asleep |
| Accessibility | labels, roles, focus order, 48 dp targets, contrast, TalkBack action wording |

Feedback grammar:

```text
idle -> pressed -> running -> success/error -> idle
```

Rules:

- use short toast/snackbar for transient page-level result;
- use button color/progress for direct button work;
- never render `Completed: completed`;
- do not add permanent text for a transient result;
- errors remain until understood or dismissed;
- haptics never replace visual state;
- simultaneous touch must not trigger unrelated navigation.

Execution:

1. Audit current physical debug build and record items.
2. Cluster by root cause:
   - state ownership;
   - gesture routing;
   - feedback rendering;
   - layout/hitbox;
   - lifecycle restoration;
   - accessibility semantics.
3. Fix one owner/surface per commit.
4. Add a regression test before closing each item.
5. Re-run physical reproduction.
6. Freeze a clean checkpoint.

Required existing tests:

- `HomeScreenBehaviorTest`;
- `KeyboardScreenPolicyTest`;
- `KeyboardViewModelStateTest`;
- `TrackpadGestureEngineTest`;
- `MouseScreenGestureInstrumentedTest`.

Ownership:

- only one agent may edit `MainActivity.kt`;
- only one agent may edit `MouseScreen.kt`;
- do not run A2 Trackpad edits concurrently with lockscreen phases;
- do not mix Reactive UI work.

Stop if:

- a complaint lacks reproducible steps;
- a fix changes HID report semantics without a focused test;
- a layout fix requires unrelated architecture work;
- the protected release app would need removal.

Exit:

- ledger contains no unlabeled “misc” bucket;
- closed items have automated and physical evidence;
- open items have explicit priority and owner;
- worktree clean.

---

## 10. Phase L0 — lockscreen threat model and pure policy

Goal:

- define the exception before any Activity can appear over keyguard.

New files:

- `docs/security/LOCKSCREEN_TRACKPAD_THREAT_MODEL.md`;
- `app/src/main/java/io/codecks/core/trackpad/LockscreenTrackpadPolicy.kt`;
- `app/src/test/java/io/codecks/core/trackpad/LockscreenTrackpadPolicyTest.kt`.

Required model:

```kotlin
enum class LockscreenCapability {
    PointerMove,
    PointerScroll,
    MouseButton,
    HidShortcut,
    Keyboard,
    DeckAction,
    ReactiveAction,
    Clipboard,
    NotificationContent,
    Settings,
    Pairing,
    Reconnect,
    Disconnect,
}

enum class TrackpadEntryOrigin {
    ExactPublicUri,
    InternalWidget,
    InternalNotification,
    InternalApp,
    Unknown,
}

data class LockscreenControlState(
    val keyguardShowing: Boolean,
    val deviceLocked: Boolean,
    val userUnlockedSinceBoot: Boolean,
    val hidConnected: Boolean,
    val selectedHostPresent: Boolean,
    val bluetoothPermissionGranted: Boolean,
    val featureEnabled: Boolean,
    val entryOrigin: TrackpadEntryOrigin,
)

sealed interface LockscreenDecision {
    data object AllowRestrictedPointer : LockscreenDecision
    data object ForwardToUnlockedTrackpad : LockscreenDecision
    data object RequireUnlock : LockscreenDecision
    data object IgnoreAutomaticEntry : LockscreenDecision
}
```

Policy:

- unlocked device: forward to normal Trackpad through a signed internal intent;
- locked + post-boot + opt-in + connected + selected host + permission:
  restricted pointer is allowed for the exact public URI or Codecks-owned entry;
- locked + disconnected + public URI: ignore; never warm/register/connect HID;
- locked + disconnected + explicit Codecks widget/notification tap: show only
  **Unlock to connect**;
- unknown origin: require unlock;
- extras cannot override any state;
- every capability other than the first three is denied while keyguard shows.

Threat model must cover:

- a stranger holding the phone can move/click the paired Mac;
- a malicious app launching the public URI;
- mutable/replayed PendingIntent;
- public URI with forged extras;
- HID disconnect during held button;
- screen off/on race;
- reboot before first unlock;
- lock transition during Activity launch;
- Recents/back-stack data exposure;
- notification content exposure;
- auto-launch denial of service.

Tests:

- full truth table across lock, boot, feature, permission, host, connection, origin;
- only pointer/scroll/mouse button allowed;
- `HidCommand` always denied;
- unknown state fails closed;
- external extras do not affect decision;
- disconnect decision requires `releaseButtons`.

Forbidden:

- code in `MainActivity`;
- Activity flags;
- UI;
- widget;
- service behavior changes.

Exit:

- threat model reviewed;
- policy exhaustive and pure;
- E1 evidence recorded.

---

## 11. Phase L1 — entry router and restricted Trackpad

Goal:

- make keyguard access possible without exposing the full app.

New files:

- `app/src/main/java/io/codecks/ui/mouse/lockscreen/TrackpadEntryActivity.kt`;
- `app/src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadActivity.kt`;
- `app/src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadViewModel.kt`;
- `app/src/main/java/io/codecks/ui/mouse/lockscreen/LockscreenTrackpadScreen.kt`;
- focused unit/Compose/instrumentation tests under matching source sets.

Scoped edits:

- `app/src/main/AndroidManifest.xml`;
- `IntentDestinationPolicy.kt`;
- `MouseScreen.kt` or a new extracted pointer-surface file;
- DI only if required.

Architecture:

```text
exact codecks://trackpad
        |
        v
exported TrackpadEntryActivity
        |
        +-- unlocked ----------------> signed MainActivity Trackpad route
        |
        +-- locked + connected ------> non-exported LockscreenTrackpadActivity
        |
        +-- locked + disconnected ---> no-op or Unlock-to-connect by origin
```

`TrackpadEntryActivity`:

- exported only because it owns the exact URI;
- no general destination extra;
- no trusted auth/connection extras;
- no user content;
- short-lived dispatcher;
- does not warm HID;
- does not turn the screen on;
- forwards with explicit component and existing internal intent signature.

`LockscreenTrackpadActivity`:

- `android:exported="false"`;
- `android:excludeFromRecents="true"`;
- empty/separate task affinity;
- `setShowWhenLocked(true)`;
- `setTurnScreenOn(false)` for initial release;
- `FLAG_SECURE`;
- no app drawer or full navigation;
- no device name, host address, connection details, notifications, history, or
  command status;
- an explicit **Unlock for full Codecks** button may call
  `requestDismissKeyguard`;
- after successful system dismissal, finish restricted Activity, then open normal
  Trackpad;
- cancel/failure stays restricted or closes.

Restricted ViewModel:

```kotlin
interface LockscreenPointerPort {
    fun move(dx: Int, dy: Int)
    fun scroll(vertical: Int, horizontal: Int)
    fun click(buttonMask: Int)
    fun press(buttonMask: Int)
    fun releaseButtons()
}
```

It must not expose:

- `start`;
- `refreshHosts`;
- `connect`;
- `disconnect`;
- `send(HidCommand)`;
- `typeText`;
- action runner;
- connection repository;
- Mac-state repository.

Pointer surface:

- do not reuse all of `MouseScreen`;
- extract or wrap only the raw pointer mechanics;
- hard-wire command gesture callbacks to absent;
- notifications empty;
- trays/More/settings/custom/Dynamic/Reactive absent;
- settings mutations absent;
- pointer, scroll, mouse-button callbacks only;
- policy checked at launch and before dispatch;
- connection observed continuously;
- disconnect or policy failure calls `releaseButtons()` and finishes.

Tests:

- exact public URI resolves only to entry router;
- normal launcher still resolves only to `MainActivity`;
- locked route never constructs `MainActivity`;
- unlocked route uses signed internal destination;
- restricted semantics tree contains no forbidden surface;
- multi-finger configured command never fires;
- disconnect releases held input and closes;
- Back/Recents cannot reveal full app;
- screenshot/recents content protected;
- no `hidRepository.start()` or `connect()` reachable from restricted ViewModel.

Performance:

- pointer callbacks contain no keyguard service call, disk, network, SSH, helper,
  or coroutine wait;
- policy snapshot changes are observed outside the hot movement callback;
- actual movement path remains the current HID path.

Stop if:

- reuse requires a loose `isLocked` Boolean across full `MouseScreen`;
- full app navigation becomes reachable;
- keyguard state is taken from an intent;
- command gestures cannot be structurally removed;
- HID needs reconnect to make the locked flow pass.

Exit:

- E1 unit/Compose tests;
- E2 instrumentation with fake connection;
- no physical completion claim yet.

---

## 12. Phase L2 — widget, notification, and Tasker entry

Goal:

- provide convenient entry without expanding lockscreen authority.

New files:

- `app/src/main/java/io/codecks/widget/TrackpadWidgetProvider.kt`;
- `app/src/main/res/layout/trackpad_widget.xml`;
- `app/src/main/res/xml/trackpad_widget_info.xml`;
- widget tests.

Scoped edits:

- `HidSessionService.kt`;
- `AndroidManifest.xml`;
- `docs/integrations/TASKER_TRACKPAD_AUTOLAUNCH.md`;
- permission/privacy and release ledgers.

Home-screen widget:

- one action: **Trackpad**;
- explicit immutable `PendingIntent`;
- Codecks-owned entry origin signed by `InternalIntentAuth`;
- no host/device name;
- no command buttons;
- no background service start;
- no claim that it appears on every lockscreen.

HID notification:

- content intent/action opens `TrackpadEntryActivity`;
- generic public title/text;
- no Mac name or private state on lockscreen;
- current unauthenticated **Stop** action is removed or routed through system unlock;
- no keyboard, media, Deck, clipboard, or settings actions.

Tasker:

- retain `codecks://trackpad`;
- primary trigger: BT Near;
- additional confidence: power + face-up;
- optional home Wi-Fi;
- no exit task that kills Codecks;
- no SSH keys/passwords/commands in Tasker;
- automatic URI entry never turns screen on;
- locked + already connected opens only restricted pointer;
- locked + disconnected does not reconnect or prompt.

Samsung:

- test whether the exact phone/One UI version allows a Codecks lockscreen shortcut;
- if absent, use notification entry;
- document verified behavior only.

Tests:

- `PendingIntent` explicit and immutable;
- widget cannot encode arbitrary destination;
- public URI ignores auth/connection extras;
- notification contains no private text;
- Stop cannot disconnect while keyguard is showing;
- automatic Tasker entry cannot wake screen;
- home widget remains useful unlocked.

Exit:

- entry surfaces work in emulator/fakes;
- Tasker docs match implementation;
- physical proof remains pending.

---

## 13. Phase L3 — physical proof and protected release

Goal:

- prove the feature on the exact Samsung phone and real Mac, then release without
  risking the installed production app.

Preflight:

- explicit user approval for physical phone work;
- resolve exact device serial;
- confirm `app.codecks` installed certificate;
- build candidate signed by matching certificate;
- confirm candidate version is not a downgrade;
- confirm minification/resource shrinking are disabled;
- hash candidate before testing.

Physical matrix:

| State | Expected |
|---|---|
| HID already connected, phone locked | restricted Trackpad opens |
| HID disconnected, phone locked | no reconnect; unlock required |
| phone rebooted, never unlocked | unavailable |
| Bluetooth toggled off while visible | release buttons and close |
| Mac sleeps | safe failure; no command surface |
| Mac wakes and HID remains connected | pointer resumes |
| public Tasker URI, screen off | no unexpected screen wake |
| notification tap, locked | restricted Trackpad |
| home widget, unlocked | normal Trackpad |
| unlock button | system credential, then full Trackpad |
| Back/Home/Recents | no protected app content |

Input matrix:

- movement;
- vertical/horizontal scroll;
- single tap;
- left/right/middle click;
- press/drag/release;
- two simultaneous touches used only for pointer gestures;
- every configured HID shortcut gesture remains blocked;
- keyboard, Deck, Clipboard, Rules, AI, notification previews, settings, pairing,
  disconnect remain unreachable.

Reliability:

- screen off/on;
- AOD;
- PIN, fingerprint, face unlock;
- rotation;
- incoming notification/call;
- app background/foreground;
- at least 30-minute connected idle;
- Mac sleep/wake;
- Bluetooth drop.

Release gate:

- unit/lint/check;
- emulator tests;
- exact-artifact physical HID;
- exact-artifact physical SSH;
- certificate equality;
- SHA-256;
- in-place `adb install -r --no-streaming`;
- verify version, data preservation, HID, SSH after install;
- GitHub release only after all release checks and explicit approval.

Never uninstall, clear data, downgrade, or use a differently signed APK.

Exit:

- E5 exact signed unshrunk artifact;
- saved evidence with hash;
- public release/version docs updated;
- clean checkpoint becomes `PLATFORM_BASELINE_COMMIT`.

---

## 14. Reactive platform execution R0–R10

After L3, execute the original Reactive plan Phases 0–10 exactly one phase at a
time, using the new baseline.

Required order:

1. R0: checkpoint, docs, ADRs;
2. R1: KMP and project scaffold;
3. R2: canonical models/codecs/fixtures;
4. R3: protocol/security state machine;
5. R4: minimal native Mac helper;
6. R5: Android helper client;
7. R6: unified `MacStateRepository` and bounded SSH fallback;
8. R7: pure Reactive engine/providers;
9. R8: typed executor/receipts/undo;
10. R9: Reactive Trackpad ViewModel/UI;
11. R10: physical behavior matrix.

Additional lockscreen constraint:

- Reactive controls never appear in `LockscreenTrackpadActivity`;
- `ReactiveTrackpadViewModel` is not constructed there;
- helper/SSH state is not needed for locked pointer;
- pointer remains available if helper/SSH is absent;
- all Reactive execution requires unlock.

Do not run R9 concurrently with Trackpad/HID reliability or lockscreen work.

---

## 15. Phase D0 — minimal DeskDock integration

Goal:

- make desk entry useful now without pretending exact location is solved.

Deliverables:

- verified Tasker profile instructions;
- NFC alternative;
- explicit user-facing status:
  - **Likely at desk**;
  - **Opened by NFC**;
  - never **Exact position detected**.

Tasker policy:

```text
BT Near paired Mac
AND charging
AND face-up
[AND optional home Wi-Fi]
  -> launch codecks://trackpad
```

NFC policy:

```text
NFC tag under stand
  -> launch codecks://trackpad
```

Rules:

- NFC is deterministic placement proof only to the extent of the tag scan;
- BT Near is room-scale;
- BT Connected alone remains circular when Codecks must register HID first;
- no Tasker SSH;
- no auto execution;
- no screen wake by default;
- locked entry obeys L0 policy.

Exit:

- docs tested on exact Tasker/phone version;
- no native confidence engine yet.

---

## 16. Phase D1 — native DeskDock confidence engine

Dependency:

- R6 unified Mac state;
- R8 executor policy;
- R10 physical Reactive proof.

Goal:

- estimate desk placement with transparent scoring and hysteresis;
- open or suggest Trackpad, never authorize commands.

New shared contracts:

```kotlin
data class DeskSignal(
    val kind: DeskSignalKind,
    val value: Double?,
    val observedAtMillis: Long,
    val source: DeskSignalSource,
    val available: Boolean,
)

data class DeskConfidence(
    val score: Int,
    val band: DeskConfidenceBand,
    val reasons: List<DeskReasonCode>,
    val observedAtMillis: Long,
)

enum class DeskConfidenceBand {
    Away,
    Possible,
    Likely,
    ConfirmedByNfc,
}
```

Initial signals:

- paired Mac BT Near;
- HID connected;
- charging source;
- face-up;
- stationary duration;
- optional home Wi-Fi;
- helper reports target Mac awake;
- helper reports expected external-display topology.

Optional experimental signals:

- ambient light fingerprint;
- magnetic fingerprint.

Rules:

- optional sensors require explicit calibration/opt-in;
- raw sensor history is local and bounded;
- score reasons are visible;
- unavailable signal contributes zero, never a hidden penalty;
- enter threshold and exit threshold differ;
- minimum dwell time prevents flapping;
- manual **Not at desk** suppresses until signals reset;
- NFC produces a separate deterministic band;
- confidence never unlocks phone or executes actions;
- if keyguard shows, only restricted pointer may open;
- if HID is disconnected while locked, do nothing.

Example policy, to calibrate rather than hardcode:

```text
enter Likely >= 70 for 10 seconds
exit Likely  < 45 for 20 seconds
cooldown after dismissal = 30 minutes
```

Tests:

- every signal combination;
- stale signal expiry;
- missing sensor;
- hysteresis;
- cooldown;
- manual suppression;
- Mac switch isolation;
- NFC override;
- keyguard cannot expand authority;
- no execution side effect in score calculation.

Physical proof:

- stand placement/removal;
- same room but away from stand;
- charging elsewhere;
- lighting changes;
- magnets/case changes;
- Mac asleep/awake;
- false-positive/false-negative log without private content.

Exit:

- published accuracy is measured, not guessed;
- product wording says probabilistic.

---

## 17. Phase I1 — Apple Shortcuts and Siri-facing workflows

Dependency:

- R4 helper;
- R6 state;
- R8 typed executor.

Goal:

- list and run explicitly selected Apple Shortcuts;
- allow Siri-authored Shortcut workflows to call safe Codecks helper actions later;
- do not automate Siri UI.

Typed operations:

```text
shortcuts.list
shortcuts.run(shortcutId, inputEnvelope?)
shortcuts.describe(shortcutId)
```

Requirements:

- imported display name is not execution identity;
- helper creates stable local ID mapped to exact shortcut;
- list refresh is explicit or bounded;
- run deadline;
- input size/type allowlist;
- stdout/stderr bounded;
- output classified as text/file/none/error;
- no output logged by default;
- no shortcut is enabled as Deck/Reactive action until reviewed;
- running a shortcut is never allowed on lockscreen;
- dangerous effects remain the shortcut author’s responsibility and Codecks marks
  unknown-effect shortcuts `RequiresConfirmation`.

SSH fallback:

- fixed wrapper only;
- shortcut identity passed as structured input, not shell concatenation;
- no arbitrary `shortcuts` subcommand from phone.

Tests:

- names with quotes/newlines/unicode;
- missing/deleted shortcut;
- timeout;
- oversized output;
- nonzero exit;
- duplicate display names;
- refresh preserves user mapping;
- locked execution denied.

Exit:

- physical Mac run proof;
- no Siri UI scripting claim.

---

## 18. Phase I2 — app-aware Deck and keyboard shortcut importer

Dependency:

- R6 front-app state;
- R7 providers;
- R8 policy.

Order:

1. curated known shortcut catalog;
2. user-reviewed import;
3. dynamic menu shortcut discovery only in I5.

Model:

```kotlin
data class ImportedShortcut(
    val id: ImportedShortcutId,
    val appIdentity: AppIdentity,
    val title: String,
    val chord: KeyChord,
    val provenance: ShortcutProvenance,
    val discoveredAtMillis: Long,
    val reviewState: ReviewState,
)
```

Rules:

- bundle/app identity, not display name only;
- canonical typed key chord;
- conflict detection;
- per-app visibility;
- imported actions start disabled/unreviewed;
- no global shortcut capture;
- no hidden menu action execution;
- no lockscreen visibility;
- current front app is state, not authorization.

MVP:

- Safari/Chrome;
- Finder;
- Terminal/iTerm;
- Music/Spotify where chords are stable.

Tests:

- app version mismatch;
- duplicate chord;
- layout/localization;
- removed app;
- stale import;
- user override;
- unsupported chord hidden.

Exit:

- app-aware controls work without Accessibility permission.

---

## 19. Phase I3 — Pocket Spotlight and SFTP

Dependency:

- R4 helper;
- R6 Mac identity/state;
- R8 execution;
- path/security threat review.

Split into two commits/subphases.

### I3A search

- helper owns typed file-search request;
- bounded query length and result count;
- metadata only by default;
- path, name, kind, size bucket, modified time;
- no file content;
- explicit allowed roots;
- private/excluded roots;
- cancellation/deadline;
- stale result invalidated on Mac switch.

### I3B transfer

- dedicated SFTP channel or typed helper transfer;
- no `scp` command strings;
- canonical path validation;
- reject `..`;
- reject symlink escape;
- max file size;
- free-space preflight;
- temporary filename + atomic finalization where supported;
- SHA-256 verification;
- cancel/cleanup;
- explicit source/destination confirmation;
- no transfer on lockscreen;
- no automatic opening of received executables.

Tests:

- unicode names;
- duplicate names;
- path traversal;
- symlink escape;
- interrupted transfer;
- checksum mismatch;
- insufficient space;
- Mac switch;
- locked request.

Exit:

- exact physical transfer proof;
- privacy ledger updated.

---

## 20. Phase I4 — ambient display brightness adapters

Dependency:

- R4 helper;
- R6 display state;
- R8 typed execution;
- actual supported external display/tool detected.

Current planning fact:

- the earlier Mac snapshot showed only the built-in display;
- neither DDPM nor BetterDisplay was detected then;
- this is not proof of future hardware state.

Capability adapters:

```text
DisplayBrightnessProvider
  -> NativeMacDisplayAdapter
  -> BetterDisplayAdapter
  -> DellDdpmAdapter
  -> Unsupported
```

Requirements:

- capability probe before UI;
- identify display by stable helper-side identity;
- show only supported controls;
- bounded brightness range;
- rate limit;
- manual override cooldown;
- opt-in ambient mode;
- local calibration curve;
- no raw sensor persistence beyond bounded calibration;
- no lockscreen brightness controls;
- no shell command constructed from display labels.

Do not implement until official/current CLI or API signatures are re-read on the
actual Mac. If only GUI automation exists, stop and re-scope.

Tests:

- tool absent;
- display absent/replaced;
- duplicate labels;
- permission failure;
- timeout;
- manual override;
- ambient jitter;
- safe clamping.

Exit:

- exact display/tool physical proof;
- unsupported systems hide feature.

---

## 21. Phase I5 — Accessibility menu and semantic UI discovery

Dependency:

- I2 proves value without Accessibility;
- helper permission UX exists;
- R8 policy;
- physical app matrix.

Order:

1. frontmost app menu titles and shortcuts;
2. menu enabled/disabled state;
3. narrowly scoped semantic element discovery;
4. no continuous whole-tree scraping.

Requirements:

- explicit Accessibility permission;
- permission denial is normal;
- bounded traversal depth/node count/time;
- app/version provenance;
- titles may be private and are not logged;
- no password/secure-text attributes;
- no selected-text polling;
- imported controls require review;
- stale UI element IDs cannot execute;
- helper re-resolves target at execution;
- never available on lockscreen.

Stop if:

- an app lacks stable Accessibility attributes;
- semantic identity cannot be revalidated;
- implementation requires screenshot/OCR for the first release;
- traversal causes material foreground-app latency.

Exit:

- physical matrix by supported app/version;
- unsupported discovery degrades to I2 catalog.

---

## 22. Final phase F — integration, release, and README truth

Goal:

- ship only proven capabilities and keep public claims synchronized.

Android:

- full unit/lint/check;
- managed emulator where available;
- signed unshrunk APK;
- certificate match;
- exact-artifact physical HID and SSH;
- lockscreen physical matrix;
- protected in-place install;
- checksum/provenance.

Mac:

- helper tests;
- signing/notarization when helper ships;
- permission denial/revocation;
- clean install and rollback;
- typed integration matrix.

Documentation:

- README version current;
- statistics snapshot date current or explicitly historical;
- feature support matrix;
- tradeoffs visible;
- no unverified Samsung/widget/DeskDock accuracy claim;
- no brightness claim without detected adapter;
- no Siri UI claim;
- release notes distinguish E1/E3/E4/E5 evidence.

No release from a rebuilt artifact after physical testing. Tested hash must equal
published hash.

---

## 23. Parallelization and ownership

May run in parallel after A0:

- A1 README evidence;
- A2 audit-only evidence capture.

Must not edit concurrently:

- A2 Trackpad fixes and L1;
- L1/L2 and R9;
- R5 and R6;
- R8 and R9;
- D1 and R6 state contracts;
- integration registry merges;
- any release phase and workflow edits.

Single owner:

- `MainActivity.kt`;
- `MouseScreen.kt`;
- `HidRepository.kt`;
- `HidSessionService.kt`;
- `AndroidManifest.xml`;
- `ConnectionRepository.kt`;
- `AppModule.kt`;
- `app/build.gradle.kts`;
- action registries;
- protocol schemas;
- release workflow.

Independent integration lanes after R8:

- I1 Shortcuts;
- I2 curated shortcut catalog;
- I3 search/transfer;
- I4 brightness.

They may gather docs/build isolated adapters in parallel. Registry, DI, helper
protocol, and UI integration remain single-owner serial merges.

---

## 24. Phase agent contract

Give an implementation agent one phase only:

```markdown
Repository: this repository checkout
Baseline: <exact commit>
Branch/worktree: <exact isolated branch/path>
Mission: implement Phase <ID> only.

Read first:
- exact repository files
- exact official API pages

Allowed files:
- exact paths

Forbidden files:
- exact paths

Required contracts:
1. behavior
2. security/lockscreen boundary
3. compatibility/migration
4. release/no-shrink boundary

Copy patterns from:
- exact file and symbol

Tests to add:
- exact cases

Verification:
- inspect Git first
- discover Gradle tasks
- run exact existing tasks
- label E0/E1/E2/E3/E4/E5

Stop and report if:
- source/API differs
- baseline dirty
- extra dependency or permission is needed
- protected production app would be modified unsafely
- lockscreen authority would expand
- command policy would need weakening
- signing/physical hardware is required without approval
- another phase's file must change

Completion report:
- sources read
- changed files
- tests
- commands and PASS/FAIL
- tests not run
- evidence level
- blockers/risks
- progress ledger change
- commit hash
```

Reject reports that say “implemented” without exact tests and evidence.

---

## 25. Global anti-pattern grep/audit

Before completion, audit for:

- `showWhenLocked` outside restricted Activity;
- `turnScreenOn=true` without a separately approved phase;
- public intent extras used as auth/connection state;
- `requestDismissKeyguard` outside explicit unlock handler;
- `HidCommand` reachable from restricted ViewModel/screen;
- lockscreen references to `ActionRunner`, `ConnectionRepository`,
  `MacStateRepository`, Clipboard, AI, or notification content;
- mutable implicit `PendingIntent`;
- Tasker docs containing SSH secrets/commands;
- raw shortcut names concatenated into shell;
- SFTP path traversal;
- sensor score authorizing execution;
- `isMinifyEnabled = true`;
- `isShrinkResources = true`;
- stale README version/statistics;
- unsupported controls rendered disabled instead of hidden.

---

## 26. Final definition of done

README:

- [ ] Every number has dated source and reproducible calculation.
- [ ] Phone placement limitation is visible.
- [ ] Manufacturer snapshot is not called market average.
- [ ] Desk cost uses square inches.
- [ ] Opportunity cost is not called recovered cash.
- [ ] Tradeoffs are visible near the claim.

UX:

- [ ] Papercut ledger exists.
- [ ] Closed items have reproduction, test, and physical evidence.
- [ ] Feedback grammar is consistent.
- [ ] Multi-touch and accessibility matrix completed.

Lockscreen:

- [ ] Only pointer, scroll, mouse buttons allowed.
- [ ] User opt-in required.
- [ ] Already-connected HID required.
- [ ] No registration/reconnect/pairing while locked.
- [ ] No command gestures.
- [ ] No full app content or navigation.
- [ ] Disconnect releases held input and closes.
- [ ] Pre-first-unlock fails closed.
- [ ] Exact Samsung + real Mac physical matrix passes.
- [ ] Exact signed unshrunk release artifact passes SSH and HID.

DeskDock:

- [ ] Tasker uses BT Near plus confidence signals.
- [ ] NFC documented as deterministic alternative.
- [ ] Native score depends on unified state.
- [ ] Hysteresis/cooldown/manual suppression tested.
- [ ] Confidence never authorizes or unlocks.
- [ ] Accuracy wording matches measured evidence.

Reactive/integrations:

- [ ] Original R0–R10 gates pass on new baseline.
- [ ] Shortcuts are typed and bounded.
- [ ] App shortcuts are reviewed and provenance-bound.
- [ ] File search/transfer is bounded and path-safe.
- [ ] Brightness appears only with a proven adapter.
- [ ] Accessibility is permissioned, bounded, and last.
- [ ] Every non-pointer action requires unlock.

Release:

- [ ] Production app was never uninstalled or cleared.
- [ ] Candidate certificate matches installed release.
- [ ] No downgrade.
- [ ] Minification/resource shrinking remain disabled.
- [ ] Tested artifact hash equals published artifact hash.
- [ ] Evidence level is stated honestly.

The project is not complete because the README has impressive arithmetic, an
emulator shows over keyguard, Tasker launches an Activity, or mocks pass. It is
complete only when the claims are reproducible and the exact signed unshrunk
release passes the physical security, HID, SSH, and integration gates applicable
to the shipped capabilities.
