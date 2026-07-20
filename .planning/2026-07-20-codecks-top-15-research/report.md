# Codecks Top 15: Research and Delivery Plan

Generated: 2026-07-20

## Executive decision

Codecks should not widen into a generic remote-desktop app. Its strongest lane is a local-first Android control surface for Mac: excellent Trackpad/HID, excellent text and clipboard handoff, trustworthy actions, and a small amount of useful automation/AI.

The current app already contains more of this product than the earlier brainstorm implied. Pages, manual clipboard actions, snippets/recent sends, live running states, retryable Activity history, context ranking, widgets/tiles, Air Mouse, and structured AI drafting all exist in some form. The roadmap should graduate and connect those pieces rather than build parallel versions.

Ranking method: weighted judgment across user impact (30%), product fit (25%), external evidence (15%), dependency unlock (15%), and feasibility/risk (15%). Scores are prioritization aids, not measured product analytics.

## Ranked top 15

| Rank | Workstream | Score | Current state | MVP outcome |
|---:|---|---:|---|---|
| 1 | Adaptive Trackpad Gesture Engine 2.0 | 95 | Custom engine improved in PR #12; tap behavior still feels finicky | Platform-consistent tap/double-tap handling, motion guard, calibration, local outcome/replay data, measurable error reduction |
| 2 | HID Pairing Doctor and physical reliability | 94 | Typed health/reconnect states exist; real Samsung-to-Mac proof is incomplete | Last failure reason, state timeline, one-tap repairs, sleep/Bluetooth-toggle recovery, supported-device matrix |
| 3 | Text-to-Mac Compose | 91 | HID/SSH transport, snippets, and recent sends exist | First-class compose screen using Gboard swipe/voice input, clear target/transport, sensitive mode, excellent long/Unicode sending |
| 4 | Manual Clipboard Bridge as the default UX | 89 | Send/pull and automatic modes exist | Prominent Send to Mac / Pull from Mac, auto-sync moved to Advanced, conflict preview, sensitive-content guard |
| 5 | Unified execution feedback and receipts | 88 | Running/success/failure and Activity retry exist, but Activity defaults off and feedback is fragmented | Every Deck/Automation/Text/Clipboard/Shortcut action gets live key state, safe receipt, retry/repair, duration, target |
| 6 | `v0.1.4` field-quality release wave | 87 | `main` is ahead of public `v0.1.3` | Version bump, signed artifact/checksum, Samsung+Mac smoke, accessibility/DeX matrix, rollback notes, public release notes |
| 7 | Accessibility and one-handed control | 85 | README names accessibility as a GA gate | TalkBack, 200% text, switch access, contrast, large targets, left/right-handed layouts, external keyboard escape paths |
| 8 | Unified command bus and architecture boundary | 83 | Actions, automations, AppFunctions, clipboard, and HID use overlapping contracts | One capability/target/input/result contract; transport adapters stay separate; incremental extraction from monolithic screens |
| 9 | Mac Shortcuts integration | 82 | No dedicated catalog integration found | List approved Shortcuts, preview required input, run through SSH, capture output/exit status, never expose arbitrary shell by default |
| 10 | Safe automation recipes and multi-actions | 80 | Automations and typed AI drafts exist | Curated meeting/coding/media/system recipes, per-step receipts, stop/undo where possible, test-before-enable, shareable safe JSON |
| 11 | Folders plus global action search | 77 | Pages already exist; no real folder model found | Folders and fast search across actions/automations/Shortcuts; no profiles and no automatic page switching |
| 12 | Android entry points and local integrations | 75 | Widget/Quick Settings exist but default off; text Share currently routes to AI | Pin selected action/widget/tile, Share text to Text/Clipboard, guarded URI/Tasker bridge, optional launcher shortcuts |
| 13 | Presentation Remote | 72 | Air Mouse is Labs; media/deck controls exist | Slide next/back, blackout, timer, notes shortcut, gyro pointer with clutch/recenter, volume-key click; Air Touch remains Labs-only |
| 14 | AI Gesture Coach, then optional tiny predictor | 70 | No useful touch-learning pipeline found | Explain misclassification, suggest thresholds, learn local timing; tiny single/double predictor only after offline replay beats rules |
| 15 | AI Builder 3: explain, repair, and suggest | 68 | Structured drafting and one repair are already mature | Turn failed receipts into reviewable repair drafts, explain actions in plain language, suggest actions from local usage, never auto-run |

## Why these won

Android’s official guidance already provides common gesture detection, touch slop, pointer history, and velocity concepts. The first Trackpad improvement should align with those primitives before adding ML ([gesture detection](https://developer.android.com/develop/ui/views/touch-and-input/gestures/detector), [movement and touch slop](https://developer.android.com/develop/ui/views/touch-and-input/gestures/movement)). Android’s HID callbacks also expose registration changes, host connection changes, and virtual-cable unplug, including unsolicited unregister when Bluetooth is disabled; Codecks should surface these states clearly ([Bluetooth HID callback](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice.Callback.html)).

GitHub evidence shows that Bluetooth HID demos are plentiful but durable products are rare. [Kontroller](https://github.com/raghavk92/Kontroller) is the closest established Android HID reference, while [HidPeripheral](https://github.com/LiangLuDev/HidPeripheral) explicitly warns about device-specific connection failures. [KDE Connect Android](https://github.com/KDE/kdeconnect-android) validates the coherent touchpad/keyboard/clipboard/media bundle; [Remote Touchpad](https://github.com/Unrud/remote-touchpad) exposes the real long tail of discovery, autostart, text encoding, acceleration, and app-specific scrolling.

Reddit evidence is unusually consistent. Users want explicit manual clipboard transfer without permanent automatic sync, but the Send action must remain obvious ([manual clipboard discussion](https://www.reddit.com/r/kde/comments/1rnepvz/how_do_i_disable_clipboard_sync_but_keep_send/)). Android-to-Mac users value native Gboard swipe input enough to call it a differentiator ([Remote Magic Trackpad update](https://www.reddit.com/r/MacOSApps/comments/1tmhv0z/apple_macos_magic_trackpad_and_keyboard_app_for/)). Remote-input failures include pointer jumps, broken text modes, and platform-specific scrolling ([pointer bug](https://www.reddit.com/r/kde/comments/177h02t/kdeconnect_remote_input_pointer_bug/), [Remote Touchpad scrolling issue](https://github.com/Unrud/remote-touchpad/issues/23)).

Competitors validate practical integrations. [Unified Remote](https://www.unifiedremote.com/features) emphasizes discovery, third-party keyboards, Wake-on-LAN, widgets, voice, NFC, and Tasker. [Stream Deck Mobile](https://www.elgato.com/us/en/s/stream-deck-mobile) emphasizes stateful keys, organization, multi-actions, plugins, and rotation-aware layouts. Codecks should choose only the pieces that preserve its small, local-first shape.

Apple provides a clean integration boundary: `shortcuts list` and `shortcuts run` support input, output, and exit status ([Apple Shortcuts command-line guide](https://support.apple.com/guide/shortcuts-mac/run-shortcuts-from-the-command-line-apd455c82f02/mac)). This is safer and more useful than inventing an unrestricted plugin runtime.

ML is credible but should be earned. The [PredicTaps paper](https://arxiv.org/abs/2408.02525) addresses the exact single-versus-double-tap latency problem; user-specific touch-model research also supports personalization. Codecks first needs local, content-free gesture features and explicit correction labels. A model should ship only if it beats the deterministic engine in offline replay and physical tests.

## Delivery waves

### Wave 0: Trust and release (`v0.1.4`, 1-2 weeks)

Scope: ranks 1, 2, and 6.

- Add a local gesture session recorder containing timings, movement, pressure/size when available, pointer count, chosen classification, and device-motion magnitude. Store no typed text, target app content, or remote output.
- Add a user-visible “That click was wrong” correction for the latest tap decision.
- Use Android platform time/slop conventions as the baseline; add a low-power accelerometer motion guard only while Trackpad is foregrounded.
- Extend the existing HID health model with last transition reason/time, repair action, and exportable redacted diagnostic summary.
- Physically test Samsung S23 Ultra -> Mac across first pair, reconnect, Bluetooth toggle, phone sleep/wake, Mac sleep/wake, Chrome back/forward, single/double click, scroll, and app pinning.
- Cut `v0.1.4` only after signed artifact and checksum verification.

Exit gate: materially fewer user-marked tap mistakes than PR #12 baseline, no additional pointer latency, and repeatable reconnect proof on the real phone/Mac pair.

### Wave 1: Text, clipboard, and access (`v0.2.0`, 2-3 weeks)

Scope: ranks 3, 4, and 7.

- Make Text a compose-first workflow; preserve the Android IME so Gboard swipe and voice input work naturally.
- Show destination, connection state, and selected transport before send. Keep short ASCII HID and long/Unicode `pbcopy` paths automatic but explainable.
- Make manual clipboard Send/Pull the primary surface. Keep automatic directions optional and off by default.
- Add sensitive mode that avoids recent-send storage and clears the editor after delivery.
- Complete TalkBack, font-scale, switch-access, handedness, and escape-path QA.

Exit gate: a new tester can send Unicode text and transfer clipboard both directions without setup help or accidental persistent history.

### Wave 2: Trustworthy action platform (`v0.3.0`, 3-4 weeks)

Scope: ranks 5, 8, 9, and 10.

- Define one command envelope: action ID, declared capabilities, target selector, typed input, safety level, execution state, receipt, and repair action.
- Route Deck, Automations, AppFunctions, Text/Clipboard helper actions, and Shortcuts through the shared execution/receipt boundary where appropriate. HID pointer packets remain direct and are not exposed as AI actions.
- Graduate Activity history after redaction/retention review.
- Add an allowlisted Mac Shortcuts catalog and safe recipe library.
- Add step-level progress, cancellation where supported, and explicit partial-success behavior.

Exit gate: every non-HID command has consistent preview, status, receipt, and repair semantics; generated actions cannot bypass review.

### Wave 3: Findability and integrations (`v0.4.0`, 2-3 weeks)

Scope: ranks 11, 12, and 13.

- Add folders and global action search on top of existing pages. Do not add profiles.
- Graduate only the Android entry points that pass privacy/reliability review: selected-action widget/tile, Share-to-Text/Clipboard, app shortcuts, guarded URI/Tasker invocation.
- Turn Air Mouse into a focused Presentation Remote with clutch/recenter and slide controls. Keep Air Touch in Labs.

Exit gate: common actions are reachable in two interactions or fewer, external triggers cannot bypass approval, and presentation control passes a 30-minute physical session without drift becoming unusable.

### Wave 4: Earned intelligence (`v0.5.0` experimental, 3-5 weeks)

Scope: ranks 14 and 15.

- Build a local gesture-coach screen from collected corrections and replay data.
- Start with adaptive statistics per user/device/orientation. Train/evaluate a tiny model only if the deterministic approach plateaus.
- Add receipt-to-repair AI drafting and local suggestion ranking. Suggestions may reorder a suggestion shelf, but must not change pages automatically or execute actions.
- Consider on-device Gemini Nano only behind runtime capability and disclosure checks; Codecks must work fully without it ([Android on-device AI overview](https://developer.android.com/ai/overview)).

Exit gate: intelligence features have a deterministic fallback, explicit privacy controls, measurable lift, and zero automatic command execution.

## Deliberately not selected

- Profiles: rejected by product decision. Pages/folders/search cover organization without mode confusion.
- Air Touch: remains Labs-only.
- Camera hand gestures: weak fit, higher privacy/battery cost.
- Account/backend/cloud sync: unnecessary for the current product thesis.
- Full remote desktop/screen streaming: crowded category and requires a different Mac security/transport architecture.
- Automatic clipboard sync as default: conflicts with user privacy/clutter preferences.
- Back-tap, shake, lift-to-wake, pocket detection, NFC, and arbitrary sensor macros: potentially useful later, but lower-frequency and more false-positive-prone than Trackpad motion confidence and presentation control.
- Plugin marketplace: too much security, compatibility, and support surface before the command bus is stable.

## Research boundary

This was a read-only product/repository research pass. No app code, build, tests, emulator, or physical device checks were performed. GitHub state and external sources were checked on 2026-07-20. The untracked multimodule `android/` foundation remains separate and untouched.

Reusable GitHub scan artifacts are stored under `<local-research-cache>/`, including `raghavk92-kontroller`, `puritysb-agentdeck`, and the broad `codecks-feature-landscape-2026-07-20` sweep.
