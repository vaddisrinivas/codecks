# Paper Cut Audit

Status: open

Purpose: turn vague UX frustration into reproducible, testable items.

## Ledger schema

```text
ID
surface
build_or_commit
device_or_window_mode
input_type
precondition
steps
expected
actual
frequency
severity
evidence
suspected_owner
test_to_add
status
commit
physical_proof
```

## Feedback grammar

`idle -> pressed -> running -> success/error -> idle`

Rules:

- Use toast or snackbar for transient page-level outcomes.
- Use button color, progress, or disabled state for direct button work.
- Never render permanent duplicate status text for transient success.
- Keep errors visible until understood or dismissed.
- Do not treat haptics as the only feedback channel.

## Open items

| ID | Surface | Summary | Status |
|---|---|---|---|
| UX-001 | Deck | Replace duplicated success copy like `Completed: completed` with transient feedback or a direct button-state change | Code-fixed, physical proof pending |
| UX-002 | Keyboard | Verify send, send + enter, enter, and command + enter keep consistent success and failure feedback | Code-fixed, physical proof pending |
| UX-003 | Keyboard | Verify clear-after-success and preserve-on-failure behavior | Code-fixed, physical proof pending |
| UX-004 | Trackpad | Audit tap, right click, drag, scroll, multi-touch, and tray overlap conflicts | Partially fixed, physical proof pending |
| UX-005 | Navigation | Audit Back behavior across Trackpad, sheets, drawer, and orientation changes | Partially fixed, physical proof pending |
| UX-006 | Accessibility | Audit labels, touch targets, focus order, and TalkBack wording on core controls | Partially fixed, physical proof pending |
| UX-007 | Keyboard | Verify whether on-screen modifier/enter controls can be pressed in quick succession or multi-touch without dropped intent | Open |

### UX-001

- surface: Deck
- build_or_commit: worktree after `c581890`
- device_or_window_mode: any phone layout
- input_type: tap
- precondition: connected Mac, runnable deck action
- steps:
  1. Open Deck
  2. Tap a successful action
- expected: direct button success state or one transient page-level outcome, then idle
- actual: generic successful deck runs now use tile-only transient success; snackbar spam was removed for routine action success
- frequency: frequent
- severity: medium
- evidence:
  - `app/src/main/java/io/codecks/ui/home/HomeStatusFeedbackPolicy.kt`
  - `app/src/test/java/io/codecks/ui/home/HomeStatusFeedbackPolicyTest.kt`
- suspected_owner: Home feedback pipeline
- test_to_add: done (`HomeStatusFeedbackPolicyTest`)
- status: code-fixed, physical proof pending
- commit: uncommitted
- physical_proof: pending

### UX-002

- surface: Keyboard
- build_or_commit: worktree after `c581890`
- device_or_window_mode: phone portrait/landscape
- input_type: soft keyboard send, button tap
- precondition: text entered in composer
- steps:
  1. Enter text
  2. Trigger IME send
  3. Trigger `Send + Enter`
  4. Trigger `Enter`
  5. Trigger `⌘ Enter`
- expected: send path is explicit and nearby; IME send uses the same send path; enter buttons remain available below composer
- actual: `OutlinedTextField` now uses `ImeAction.Send` and `KeyboardActions(onSend = { onTypeText() })`; explicit `Enter` and `⌘ Enter` buttons remain below send controls
- frequency: frequent
- severity: high
- evidence:
  - `app/src/main/java/io/codecks/ui/keyboard/KeyboardScreen.kt`
  - `app/src/test/java/io/codecks/ui/keyboard/KeyboardScreenPolicyTest.kt`
- suspected_owner: Keyboard composer
- test_to_add: done (`KeyboardScreenPolicyTest`)
- status: code-fixed, physical proof pending
- commit: uncommitted
- physical_proof: pending

### UX-003

- surface: Keyboard
- build_or_commit: worktree after `c581890`
- device_or_window_mode: any
- input_type: send success/failure
- precondition: text entered in composer
- steps:
  1. Send text successfully
  2. Repeat with a forced failure path
- expected: success clears draft and keeps recents; failure preserves draft for retry
- actual: success clears text and stores recent send; failure leaves existing draft in place because only status updates on failure
- frequency: frequent
- severity: high
- evidence:
  - `app/src/main/java/io/codecks/ui/keyboard/KeyboardViewModel.kt`
  - `app/src/test/java/io/codecks/ui/keyboard/KeyboardViewModelStateTest.kt`
- suspected_owner: Keyboard send state reducer
- test_to_add: done (`KeyboardViewModelStateTest`, `KeyboardViewModelTest`)
- status: code-fixed, physical proof pending
- commit: uncommitted
- physical_proof: pending

### UX-004

- surface: Trackpad
- build_or_commit: current worktree
- device_or_window_mode: Trackpad mode with controls open
- input_type: tap on tray dead space / overlap area
- precondition: controls panel open
- steps:
  1. Open Trackpad controls
  2. Tap or rest on blank area inside the tray sheet
- expected: tray dead space should absorb the gesture instead of moving the pointer or triggering underlying trackpad gestures
- actual: the expanded Trackpad sheet now installs a full-size overlay touch consumer behind its interactive children, so dead-space taps do not leak through to the underlying trackpad
- frequency: moderate
- severity: high
- evidence:
  - `app/src/main/java/io/codecks/ui/mouse/MouseScreen.kt`
  - `app/src/test/java/io/codecks/core/trackpad/TrackpadTrayOverlayPolicyTest.kt`
- suspected_owner: Trackpad chrome layering
- test_to_add: done (`TrackpadTrayOverlayPolicyTest`)
- status: partially fixed, broader multi-touch/drag/right-click physical audit still pending
- commit: uncommitted
- physical_proof: pending

### UX-005

- surface: Navigation
- build_or_commit: current worktree
- device_or_window_mode: AirMouse, AirTouch, Trackpad
- input_type: system Back
- precondition: controls panel open
- steps:
  1. Open controls
  2. Switch to AirMouse or AirTouch
  3. Press Back
- expected: Back closes the currently open controls before any broader navigation
- actual: code now enables the Back handler whenever controls are open, not only in Trackpad mode
- frequency: moderate
- severity: medium
- evidence:
  - `app/src/main/java/io/codecks/ui/mouse/MouseScreen.kt`
  - `app/src/test/java/io/codecks/core/trackpad/TrackpadChromeAccessibilityPolicyTest.kt`
- suspected_owner: Trackpad chrome state
- test_to_add: done (`TrackpadChromeAccessibilityPolicyTest`)
- status: partially fixed, broader physical audit still pending
- commit: uncommitted
- physical_proof: pending

### UX-006

- surface: Accessibility
- build_or_commit: current worktree
- device_or_window_mode: Trackpad tray
- input_type: TalkBack/focus navigation
- precondition: Trackpad controls open
- steps:
  1. Open Trackpad tray
  2. Focus tray icons with TalkBack or keyboard focus
- expected: tray items announce button role, label, and selected state
- actual: tray icons now expose merged semantics with explicit button role and selected/not-selected state description
- frequency: frequent
- severity: medium
- evidence:
  - `app/src/main/java/io/codecks/ui/mouse/MouseScreen.kt`
  - `app/src/test/java/io/codecks/core/trackpad/TrackpadChromeAccessibilityPolicyTest.kt`
- suspected_owner: Trackpad chrome semantics
- test_to_add: done (`TrackpadChromeAccessibilityPolicyTest`)
- status: partially fixed, wider focus-order/contrast/TalkBack audit still pending
- commit: uncommitted
- physical_proof: pending

### UX-007

- surface: Keyboard
- build_or_commit: current worktree
- device_or_window_mode: phone portrait/landscape
- input_type: multi-touch / near-simultaneous taps
- precondition: keyboard screen open
- steps:
  1. Hold or rapidly alternate modifier-style controls
  2. Tap `Enter`, `⌘ Enter`, and send-adjacent actions in quick succession
  3. Attempt two-finger interaction on the keyboard action row
- expected: interaction model is explicit; supported combinations work reliably; unsupported simultaneous touches fail predictably instead of half-firing or dropping state
- actual: not yet physically characterized; user specifically asked whether multiple buttons on the keyboard screen can be touched at once
- frequency: moderate
- severity: medium
- evidence:
  - user report in current thread
- suspected_owner: Keyboard action row interaction model
- test_to_add: pending
- status: open
- commit: uncommitted
- physical_proof: pending

## Fill-in template

### UX-XXX

- surface:
- build_or_commit:
- device_or_window_mode:
- input_type:
- precondition:
- steps:
- expected:
- actual:
- frequency:
- severity:
- evidence:
- suspected_owner:
- test_to_add:
- status:
- commit:
- physical_proof:
