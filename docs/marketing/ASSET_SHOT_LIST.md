# Codecks Asset Shot List

Use this list before publishing a release, showcase PR, Reddit post, or Show HN post.

## Current Captures

| Asset | Path | Use |
| --- | --- | --- |
| Home command deck | `docs/images/screenshots/codecks-home.png` | README gallery, FOSS listings, demo scene 1 |
| Trackpad setup boundary | `docs/images/screenshots/codecks-trackpad.png` | Store listing, demo scene 2 |
| Keyboard | `docs/images/screenshots/codecks-keyboard.png` | README gallery, store listing, demo scene 3 |
| Clipboard | `docs/images/screenshots/codecks-clipboard.png` | Store listing, demo scene 4 |
| Rules | `docs/images/screenshots/codecks-automations.png` | README gallery, store listing, demo scene 5 |
| Deck editor | `docs/images/screenshots/codecks-deck-editor.png` | Store listing, demo scene 6 |
| Command Palette | `docs/images/screenshots/codecks-command-palette.png` | Store listing, demo scene 7 |
| Lockscreen restrictions | `docs/images/screenshots/codecks-lockscreen-settings.png` | Store listing, demo scene 8 |
| AI Builder setup | `docs/images/screenshots/codecks-ai-builder.png` | Optional AI documentation |
| Gallery strip | `docs/images/screenshot-gallery.png` | README hero |
| Overview strip | `docs/images/codecks-overview.png` | Five-surface feature overview |
| Framecraft demo | `docs/images/codecks-demo.mp4` | Full narrated demo |
| README demo | `docs/images/codecks-demo.gif` | Silent README playback |
| Social preview | `docs/images/social-preview.png` | GitHub social preview, Reddit/HN link preview |
| Fastlane screenshots | `fastlane/metadata/android/en-US/images/phoneScreenshots/` | IzzyOnDroid, F-Droid, Play listing prep |

## Missing Ideal Launch Assets

- One signed-release install screenshot, not a debug build.
- One tablet or DeX screenshot showing adaptive layout.
- One Mac pairing or SSH host-key verification screenshot with private host data redacted.
- One connected Trackpad screenshot from a compatible Bluetooth HID environment.
- One opt-in locked Trackpad screenshot after HID is already connected.
- One generated AI result preview using non-sensitive demo text and no real provider key.

The current emulator cannot supply a paired Bluetooth HID Mac. Do not fabricate
connected Trackpad or lockscreen-pointer states; capture them only from a
compatible test environment.

## Publishing Rules

- Redact hostnames, IPs, SSH usernames, API key names, and personal command text.
- Do not imply Codecks directly controls macOS without user-reviewed SSH/Bluetooth setup.
- Show the review step for generated automations before showing enablement.
- Use local-first copy first; optional AI copy second.

## Social Crop Notes

- GitHub social preview: use `docs/images/social-preview.png`.
- Reddit image posts: use `docs/images/screenshot-gallery.png`.
- Show HN link: point to the repository after the README image, release, and demo GIF are present.
