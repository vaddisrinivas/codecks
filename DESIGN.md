# Codecks design language

Codecks is a local-first Mac control app. The UI should feel like a precise tool: calm, dense where useful, direct, and never magical.

## Product nouns

- **Mac**: a configured computer. Avoid user-facing `target`, `host`, or `device`.
- **Button**: an item on the Deck. Avoid user-facing `action`, `tile`, or `key`.
- **Deck**: a layout of buttons.
- **Rule**: a When/If/Then automation. Avoid user-facing `recipe`, `workflow`, or `automation` except in explanatory text.
- **Trackpad**: the remote pointer and gesture surface.
- **Keyboard**: the dedicated Mac typing and shortcut surface.
- **Clipboard**: the dedicated phone-to-Mac text transfer surface.
- **AI Builder**: creates draft buttons, decks, and rules. Avoid `chat`, `artifact`, and protocol version names.
- **Run history**: completed button/rule records.
- **Connect a Mac**: SSH setup workflow.

## Status vocabulary

Use only these user-facing status words:

- **Connected**: the Mac is actively connected.
- **Ready**: setup exists and commands can run.
- **Offline**: configured but unreachable.
- **Setup needed**: missing key, trust, or permission.
- **Connecting…**
- **Checking…**
- **Failed**

Do not mix in `Live`, `Online`, `Current`, or `target`.

## Voice

- Direct.
- Technical, not implementation-specific.
- Sentence case.
- No hype, magic, anthropomorphic assistant copy, protocol versions, or over-strong security claims.
- Buttons must include the object when context is weak: **Select Mac**, **Test connection**, **Save API key**, **Save disabled**, **Clear run history**.

## Visual grammar

Use four surface levels:

1. Page background.
2. Section.
3. Interactive row/control.
4. Modal/sheet.

Reserve luminous deck styling for Deck buttons, Trackpad active controls, and run feedback. Settings, history, provider setup, and connection lists should use restrained Material rows.

Avoid:

- cards inside cards
- hero cards on utility settings pages
- bordered pills for ordinary metadata
- large icon circles beside routine settings
- multiple accent colors in one section

## Layout rules

- Bottom navigation shows each enabled primary destination: Deck, Trackpad, Keyboard, Clipboard, Rules, AI, and Settings.
- Keyboard and Clipboard are primary destinations, not Trackpad modes.
- Use Material navigation components.
- Keep all tap targets at least 48dp.
- Use the 8dp grid: 8, 12, 16, 24, 32.
- Primary actions should be thumb-reachable where practical.
- Text fields that summon the keyboard should not hide the generated result; bottom-compose only when the preview remains visible above it.

## AI Builder flow

One draft at a time:

1. Describe button/deck/rule.
2. Generate draft.
3. Review preview.
4. Test draft.
5. Save disabled.

No transcript UI. History is draft history, not conversation history.
