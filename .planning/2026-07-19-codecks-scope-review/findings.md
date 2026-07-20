# Codecks Scope Review Findings

## Initial Context

- Memory says PR #11 merged and final open PR query returned `[]`.
- Memory warns AppFunctions checkout had many untracked files and only selected files were committed.
- Current checkout is `<codecks-checkout>` on `codex/issue-10-appfunctions`, remote `origin=https://github.com/vaddisrinivas/codecks.git`.
- Current dirty state contains many untracked files under `android/`, `docs/`, `mac-actions/`, `protocol/`, and `tools/`.

## GitHub And Release State

- Live GitHub PR list shows PRs #5, #6, #7, #8, #9, and #11 merged; no open PRs found in the latest query.
- Live GitHub issue list shows only issue #10, closed by the AppFunctions PR.
- After `git fetch origin --prune`, `origin/main` is `e8dfcf2 Merge pull request #11 from vaddisrinivas/codex/issue-10-appfunctions`.
- Local branch `codex/issue-10-appfunctions` tracks a deleted remote branch and is one commit behind the merge commit wrapper on `origin/main`.

## Committed Product Shape

- README positions Codecks as an Android phone/tablet/DeX local-first command deck, Bluetooth HID trackpad, keyboard/text surface, automations surface, and optional AI drafting app for Mac control.
- Production launch plan says current beta is not GA; GA gates include device/macOS matrix, accessibility, reliability, automation safety, pairing UX, DeX QA, release operations, store decision, and AI draft reliability.
- Next-wave plan prioritizes HID session persistence, reconnect state machine, host health/repair UI, first-class text-to-Mac, and controlled clipboard bridge.
- CI runs privacy scan, unit tests, lint, debug APK build, and artifact upload.
- Default flags currently enable Deck, Trackpad, Automations, AI, AI Builder, Deck Editor, Connection, Keyboard, Clipboard, and Settings; incubator/context/widget/activity/devices/premium/advanced/appearance/labs remain disabled.

## Local Untracked Scope

- Untracked `android/` tree contains a separate Gradle multimodule foundation with modules for core, data, domain, feature, runtime, transport, and app.
- Untracked `protocol/` contains action schemas and fixtures; `mac-actions/` contains a catalog fixture; `docs/adr` and `docs/evidence` contain protocol/release evidence.
- AppFunctions symbol search found `android/app/src/main/java/io/codecks/app/CodecksAppFunctionService.kt`, meaning local untracked tree contains implementation not present as committed root `app/src/...` code.
- Direct `origin/main` tree check shows PR #11 committed only a narrow `android/app` AppFunctions slice plus `android/gradle/libs.versions.toml`, not the full multimodule Android foundation.
- Local-only files still include `android/settings.gradle.kts`, root `android/build.gradle.kts`, feature/domain/data/runtime/transport modules, protocol schemas, Mac fixture catalog, release evidence, and `tools/check-local-release-boundary.sh`.

## Technical Review Notes

- Root app is still monolithic in places: `MainActivity.kt` is 1753 lines, `AiProviderSettingsScreen.kt` is 1842 lines, `AutomationsScreen.kt` is 1018 lines.
- `HidSessionService` exists and runs a foreground keep-alive loop, but the product plan still calls for a more explicit HID state machine, reasoned diagnostics, and repair UI.
- Clipboard has a visible screen with manual and automatic modes, but production plan previously said clipboard should remain disabled for beta; current default flags enable Clipboard, Keyboard, AI, and AI Builder.
- Current latest release is `v0.1.3`, published 2026-07-18T01:05:37Z, before the PR #5-#9/#11 merge batch. Main CI is green at `e8dfcf2`.
- There are 126 committed root app source files and 56 root unit/instrumented test files under the inspected directories.

## Recommended Priority

1. Make connection/HID reliability boring: explicit session manager, reconnect state machine, last failure reasons, repair flows, and physical device matrix.
2. Promote Text-to-Mac as a first-class flow using short HID typing plus long/unicode SSH pasteboard mode.
3. Make Clipboard manual-only first, then gated automatic modes with conflict UX and sensitive-content protections.
4. Add live deck key status, run history, and command receipts across Deck/Text/Clipboard/Automations.
5. Decide whether the local-only multimodule `android/` foundation is the next architecture PR or should be archived before it rots.
6. Tighten GA readiness: accessibility, DeX resize, tester telemetry without analytics, release artifact after merged main, and source-set boundaries for incubators.
