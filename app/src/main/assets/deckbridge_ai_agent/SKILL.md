# Codecks Artifact Builder Skill

Use this skill when the user chats with Codecks AI to create, test, or refine anything custom: deck buttons, full decks, automation recipes, Mac scripts, app-specific control sets, trackpad background widgets, or workflow shortcuts.

## Inputs
- User request in natural language.
- Draft kind requested by the app: `action`, `deck`, or `automation`.
- Supported capabilities: `Advanced`, `Browser`, `Clipboard`, `HidKeyboard`, `HidMouse`, `Media`, `Shell`, `Ssh`.
- Target selector from the app: any connected Mac, current device, device id, or group id.

## Artifact Types

### Button
Create a single `definition` with a short title, clear description, safety metadata, and one or more steps. Good buttons do one obvious thing.

### Deck
Create a deck with 4-12 actions. Use consistent naming and button intent. Good templates include Browser, Media, Meetings, Window Manager, Developer, Presentation, System Controls, Finder, Terminal, and Focus.

### Automation
Create one recipe with trigger intent in the title/description and steps that reuse the same action definition model. V1 automations are manual-testable; background triggers can be described but must not be assumed active unless the schema supports them.

### Clock Or Background Widget
If the user asks for a clock, notification background, trackpad visual, or OLED background, create a button/action only if it is meant to change app settings. Otherwise explain through the artifact description what app-side widget is requested and use a safe no-op shell step such as `printf 'Trackpad clock style requested'`.

## Step Types
- `open_url`: requires `url`.
- `clipboard_text`: requires `value`.
- `delay`: requires `delayMs`.
- `shell` or `ssh_action`: requires `value`.
- `hid_key`: requires `value` naming the key/chord.

## Mac Script Patterns
- Open URL: use `open 'https://example.com'` or the `open_url` step.
- Copy text: use `clipboard_text` or `printf %s 'text' | pbcopy`.
- Screenshot: `screencapture -x ~/Desktop/codecks-$(date +%Y%m%d-%H%M%S).png`.
- Caffeinate: `caffeinate -dimsu -t 3600 >/dev/null 2>&1 &`.
- Run Apple Shortcut: `shortcuts run 'Shortcut Name'`.
- Open app: `open -a 'App Name'`.
- Chrome new tab: `osascript -e 'tell application "Google Chrome" to make new tab at end of tabs of front window'`.
- Finder home: `open ~`.
- Mission Control via HID is better than shell when Bluetooth HID is available.

## Quality Rules
- Titles: 2-4 words, button-ready.
- Descriptions: one sentence, direct.
- IDs: lowercase stable slugs with dots or underscores.
- Commands must be testable and should fail loudly when appropriate.
- Do not generate more than 12 deck actions.
- If a command might delete, close, overwrite, purchase, send, share, expose private data, or change security settings, set `safety.requiresConfirmation` to true and `safety.level` to `Dangerous`.

## Response
Return only the schema JSON requested by the app.
