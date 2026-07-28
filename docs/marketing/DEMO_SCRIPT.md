# Codecks Demo Script

Target: 60-70 second narrated feature tour, plus a 25-second silent 640×360
README GIF under 5 MB. All app frames come from the current debug build on an
emulator.

## Story

1. Hook: turn the Android phone already on the desk into Mac controls.
2. Trackpad: show that Bluetooth HID stays unavailable until a Mac is paired.
3. Keyboard: show explicit Send + Enter, Enter, and Command + Enter.
4. Clipboard: show manual transfer and opt-in directional sync.
5. Rules: show local When / If / Then routines and test-after-change status.
6. Editor: show resizable, replaceable Deck controls.
7. Palette: show searchable actions without permanent Deck clutter.
8. Lockscreen: show the pointer-only opt-in and its forbidden capabilities.
9. Invite: open source, local-first, and available on GitHub.

## Voiceover

The narration and per-scene timing live in
[`framecraft-demo.json`](framecraft-demo.json), the render source of truth.

## Extended Product Tour

Use a 45-60 second version when the audience needs the full product rather than the shortest hook:

1. Deck: run one visible, reversible Mac action.
2. Trackpad: move, click, scroll, and show the compact control sheet.
3. Keyboard: send a short line with `Send + Enter`, then show `Enter` and `Cmd + Enter`.
4. Clipboard: manually push or pull text and show conflict review if both sides changed.
5. Rules: test a recipe, inspect the result, then enable it.
6. Build: edit a Deck action and find it through Command Palette.
7. Desk use: show the opt-in lock-screen Trackpad only after Bluetooth HID is already connected.
8. Optional AI: draft an action, review the generated result, and stop before execution.

## Render Commands

```bash
FRAMECRAFT_DIR=/path/to/framecraft ./scripts/render_marketing_assets.sh
```

## Acceptance Checklist

- First frame shows the product name and one concrete use.
- Each scene has one focus point.
- All app screenshots are visually checked emulator captures from the current debug build.
- GIF is 640×360 and under 5 MB for README use.
- MP4 is 1920x1080 and works without relying on audio.
- MP4 has H.264 video, AAC audio, matched stream durations, and no black frames.
- The final GitHub invite card is present.
- No personal host, account, or command data is visible.
