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
