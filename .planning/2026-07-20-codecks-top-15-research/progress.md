# Progress

## 2026-07-20

- Confirmed canonical checkout: `<codecks-checkout>`, branch `main`, tracking `origin/main`.
- Confirmed known untracked multimodule/v2 foundation remains present and out of scope.
- Loaded GitHub exploration, deep-research, and planning-with-files workflows.
- Created five-phase research plan; current phase is Codecks capability baseline.
- Read the current README/build configuration, prior scope-review packet, implementation symbol inventory, and live GitHub state.
- Recorded the shipped/gated feature baseline so research does not recommend duplicates.
- Completed source/docs baseline. Confirmed Air Mouse/Air Touch and advanced AI drafting already exist behind current product boundaries.
- Started a reusable GitHub keyword/deep-scan under the gh-repo-exploration workflow.
- Applied user scope correction: Air Touch remains Labs-only and is excluded from roadmap ranking.
- Initial broad GitHub scan produced only raw search artifacts; narrowed-query retry planned.
- Ran narrower GitHub searches and identified maintained HID, remote-input, command-deck, and sensor-pointer references.
- Deep scans succeeded for Kontroller, Deckboard, and AgentDeck. Three scans hit a helper null-topic bug; those will use direct GitHub API fallback.
- Completed direct GitHub fallback for HidPeripheral, KDE Connect Android, and Remote Touchpad; extracted concrete reliability, discovery, text, and gesture pain signals.
- Completed two Reddit research sweeps covering remote input, clipboard, Android-to-Mac control, gesture customization, accessibility, and sensor triggers.
- Reviewed current Android gesture, HID callback, sensor, and system-gesture guidance.
- Reviewed primary research on personalized touch models, adaptive sensor recognition, and ML prediction for single-versus-double taps.
- Reviewed official competitor surfaces and Apple Shortcuts integration options.
- Reviewed current Android on-device AI options and privacy/device-availability tradeoffs.
- Audited current implementations for action states, Activity receipts, Text snippets/recent sends, clipboard modes, pages, context/widget/tile gates, and sensor-learning gaps.
- Scored and selected the top 15 workstreams.
- Wrote the full evidence-backed roadmap to `report.md`.

## 2026-07-20 implementation continuation

- User expanded the active goal to "do all waves at once"; kept that as the multi-release objective.
- Chose a first cross-wave implementation slice in the shipping `app/` module while preserving the untracked multimodule `android/` foundation.
- Added content-free Trackpad gesture decisions and diagnostic summaries for later replay/AI coaching.
- Added adaptive tap correction settings: tap movement threshold, correction count, and reset behavior.
- Added a short-lived Trackpad "Wrong click" feedback chip after click-like decisions.
- Added redacted HID transition diagnostics and included them in the debug bundle export.
- Kept Air Touch gated under Labs; no productionization work was added.
- Validation passed:
  - `ANDROID_HOME=$HOME/Library/Android/sdk ANDROID_SDK_ROOT=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'io.codex.s23deck.core.trackpad.*'`
  - `ANDROID_HOME=$HOME/Library/Android/sdk ANDROID_SDK_ROOT=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest`
  - `ANDROID_HOME=$HOME/Library/Android/sdk ANDROID_SDK_ROOT=$HOME/Library/Android/sdk ./gradlew :app:lintDebug`
  - `ANDROID_HOME=$HOME/Library/Android/sdk ANDROID_SDK_ROOT=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug`
  - Gradle also ran `validateArchitectureBoundaries` and `validateReleaseSurface` during these gates.
- Remaining before the active "all waves" goal can be complete: physical Samsung-to-Mac HID proof, emulator/device UI smoke, release/versioning, command-bus unification, Shortcuts catalog, folders/search, presentation remote graduation, and AI coach/builder improvements.
