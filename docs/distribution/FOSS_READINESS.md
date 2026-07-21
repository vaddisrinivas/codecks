# Codecks FOSS Readiness

Status: ready for maintainer review. Current public beta is `v0.1.9`.

## What Is Ready

- License: Apache-2.0 detected by GitHub.
- Source: full Android app source is public.
- Build: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Release: single signed APK and checksum workflow exists.
- Metadata: Fastlane/Izzy-style metadata and screenshots are present under `fastlane/metadata/android/en-US`.
- Privacy: no Codecks account, hosted backend, analytics SDK, ads SDK, public database, or cloud sync in the public beta.
- User data: local app storage; optional exports remain user-controlled.

## Anti-Feature Disclosure Draft

Potential F-Droid/IzzyOnDroid disclosures:

- **NonFreeNet:** optional AI provider calls may contact a user-selected external model provider.
- **Dangerous capability:** user-reviewed Mac commands can run over SSH against the user's own Mac.
- **Connectivity:** Bluetooth HID and local-network/SSH flows are core product behavior.

These should be disclosed clearly rather than hidden. The default app remains useful without AI.

## Before Submission

- Confirm the latest signed APK is attached to a GitHub release with checksum.
- Upload `docs/images/social-preview.png` as the GitHub social preview.
- Confirm screenshots show the current release UI.
- Re-run local quality gate and release-surface checks.
- Ask maintainers before submitting to stricter curated lists.

## Suggested GitHub Topics

`android`, `automation`, `bluetooth-hid`, `command-deck`, `foss`, `jetpack-compose`, `kotlin`, `local-first`, `macos`, `samsung-dex`, `trackpad`
