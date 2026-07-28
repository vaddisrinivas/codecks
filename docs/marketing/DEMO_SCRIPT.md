# Codecks Demo Script

Target: 20-30 seconds, silent GIF for README and 1080p MP4 for social posts.

## Story

1. Hook: "Your Android phone can be a Mac command deck."
2. Proof: show the command deck with large, tappable actions.
3. Magic: switch to the Bluetooth trackpad.
4. Safety: show automations as reviewable local recipes.
5. Invite: open source, local-first, star on GitHub.

## Voiceover

Meet Codecks. Turn an Android phone into a command deck, trackpad, and automation surface for your Mac.

Tap big controls for Finder, Terminal, Spaces, media, screenshots, and browser tabs.

Switch to a Bluetooth trackpad when the Mac is across the room.

Draft automations locally. Test them before they ever run.

Open source, local-first, and ready to try.

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
python3 /path/to/framecraft/framecraft.py render docs/marketing/framecraft-demo.json --output docs/images/codecks-demo.mp4 --auto-duration
ffmpeg -y -i docs/images/codecks-demo.mp4 -vf "fps=12,scale=640:360:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" docs/images/codecks-demo.gif
```

## Acceptance Checklist

- First frame shows the product name and one concrete use.
- Each scene has one focus point.
- GIF is under 5 MB for README use.
- MP4 is 1920x1080 and works without relying on audio.
- No personal host, account, or command data is visible.
