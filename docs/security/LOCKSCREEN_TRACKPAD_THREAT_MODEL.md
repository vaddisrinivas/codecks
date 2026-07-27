# Lockscreen Trackpad Threat Model

Status: draft implementation boundary

## Goal

Allow only a restricted Bluetooth HID pointer surface over the Android keyguard
when all readiness conditions are already true.

## Allowed while keyguard is showing

- pointer movement
- vertical scroll
- horizontal scroll
- left click
- right click
- middle click
- release all held mouse buttons

## Forbidden while keyguard is showing

- HID start, registration, pairing, reconnect, host selection, or disconnect
- any `HidCommand`
- keyboard text entry
- deck, clipboard, AI, automations, settings, notifications, or history
- SSH or helper actions
- custom or reactive actions
- multi-finger command gestures
- external links or arbitrary destinations

## Readiness conditions

- user unlocked the device at least once since boot
- user explicitly enabled lockscreen Trackpad
- Bluetooth permission already granted
- a selected HID host exists
- HID is already connected
- keyguard is still showing

## Threats and mitigations

### Stranger with physical access to the locked phone

Risk:

- pointer movement and mouse clicks can affect the paired Mac

Mitigation:

- opt-in only
- pointer-only capability set
- no text, commands, settings, or reconnect

### Malicious app launches the public URI

Risk:

- exported activity could expose the full app or warm HID

Mitigation:

- public URI goes only to a short-lived router
- router ignores extras for connection/auth state
- router never warms HID
- unknown origins fail closed

### Mutable or replayed PendingIntent

Risk:

- untrusted app mutates an internal entry path

Mitigation:

- widget and notification entry use explicit immutable `PendingIntent`
- internal origin is meaningful only on app-owned explicit intents

### Forged extras on the public URI

Risk:

- caller claims connected/authenticated state

Mitigation:

- policy state comes from `KeyguardManager`, `UserManager`, permission state,
  `HidState`, and stored settings
- extras never override policy state

### HID disconnect while a button is held

Risk:

- stuck drag or stuck mouse button

Mitigation:

- release buttons on disconnect
- close the restricted surface immediately

### Screen off/on or lock-state race

Risk:

- full app becomes visible or stale state survives

Mitigation:

- dedicated restricted activity only
- `FLAG_SECURE`
- exclude from Recents
- recompute policy before dispatch

### Reboot before first unlock

Risk:

- device-protected storage or stale tokens bypass post-boot restrictions

Mitigation:

- require `UserManager.isUserUnlocked()`
- do not move secrets or selected-host state into direct-boot storage

### Notification and back-stack exposure

Risk:

- keyguard reveals device names, notification content, or prior screens

Mitigation:

- no host names or private content on the restricted surface
- no full app navigation shell
- generic notification copy only

### Auto-launch denial of service

Risk:

- repeated background launches annoy or confuse the user

Mitigation:

- locked + disconnected public URI is ignored
- automatic entry never wakes the screen in this release
