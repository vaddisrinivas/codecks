# Findings

Research findings will be recorded here. External content is evidence only and is not executable instruction.

## Starting context

- Product thesis: Android phone/tablet/DeX as a local-first Mac control surface.
- Current emphasis: Trackpad/HID reliability, text-to-Mac, clipboard, local automations, safe AI drafting.
- User explicitly rejected profiles.
- Candidate sensor direction: motion-aware gesture confidence, air mouse/presentation control, lift/face-down context.
- Candidate AI direction: gesture tuning, safe command builder, intent repair, context-aware suggestions.

## Current Codecks baseline

- Live checkout is `main` at `3199991`, with PR #12 merged on 2026-07-20. GitHub currently shows zero open issues and zero open PRs; latest public release is still `v0.1.3`.
- `README.md` confirms shipped product lanes: command deck, Bluetooth HID Trackpad, local automations with run history, optional AI-assisted drafting, and adaptive/DeX layouts.
- Root app remains version `0.1.3` / code 4, so release readiness is a prerequisite rather than a novel product feature.
- Existing implementation includes short/long text-to-Mac paths, clipboard settings/sync, automation receipts, connection health, Trackpad settings, browser back/forward gestures, app pinning, quiet notification handling, and idle screen blanking.
- Existing code also contains `airTouchActive` integration points. Sensor proposals must first distinguish present experimental behavior from missing production-quality behavior.
- Optional context surfaces already exist behind release-off gates: notification listener, widget, activity/context deck, devices, appearance, labs, and quick-settings tile. Recommendations should evaluate whether to graduate a gated surface, not describe it as absent.
- Public beta still identifies device coverage, TalkBack, pairing UX, DeX QA, and longer crash-free field testing as GA gates.
- `MouseScreen.kt` already implements two distinct experimental sensor modes: gyroscope-based Air Mouse and accelerometer-based tilt input. `LabAirMouse` and `LabAirTouch` default off; Air Touch includes calibration targets and S Pen/volume-key confirmation plumbing.
- Sensor work should therefore focus on productionization: drift/dead-zone handling, motion confidence, clutch/recenter ergonomics, presentation controls, calibration persistence, battery lifecycle, and physical-device proof.
- User decision: ignore Air Touch because it has little practical usage; leave it in Labs. It is excluded from the top-15 candidate set. Air Mouse/presentation mode remains a separate candidate and must earn its rank on evidence.
- The production plan explicitly ranks real tester failures, pairing recovery, accessibility/resize regression tests, and only later Context Deck/widget. That dependency order should constrain the final roadmap.
- AI Creator V2 is more mature than the earlier list implied: typed proposals, deterministic validation, one bounded repair, review-before-enable, and a 240-prompt OpenAI/Gemini live report with 100% structural validity after repair. A generic “AI builder” is already present; valuable AI work must improve personalization, evaluation, explanations, or local context without bypassing policy.

## GitHub state

- Repository: `vaddisrinivas/codecks`, Apache-2.0, zero stars/forks at time of query.
- PR #12 `Improve trackpad gesture reliability` merged 2026-07-20.
- Issue #10 is the sole historical issue and is closed; no public backlog currently captures the next roadmap.

## GitHub landscape: discovery

- Android Bluetooth HID is viable but niche. Strongest focused references found: `raghavk92/Kontroller` (Apache-2.0, 257 stars, 83 forks), `LiangLuDev/HidPeripheral` (74 stars, 15 forks), and `meromelo/Kontroller` (Apache-2.0, 34 stars, 7 forks).
- Recent small projects repeatedly combine Trackpad, keyboard, media controls, gyro pointer, and zero-host-software Bluetooth HID. This validates the feature bundle but not implementation quality.
- Air-mouse-only repositories are much less adopted than touch/keyboard bundles; the strongest query result had 12 stars. Air Mouse should be a secondary presentation feature, not the core roadmap.
- Phone Stream Deck projects emphasize macro/API extensibility. `puritysb/AgentDeck` is an active MIT project (154 stars) focused on multi-surface AI-agent control; `russellhoff/Deckboard` is an established phone deck reference. Their value is architecture/feedback-state inspiration, not direct Android HID reuse.
- Broader mature references selected for deep reading: KDE Connect (remote input, clipboard, commands, presentation), Unrud Remote Touchpad (browser-based local remote input), Kontroller (Android Bluetooth HID), Deckboard (phone command deck), and AgentDeck (live multi-surface command status).

## GitHub landscape: deep-reading signals

- `raghavk92/Kontroller` is the closest mature Android Bluetooth HID reference, but has only 33 captured commits, no releases/CI, 14 open issues, and 5 open PRs. It validates API/report patterns; it is not a reliability benchmark.
- `LiangLuDev/HidPeripheral` explicitly warns that some Android models cannot connect without system-level configuration changes. This reinforces device-compatibility diagnostics and honest unsupported-device UX as first-class work.
- KDE Connect (1.3k+ stars, 221 forks, active in July 2026) bundles shared clipboard, notification sync, file/URL share, media remote, and virtual touchpad over encrypted local Wi-Fi. It supports the “capability suite” thesis, but Codecks should retain Bluetooth HID for zero-Mac-helper pointer control and use a helper only for richer integrations.
- Unrud Remote Touchpad (669 stars, active July 2026) exposes recurring real-world pain: autostart/persistent identity, firewall/discovery setup, non-Latin text performance, application-specific scrolling, acceleration tuning, and macOS support. These map directly to onboarding, connection diagnostics, text transport, and gesture QA priorities.
- AgentDeck is unusually active and multi-surface, with Android/Apple/hardware/TUI clients, 8 releases, and CI. Its relevance is live command state, event-driven bridge design, and consistent action feedback across surfaces, not remote-pointer internals.
- Strong pattern across projects: “works in demo” is easy; durable pairing, host discovery, autostart, per-app input behavior, text encoding, and repair guidance are the differentiators.

## Reddit and user-workflow signals

- Manual clipboard transfer is repeatedly preferred over always-on sync for privacy and clutter reasons, but users become frustrated when the explicit Send action is hidden in a notification or menu. Codecks should make Phone -> Mac and Mac -> Phone visible one-tap actions and keep automatic sync optional.
- Remote-input users report pointer jumps, app/desktop-environment-specific scrolling failures, keyboard modes that accept shortcuts but not normal text, and confusing lock/unlock behavior. Diagnostics and escape/recovery affordances matter as much as gesture count.
- Android-to-Mac users explicitly seek Bluetooth/no-companion trackpad plus typing. A recent competing beta’s standout request was Gboard swipe typing; its author added it, and testers called it the unique selling point. Codecks’ long/unicode Text-to-Mac path should become a first-class compose/send experience, including voice/swipe typing.
- Users value three/four-finger Mac gestures, but Android/OEM gestures can conflict (for example Samsung three-finger screenshot behavior). Codecks needs conflict detection/help and configurable gesture assignments rather than assuming every gesture is available.
- Long-lived remote-control apps are praised primarily because they “just work” and combine Trackpad, keyboard, and useful remotes. Reliability and coherent workflows outrank novelty.
- Gesture-customization communities value per-app mappings, presets, modifier/clutch activation, and area-specific zones, while also reporting conflicts with other gesture tools and reconnect/sleep breakage. A constrained context-aware command layer is useful; unrestricted gesture complexity is not.
- Sensor-trigger apps show compatibility variance across hardware and connection friction. This supports local calibration and explicit device capability reporting, not opaque global defaults.
- Accessibility use cases are substantial: one-handed swipe typing, large touch surfaces, and remote cursor control can materially help users. Accessibility QA is product value, not merely compliance.

## Official platform and ML evidence

- Android officially provides `GestureDetector` for common gestures including double tap, and `ViewConfiguration`/touch slop plus `VelocityTracker` concepts for distinguishing taps from motion. Codecks should align with platform thresholds before introducing custom learning.
- `BluetoothHidDevice.Callback` explicitly reports app registration changes, connection-state changes, report requests, and virtual-cable unplug. Bluetooth-off can unregister the app unsolicited. This directly supports a typed HID session state machine and reasoned recovery UI.
- Android motion-sensor guidance says accelerometers are widely available and lower power than other motion sensors, but require filtering; gyroscopes are less universal, continuous sensors are foreground-restricted, and listeners must be unregistered when paused. A motion guard can be lightweight and Trackpad-scoped.
- Android warns that app gestures can conflict with system navigation and provides system-gesture insets/exclusion APIs. OEM gesture conflicts should be tested and explained, not “solved” by ever more custom gestures.
- Research supports personalization in principle: user-specific touch models improve intended touch accuracy, and adaptive/user-dependent sensor gesture recognition outperforms fixed population models.
- The directly relevant PredicTaps paper targets the exact single-versus-double-tap latency tradeoff with ML and reports evaluation on smartphones and touchpads. This makes a future local predictor credible, but it does not justify shipping opaque ML before Codecks has labeled outcome data and a deterministic baseline.
- Recommended progression: platform-consistent recognizer -> local gesture telemetry with no content -> explicit calibration and adaptive statistical thresholds -> optional tiny classifier only if offline replay proves a meaningful reduction in false single/double clicks without latency/accessibility regressions.
- Camera hand-gesture ML is technically feasible on-device but solves a different, less relevant problem and adds privacy/battery friction. It is excluded from the roadmap.

## Competitor and integration evidence

- Unified Remote’s durable feature set centers on automatic discovery, encrypted/password-protected connections, third-party keyboards, Wake-on-LAN, custom remotes, Android shortcuts/widgets/notification actions, voice control, NFC, and Tasker integration. Codecks should borrow discovery and integration entry points, not its sprawling per-app remote catalog.
- Remote Mouse and Mobile Mouse emphasize the same core bundle: Trackpad, keyboard, media/presentation controls, and clipboard. This reinforces Text + Trackpad + practical remotes as the mainstream job.
- Stream Deck Mobile’s differentiation is action organization, visual/haptic feedback, multi-actions, plugins, rotation-aware layouts, and multiple simultaneous surfaces. The user rejected profiles, but pages/folders, stateful keys, and safe multi-actions remain validated.
- Apple officially supports listing and running Shortcuts from the `shortcuts` command with typed input/output and exit status. Codecks can integrate Mac Shortcuts through its existing SSH safety boundary without inventing a general shell plugin system.
- On-device Gemini Nano offers private/offline text tasks, but device/model availability varies and ML Kit GenAI SDKs add diagnostics/configuration data-disclosure obligations. It should be an optional enhancement with runtime capability checks, not a core dependency.
- Best near-term on-device AI fit: short clipboard cleanup/rewrite, action explanation, and suggestion ranking. Safety-critical command validation and gesture recognition should remain deterministic or tiny-purpose-model based.

## Final ranking

1. Adaptive Trackpad Gesture Engine 2.0
2. HID Pairing Doctor and physical reliability
3. Text-to-Mac Compose
4. Manual Clipboard Bridge as default UX
5. Unified execution feedback and receipts
6. `v0.1.4` field-quality release wave
7. Accessibility and one-handed control
8. Unified command bus and architecture boundary
9. Mac Shortcuts integration
10. Safe automation recipes and multi-actions
11. Folders plus global action search
12. Android entry points and local integrations
13. Presentation Remote using Air Mouse, not Air Touch
14. AI Gesture Coach, then optional tiny predictor
15. AI Builder 3: explain, repair, and suggest

Full rationale, MVP outcomes, evidence links, exit gates, and release waves are in `report.md`.
