# Progress

Date: 2026-07-20

## Completed

- Recovered context from the recently archived Codecks work.
- Released root Codecks v0.1.4 from local work.
- Checked local branches and stashes.
- Identified local-only v2 WIP.
- Repaired nested v2 build by parking unresolved AppFunctions integration.
- Checkpointed Codecks v2 local foundation to `main`.
- Investigated Reddit/GitHub/productivity signals for Codex dashboard and agent control-surface ideas.
- Captured durable plan for Codex Cockpit + Fancy Deck.
- Added planned implementation scaffolding:
  - `android/domain/codex` for Codex task snapshots, fancy button specs, effect specs, theme specs, and mock cockpit data.
  - `android/core/designsystem/.../FancyDeckKey.kt` for fancy emoji/empty buttons and visual effects.
  - `android/feature/codex` for the mock-first Codex Cockpit screen.
  - App shell route wiring for a new top-level `Codex` tab.
- Verified focused compile gate:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon :app:compileLocalDebugKotlin :feature:codex:compileDebugKotlin :core:designsystem:compileDebugKotlin :domain:codex:compileKotlin`
  - Result: `BUILD SUCCESSFUL in 42s`.
- Added next-depth scaffolding:
  - domain tests for mock cockpit task states, fancy buttons, and theme presets.
  - empty button lab placeholder in the Codex Cockpit screen.
  - `protocol/codex-cockpit/snapshot.schema.json`.
  - `protocol/codex-cockpit/example-snapshot.json`.
  - `mac-actions/codex-cockpit/README.md`.
- Implemented the local fancy button editor path:
  - tapping fancy buttons now selects them for editing.
  - emoji choices update the visible key.
  - effect choices update the key effect model.
  - success/arcade/danger chips change visual role and safety level.
  - "Make empty" returns a button to an assign-later placeholder.
- Added `mac-actions/codex-cockpit/emit-mock-snapshot.sh` to emit a privacy-safe bridge payload matching the protocol shape.
- Marked `mac-actions/codex-cockpit/emit-mock-snapshot.sh` executable.
- Added Android-side bridge JSON parsing:
  - `CodexBridgeSnapshotParser` decodes protocol snapshots into `CodexCockpitSnapshot`.
  - parser rejects snapshots that include prompt content.
  - parser rejects snapshots that include source content.
  - parser tests cover counts, source mapping, default buttons/themes, and privacy rejection.
- Added app-private persistence for fancy button edits:
  - `feature:codex` loads saved `DeckButtonSpec` values from SharedPreferences.
  - edits are serialized with kotlinx.serialization JSON.
  - decode failures fall back to mock buttons instead of breaking the cockpit.
- Added interactive bridge intake in the Codex Cockpit:
  - users can paste status-only bridge JSON.
  - successful parse replaces the live task list.
  - parser rejection messages are shown in the cockpit.
- Expanded the fancy button editor:
  - editable label.
  - editable status label.
  - editable action id.
  - emoji/effect/theme/safety controls remain available.
- Added `mac-actions/codex-cockpit/emit-release-snapshot.sh` for Git/GitHub release status snapshots.
- Marked `mac-actions/codex-cockpit/emit-release-snapshot.sh` executable.
- Added local HTTP bridge fetch in Android:
  - `Bridge intake` now has a bridge URL field.
  - `Fetch bridge` downloads JSON with `HttpURLConnection`.
  - downloaded JSON is parsed with the same status-only privacy gate.
  - default URL targets the Android emulator bridge path.
- Added localhost/emulator cleartext allowlist in the Android network security config.
- Added `mac-actions/codex-cockpit/serve-local-bridge.sh` to emit snapshots and serve them on port `8765`.
- Marked `mac-actions/codex-cockpit/serve-local-bridge.sh` executable.
- Added automatic local Codex metadata emitter:
  - `mac-actions/codex-cockpit/emit-local-codex-snapshot.py`.
  - reads only `~/.codex/session_index.jsonl` and `~/.codex/process_manager/chat_processes.json`.
  - does not read prompts, session bodies, source files, tool outputs, or conversation content.
  - emits status-only `local-codex-metadata` snapshots.
- Updated `serve-local-bridge.sh` to serve `/codecks-local-codex-snapshot.json`.
- Verified local Codex metadata emitter:
  - `mac-actions/codex-cockpit/emit-local-codex-snapshot.py /tmp/codecks-local-codex-snapshot.json /Users/srinivasvaddi/.codex 8`
  - Result: emitted non-empty `/tmp/codecks-local-codex-snapshot.json` with 8 metadata-only tasks and privacy flags false.
- Verified bridge server with local Codex metadata endpoint:
  - served and fetched non-empty `/codecks-local-codex-snapshot.json`.
- Verified focused gate after local Codex metadata emitter:
  - `cd android && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon :domain:codex:test :feature:codex:compileDebugKotlin :app:compileLocalDebugKotlin`
  - Result: `BUILD SUCCESSFUL in 10s`.
- Verified focused gate after HTTP bridge fetch/server patch:
  - `chmod +x mac-actions/codex-cockpit/serve-local-bridge.sh && cd android && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon :domain:codex:test :feature:codex:compileDebugKotlin :app:compileLocalDebugKotlin`
  - Result: `BUILD SUCCESSFUL in 18s`.
- Verified local bridge server smoke:
  - started `mac-actions/codex-cockpit/serve-local-bridge.sh /Users/srinivasvaddi/Projects/codecks 8765`.
  - fetched non-empty `http://127.0.0.1:8765/index.json`.
  - fetched non-empty `http://127.0.0.1:8765/codecks-codex-cockpit-snapshot.json`.
  - fetched non-empty `http://127.0.0.1:8765/codecks-release-cockpit-snapshot.json`.
- Verified latest focused gate:
  - `chmod +x mac-actions/codex-cockpit/emit-release-snapshot.sh && cd android && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon :domain:codex:test :feature:codex:compileDebugKotlin :app:compileLocalDebugKotlin`
  - Result: `BUILD SUCCESSFUL in 14s`.
- Verified both Mac-side emitters:
  - `mac-actions/codex-cockpit/emit-mock-snapshot.sh /tmp/codecks-codex-cockpit-snapshot.json && test -s /tmp/codecks-codex-cockpit-snapshot.json && mac-actions/codex-cockpit/emit-release-snapshot.sh /Users/srinivasvaddi/Projects/codecks /tmp/codecks-release-cockpit-snapshot.json && test -s /tmp/codecks-release-cockpit-snapshot.json`
  - Result: emitted non-empty `/tmp/codecks-codex-cockpit-snapshot.json` and `/tmp/codecks-release-cockpit-snapshot.json`.
- Verified latest focused gate:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon :domain:codex:test :feature:codex:compileDebugKotlin :app:compileLocalDebugKotlin`
  - Result: `BUILD SUCCESSFUL in 21s`.
- Verified mock bridge emitter:
  - `mac-actions/codex-cockpit/emit-mock-snapshot.sh /tmp/codecks-codex-cockpit-snapshot.json && test -s /tmp/codecks-codex-cockpit-snapshot.json`
  - Result: emitted non-empty `/tmp/codecks-codex-cockpit-snapshot.json`.
- Verified broader nested Android suite:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon assembleLocalDebug assembleIncubatorDebug :app:testLocalDebugUnitTest :feature:deck:testDebugUnitTest :domain:targets:test :domain:decks:test :domain:codex:test :domain:actions:test :runtime:actions:test :data:decks:testDebugUnitTest :data:receipts:testDebugUnitTest :data:targets:testDebugUnitTest :core:security:test :transport:ssh:test checkReleaseBoundary`
  - Result: `BUILD SUCCESSFUL in 1m 2s`.
- Verified second focused gate:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon :domain:codex:test :feature:codex:compileDebugKotlin :app:compileLocalDebugKotlin`
  - Result: `BUILD SUCCESSFUL in 16s`.
- During emulator proof, discovered compact phone layout had no top-level navigation to the `Codex` tab.
- Added bottom navigation for compact phone layouts so `Deck`, `Codex`, `Trackpad`, `Automations`, and `Settings` are reachable.
- Verified compact emulator rendered bridge proof:
  - installed `android/app/build/outputs/apk/local/debug/app-local-debug.apk` on `emulator-5554`.
  - started `mac-actions/codex-cockpit/serve-local-bridge.sh /Users/srinivasvaddi/Projects/codecks 8765`.
  - configured `adb reverse tcp:8765 tcp:8765`.
  - launched `io.codecks.app.debug/io.codecks.app.MainActivity`.
  - entered demo mode and opened the `Codex` bottom tab.
  - fetched default `http://10.0.2.2:8765/codecks-local-codex-snapshot.json`.
  - UI showed `Fetched 12 status-only tasks.` and rendered `Run emulator verification tests` from `local-codex-metadata`.
  - screenshot: `/tmp/codecks-render-08-codex-local-fetch-clean.png`.
- Added guarded custom button action execution:
  - safe action ids can fetch `bridge.fetch.local-codex`, `bridge.fetch.release`, or `bridge.fetch.mock`.
  - unknown action ids are saved but not executed.
  - no arbitrary shell execution is exposed from fancy buttons.
- Verified focused gate after guarded action execution:
  - `cd android && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon :domain:codex:test :feature:codex:compileDebugKotlin :app:compileLocalDebugKotlin`
  - Result: `BUILD SUCCESSFUL in 15s`.
- Verified broader nested Android suite after guarded action execution:
  - `cd android && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon assembleLocalDebug assembleIncubatorDebug :app:testLocalDebugUnitTest :feature:deck:testDebugUnitTest :domain:targets:test :domain:decks:test :domain:codex:test :domain:actions:test :runtime:actions:test :data:decks:testDebugUnitTest :data:receipts:testDebugUnitTest :data:targets:testDebugUnitTest :core:security:test :transport:ssh:test checkReleaseBoundary`
  - Result: `BUILD SUCCESSFUL in 15s`.
- Checked connected Android devices for physical proof:
  - `adb devices -l`
  - Result: only `emulator-5554` was connected; no physical S23 Ultra was available.
- Verified release-readiness checks after the current implementation:
  - `python3 tools/secret_surface_check.py`
  - Result: `secret surface OK`.
  - `cd android && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon checkReleaseBoundary`
  - Result: `BUILD SUCCESSFUL in 6s`.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew --no-daemon :app:check`
  - Result: `BUILD SUCCESSFUL in 6s`.

## Current plan location

- `.planning/2026-07-20-codecks-codex-cockpit-fancy-deck/task_plan.md`
- `.planning/2026-07-20-codecks-codex-cockpit-fancy-deck/findings.md`
- `.planning/2026-07-20-codecks-codex-cockpit-fancy-deck/progress.md`

## Current recommendation

Continue with the remaining release-quality gaps:

1. Add guarded execution for safe custom button action ids.
2. Run physical-device proof on S23 Ultra if available.
3. Re-run broader nested suite after the latest compact-navigation/default-bridge changes.
4. Decide whether this is a checkpoint-only build or a new public release.

## Not yet done

- Full nested Android test/build suite is green after the latest guarded-action changes.
- Focused compile/test gate and broader nested suite are green after the latest guarded-action changes.
- Basic local HTTP bridge is implemented, smoke-tested, and proven from emulator UI.
- Automatic local Codex metadata reader is implemented from metadata-only files and proven from emulator UI.
- Button edits persist locally, including label/status/action id, and guarded bridge-fetch actions are implemented.
- Rendered emulator proof exists; physical-device proof is still not done.
- Mac-to-emulator rendered bridge proof exists; physical-phone rendered bridge proof is still not done.
- No AppFunctions revival was attempted.
- No public release was created from this new plan; current recommendation is commit/push checkpoint first, then release only after physical-device proof or an explicit checkpoint-release decision.

## Next action

Run physical-device proof if the S23 Ultra is connected, then decide whether to package/release this checkpoint.
