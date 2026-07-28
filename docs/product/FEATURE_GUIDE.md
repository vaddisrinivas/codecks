# Codecks Feature Guide

Applies to: public beta v0.1.23

This guide explains what the shipped features do, why they exist, what they
require, and where their boundaries are. It is a product guide, not a promise
that every feature fits every workflow.

## Ten user-facing feature groups

| # | Feature | What it does and why it is useful | Needs | Important limit |
| --- | --- | --- | --- | --- |
| 1 | Command Deck | Large persistent buttons run common Finder, Terminal, Spaces, media, screenshot, browser, and reviewed custom actions. It replaces memorized shortcuts and repeated menu navigation. | Mac connection for Mac actions | Custom commands can affect the Mac; review them before use. |
| 2 | Bluetooth Trackpad | Moves, clicks, drags, and scrolls through Android's Bluetooth HID profile. Haptics, sensitivity, rotation, scroll rail, fullscreen, and screen pinning adapt it to a desk position. | Bluetooth permission and paired compatible host | Touch input differs from a dedicated mouse and needs learning. |
| 3 | Restricted lockscreen Trackpad and desk entry | A widget, HID notification, exact `codecks://trackpad` URI, NFC tag, or Tasker profile opens Trackpad quickly. When explicitly enabled and already connected, the locked surface exposes pointer input only. | Existing HID connection; explicit lockscreen opt-in; Tasker or NFC only for automation | It cannot pair, reconnect, type, run commands, read clipboard, or open settings while locked. Android may block background launches. |
| 4 | Remote Keyboard | Sends text, navigation keys, media/function controls, Enter, and Command+Enter. Snippets reduce repeated typing. Auto mode uses Bluetooth for short text and Mac clipboard paste for longer or Unicode text. | Bluetooth for HID keys; configured Mac connection for clipboard paste | Send clears text only after success. Clipboard-paste delivery temporarily uses the Mac clipboard. |
| 5 | Clipboard Bridge | Shows phone and Mac previews and hashes, manually sends either direction, detects conflicting changes, and offers off, one-way, or two-way timed sync. It moves text without messaging it to yourself. | Configured Mac connection | Automatic sync runs while Codecks is open; it is text-oriented and may require choosing a side after conflicts. |
| 6 | Rules | Builds local When / If / Then routines from manual, time, app, clipboard, Wi-Fi, Mac-awake, file-change, or battery triggers. Testing and approval make repeated Mac routines safer. | Configured Mac connection for Mac commands | New or changed Rules must be tested before enablement; Android scheduling is not exact real-time automation. |
| 7 | Deck Editor and local backup | Reorders, resizes, replaces, duplicates, tests, and styles buttons. JSON backup preserves Deck and Rules while deliberately excluding API keys, SSH keys, passwords, and connection secrets. | No connection for editing; Mac connection to test Mac actions | Restore replaces current Deck and Rules. Keep the exported file private. |
| 8 | Command Palette and Run History | Searches across actions and Rules instead of consuming permanent Deck space. Run History shows outcomes and supports diagnosis and review. | Same connection as the selected action | Search does not bypass action safety, approval, or connection requirements. |
| 9 | AI Builder | Sends an optional prompt directly to a selected AI provider to draft a button, Deck, or Rule. It saves setup time while keeping generated actions disabled until tested and reviewed. | User-supplied provider key and internet access | AI output can be wrong. No generated command runs merely because it was generated. |
| 10 | Adaptive layouts and appearance | Reflows navigation and controls for phones, tablets, landscape, freeform windows, and Samsung DeX. Fullscreen, themes, Deck styles, shapes, borders, and icon packs make the persistent control surface readable in its actual placement. | None | Layout adaptation does not make every Mac action available without its transport setup. |

## Which connection does a feature use?

| Channel | Used for | Why |
| --- | --- | --- |
| Bluetooth HID | Trackpad, clicks, scrolling, keyboard keys, short text, media controls | Low-latency standard input; no Mac helper or SSH command is needed. |
| Mac secure connection | Deck Mac actions, Rules, clipboard exchange, long/Unicode paste, Mac state checks | Typed or reviewed Mac operations need authenticated command and data exchange. SSH host keys are pinned. |
| Direct AI-provider request | Optional AI Builder only | Codecks has no hosted account or AI proxy. The request goes to the provider selected by the user. |
| Local Android storage | Deck, Rules, settings, run history, encrypted keys | Core configuration remains on the phone and works without a Codecks backend. |

## Common reasons to use each surface

- **Trackpad:** a mouse is missing, inconvenient, or occupying scarce desk
  space; quick pointer access matters more than mouse-grade precision.
- **Deck:** an action is frequent enough to deserve one large, fixed button.
- **Command Palette:** an action is occasional and should remain searchable
  without crowding the Deck.
- **Keyboard:** text or a modifier sequence is easier to compose on the phone,
  or should be reused as a snippet.
- **Clipboard:** text must cross devices without email, chat, or cloud notes.
- **Rules:** the same checked sequence repeats on a schedule or observable
  condition.
- **AI Builder:** the desired workflow is clear in plain language but tedious
  to translate into structured buttons or Rules.
- **Lockscreen entry:** the phone is already docked and the user needs pointer
  access without exposing higher-risk controls.

## Safety boundaries

- While locked, only pointer movement, scrolling, mouse buttons, and release of
  held buttons are permitted.
- Built-in command templates use an allowlist; dangerous shell patterns are
  blocked.
- Changed Rules require a current successful test before they can be enabled.
- Generated actions remain disabled until reviewed.
- SSH host identity is pinned; optional AI keys and the SSH private key are
  protected with Android Keystore-backed encryption.
- Local backup excludes secrets.

See:

- [Security policy](../../SECURITY.md)
- [Privacy policy](../../PRIVACY.md)
- [Lockscreen threat model](../security/LOCKSCREEN_TRACKPAD_THREAT_MODEL.md)
- [Tasker and NFC Trackpad entry](../integrations/TASKER_TRACKPAD_AUTOLAUNCH.md)

## Experimental and default-off

These exist behind developer or feature flags and are not core public-beta
features:

- Smart suggestions and Smart Deck/Keyboard/Clipboard/Rules/Settings;
- Reactive Trackpad controls;
- notification-context and OCR experiments;
- AirMouse and AirTouch;
- back-tap and volume-key controls.

Do not describe these as generally available without naming the experimental
flag and validating the exact build.

## Infrastructure, not a user feature

v0.1.23 includes a Kotlin Multiplatform shared module, typed authenticated
protocol models, an Android Mac-helper client scaffold, and buildable iOS
framework targets. These prove shared foundations compile and have hostile
protocol tests. They do not provide a native Mac helper or an iOS app.

## Deferred

Not shipped:

- native Mac helper and complete helper-pairing UI;
- DeskDock confidence engine;
- Apple Shortcuts provider;
- SFTP file transfer;
- monitor brightness provider;
- Accessibility discovery;
- complete iOS application.
