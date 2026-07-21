# Codecks next wave: control, sync, and tactile deck

Updated: July 21, 2026

> Status note: this document is a historical next-wave plan. Version numbering has since moved; treat the wave labels as planning history, not current release commitments.

## Product direction

Codecks should become a reliable local control surface, not only a pretty deck:

```text
Phone / tablet / DeX
  -> tactile deck keys
  -> stable HID trackpad + keyboard
  -> send text to Mac
  -> controlled clipboard bridge
  -> local automations
  -> optional AI for creating controls
```

Keep the product local-only by default. No hosted database, account, analytics, or public clipboard relay.

## Current truth

Already exists:

- HID mouse/keyboard/consumer reports via `BluetoothHidDevice`.
- Text typing path: `HidRepository.typeText(text)` and `KeyboardScreen`.
- Clipboard sync domain engine: `ClipboardSyncEngine`.
- Clipboard UI/view model skeleton using `pbcopy`/`pbpaste` over the existing Mac connection.
- Deck, Trackpad, Automations, AI Creator V2, and release workflow.

Not production-ready yet:

- Bluetooth connection persistence.
- Clipboard sync as a first-class core flow.
- Unicode/large text transfer.
- Conflict UX.
- Background/foreground policy.
- Stream Deck/Codex Micro-level tactile polish.

## Why Bluetooth keeps needing reconnect

This is not purely user error. It is a product gap.

Android’s `BluetoothHidDevice` API is a HID Device profile proxy. The app must register a HID app before connecting, and official Android docs state that HID connections are only possible while the app is registered; the app can be automatically unregistered if it is not foreground.

In our code:

- [`HidController.java`](../../app/src/main/java/io/codecks/HidController.java) opens the HID profile and registers the app.
- [`HidRepository.kt`](../../app/src/main/java/io/codecks/HidRepository.kt) stores one selected host and attempts auto-reconnect.
- But reconnect is one-shot/best-effort. If `connect()` returns false, if Android closes the HID profile, if the app leaves foreground, or if macOS rejects the attempt, we do not have a durable reconnect loop with backoff and diagnostics.

So the fix is a real HID session manager.

## Inspiration to use

| Inspiration | Apply to Codecks |
|---|---|
| Codex Micro / Work Louder | Default deck should feel like a glassy physical device: status lighting, tactile keys, action slots, live feedback. |
| `dazer1234/codex-stream-deck` | Deterministic key rendering, status color mapping, local-only bridge, key-down/key-up semantics, agent/action/navigation pages. Do not copy undocumented Codex renderer hacks. |
| Elgato Stream Deck | Profiles, pages, folders, multi-action buttons, per-key icons, visual state feedback. |
| Touch Portal | Phone/tablet as macro surface, customizable pages, icons, labels, and large touch-friendly controls. |
| Bitfocus Companion | Variables, feedback, triggers, safe action templates, run history, and serious automation ergonomics. |

## Wave 1 — v0.1.3: “It stays connected”

Goal: Trackpad/keyboard feel like a real paired device.

### CONN-01: HID foreground session service

Create a foreground service that owns `HidController` while HID mode is active.

Acceptance:

- User can enable “Keep Mac controls connected.”
- Android shows a persistent notification while HID is maintained.
- Trackpad/keyboard/deck share one HID session.
- Closing the session releases all buttons/keys and unregisters cleanly.

### CONN-02: Reconnect state machine

Replace one-shot reconnect with explicit states:

```text
Idle -> OpeningProfile -> RegisteringApp -> Ready -> Connecting -> Connected
                         -> Failed(backoff) -> Ready
                         -> Suspended
```

Acceptance:

- Reconnect retries with bounded backoff.
- `Connect request failed` clears the in-flight attempt and schedules retry.
- HID profile closed reopens profile if the user enabled keep-connected.
- App never spams `connect()` while already connecting.
- Last disconnect reason is stored.

### CONN-03: Host health and repair UI

Add a single status model:

```text
Mac SSH: ready / blocked / offline
Bluetooth HID: ready / connecting / connected / dropped / permission missing
Overall: ready / needs setup / reconnecting
```

Acceptance:

- User sees why connection dropped.
- “Reconnect,” “Forget host,” “Open Bluetooth settings,” and “Re-pair” are visible.
- If macOS rejects HID, UI says so instead of generic “Disconnected.”

### CONN-04: Samsung/macOS reconnect QA

Acceptance:

- Pass on representative physical Android/Samsung hardware.
- Pass app foreground, screen off/on, home/back gesture, app switch, phone lock/unlock, Bluetooth toggle, Mac sleep/wake.
- Save reconnect logs without personal device identifiers.

### CONN-05: Keep-awake policy

Acceptance:

- Trackpad screen keeps display awake only when actively connected.
- Optional “keep session alive in background” uses foreground notification, not hidden background work.
- No battery-optimization bypass without explicit user action.

## Wave 2 — v0.1.4: “Type on phone, send to Mac”

Goal: a fast text console for the Mac.

### TEXT-01: Promote Keyboard/Text from hidden to core

Acceptance:

- “Text” or “Keyboard” becomes a first-class drawer item.
- It uses the same Codecks visual system as Deck/Trackpad.
- It works in phone, tablet, landscape, and DeX.

### TEXT-02: Two delivery modes

Use both paths:

1. HID typing for short ASCII text and key commands.
2. SSH pasteboard path for long text/unicode: `printf %s ... | pbcopy`, then paste on Mac.

Acceptance:

- UI explains which mode will be used.
- Unicode, newlines, emoji, and long text work through paste mode.
- HID mode remains instant for short snippets.

### TEXT-03: Send-to-frontmost-app action

Acceptance:

- User types on phone.
- Taps “Send.”
- Codecks copies to Mac clipboard and triggers paste into frontmost Mac app.
- Requires clear confirmation the first time.

### TEXT-04: Snippets and recent sends

Acceptance:

- Save reusable snippets.
- Recent sends are local-only and can be cleared.
- Sensitive mode sends once without saving history.

### TEXT-05: Deck key integration

Acceptance:

- Deck can include “Text to Mac,” “Paste saved snippet,” “Paste phone clipboard,” and “Open keyboard” keys.
- AI Creator V2 can create these only as typed safe actions, never arbitrary shell.

## Wave 3 — originally v0.1.5: Clipboard bridge

Goal: controlled clipboard sync, not spooky clipboard surveillance.

### CLIP-01: Make Clipboard a core opt-in surface

Acceptance:

- Clipboard appears in drawer only after feature is enabled.
- Default is Off.
- First-run explains what can be read/written.

### CLIP-02: Manual one-shot flows first

Acceptance:

- “Phone -> Mac” copies current phone clipboard to Mac.
- “Mac -> Phone” reads Mac clipboard and writes phone clipboard.
- Both require visible user action.
- Works with the existing Mac connection and no server.

### CLIP-03: Safe automatic modes

Modes:

- Off
- Manual only
- Phone to Mac
- Mac to Phone
- Two-way

Acceptance:

- Automatic Android clipboard read only happens while Codecks is foreground/focused, because Android 10+ limits background clipboard access.
- Android 12+ clipboard access toast behavior is respected.
- Interval defaults conservative.

### CLIP-04: Conflict UX

Acceptance:

- If phone and Mac both changed, Codecks shows conflict.
- User chooses phone wins, Mac wins, or keep both as snippets.
- No silent overwrite in two-way mode.

### CLIP-05: Sensitive-content guardrails

Acceptance:

- Detect likely passwords/tokens/private keys and require confirmation.
- Never send clipboard to AI.
- Never log clipboard contents.
- History stores hash, size bucket, timestamp, endpoint, not raw text unless user saved as snippet.

### CLIP-06: Clipboard test suite

Acceptance:

- Unit tests for conflict resolution, echo suppression, stale endpoints.
- Instrumented/manual QA for Android clipboard focus limitations.

## Wave 4 — originally v0.1.6: Codex Micro / Stream Deck polish

Goal: make Deck feel like a tactile device.

### DECK-01: Key status system

Statuses:

- off
- idle
- pressed
- running
- success
- needs input
- error

Acceptance:

- Every deck key can show state color.
- Automations update key status during dry run and run.
- Trackpad/Text/Clipboard keys show live availability.

### DECK-02: Profiles/pages/folders

Acceptance:

- Profiles: Mac, Browser, Meeting, Writing, Automation, AI.
- Pages within profiles.
- Folders as large key groups.
- Drawer shows active profile and page.

### DECK-03: Icon/keycap packs

Acceptance:

- Built-in Codecks Micro pack.
- Simple monochrome pack.
- Neon/glass pack.
- User-imported SVG/PNG icons.
- Missing icon fallback is attractive.

### DECK-04: Multi-action keys

Acceptance:

- Button can run ordered steps.
- Each step is typed and dry-runnable.
- Long press opens preview; tap runs.

### DECK-05: Visual designer

Acceptance:

- Edit key label/icon/color/glow/shape.
- Preview actual pressed/running/error states.
- Global theme plus per-key overrides.

## Wave 5 — v0.2.0: Unified local command bus

Goal: everything uses one safe command model.

### BUS-01: Command capabilities

Define capabilities:

- HID mouse
- HID keyboard
- SSH command
- Mac clipboard read
- Mac clipboard write
- Phone clipboard read
- Phone clipboard write
- Open URL
- Open Android app
- Automation trigger

Acceptance:

- Every action declares required capabilities.
- UI can explain why a key is disabled.
- AI Creator sees only enabled capabilities.

### BUS-02: Run history

Acceptance:

- Deck, Text, Clipboard, and Automations share run history.
- Store status, duration, capability names, and safe summaries.
- No raw secrets.

### BUS-03: Local-only trust model

Acceptance:

- No backend needed.
- Mac communication uses user-configured SSH/local network.
- Bluetooth HID uses Android permission and explicit session.
- AI never receives clipboard contents unless user explicitly pastes them into a prompt.

## Priority order

Do this next:

1. `CONN-01` through `CONN-03`: fix the reconnect pain.
2. `TEXT-01` through `TEXT-03`: make “type on phone, send to Mac” real.
3. `CLIP-01` and `CLIP-02`: manual clipboard bridge.
4. `DECK-01`: live visual status.
5. `CLIP-03` through `CLIP-06`: safe automatic clipboard.
6. `DECK-02` through `DECK-05`: Stream Deck/Codex Micro polish.

## Non-goals for this wave

- No public backend.
- No cloud clipboard relay.
- No always-on hidden clipboard scraping.
- No arbitrary shell generated by AI.
- No undocumented Codex desktop internals.
