# Codecks v0.1.35 release notes

Date: August 2, 2026

Release URL: https://github.com/vaddisrinivas/codecks/releases/tag/v0.1.35

## Summary

Codecks v0.1.35 is a production-hardening release focused on predictable setup,
reconnection, input delivery, clipboard behavior, automation safety, recovery,
diagnostics, accessibility, and adaptive layouts. It does not add another
experimental surface. It makes the existing product substantially safer and
more self-explanatory.

## Connection setup and recovery

- Added durable structural and terminal setup proof for the selected Mac.
- Bound setup proof to the exact target, host-key identity, revision,
  capabilities, and freshness window.
- Added a clock-driven proof-expiry path so UI readiness and background work
  stop when verification expires.
- Centralized the terminal-proof guard below UI call sites so actions,
  automations, clipboard operations, polling, and reactive execution fail
  closed.
- Replaced arbitrary setup-command bypasses with a typed fixed capability
  probe.
- Clear passwords whenever the host, port, user, or selected Mac changes.
- Ensure authorization, test, rotate, reset, and remove flows always clear
  secrets and leave a terminal UI state, including cancellation and persistence
  failure.
- Made proof persistence explicitly durable before the app reports setup
  success.
- Added clearer offline, authentication, trust, permission, sleeping-Mac, and
  recovery states.

## Bluetooth HID and keyboard reliability

- Added a priority lifecycle path for phone lock and Bluetooth-off events.
- Phone lock immediately invalidates queued keyboard and consumer input.
- Emergency Stop releases input and disconnects before attempting persistence.
- Serialized HID persistence so a rapid connect/stop sequence cannot restore a
  stale connected intent after process restart.
- Isolated event-processing failures so one storage error cannot kill the HID
  lifecycle loop.
- Added typed delivery receipts backed by Bluetooth HID report acceptance.
- Keyboard drafts clear only after text, Paste, and Enter dispatch are
  confirmed.
- Added bounded/coalesced HID event handling and disconnected-only reconnect.
- Always attempt a zero-key release after a press, including false and
  exceptional transport results.
- Added a fallback `releaseAllInputs` cleanup if confirmed delivery or the
  release report fails.
- Removed direct ViewModel connection paths that bypassed the unified
  permission/confirmation coordinator.
- Preserved production SSH reliability: release minification and resource
  shrinking remain disabled.

## Clipboard reliability and privacy

- Serialized clipboard decisions and revalidated an immutable configuration
  snapshot before writing.
- Added typed `Blocked`, `AppliedUnverified`, conflict, offline, and retryable
  outcomes instead of treating every transport write as success.
- Added verification retry/backoff without blindly rewriting clipboard
  contents.
- Persisted only expected hashes for pending verification; clipboard contents
  are not persisted for this purpose.
- Added a monotonic 15-minute visible-session authority that cannot be extended
  by wall-clock rollback.
- Added an independent expiry timer and stopped polling when terminal Mac proof
  is absent or revoked.
- Retain Android shared text until a terminal receipt, with explicit Retry and
  Discard paths.
- Propagate coroutine cancellation and fatal failures rather than converting
  them into ordinary sync errors.
- Improved visible last-sync, warning, offline, and verification state.

## Automation execution safety

- Added a shared automation execution coordinator used by foreground and
  background paths.
- Resolve and pin one explicit Mac target and host-key identity for preflight,
  live test, every step, cleanup, and final dispatch.
- Added exact trigger claims with revision binding, owner leases, SHA-256
  fingerprints, collision-safe migration, and compare-and-set completion.
- Prevent concurrent UI and WorkManager execution of the same trigger claim.
- Release retryable claims before retry so transient connectivity failures do
  not consume or stall an event.
- Record execution start before side effects and fail closed as
  `EXECUTION_UNCERTAIN` after interruption instead of replaying a
  non-idempotent action.
- Re-authorize catalog actions after resolving the stored action and revision,
  preventing hidden dangerous actions from bypassing confirmation.
- Added strict generated-command, interpreter, expansion, path, and AppleScript
  policy.
- Visual effects now require byte-for-byte compiler-reproducible scripts;
  marker text alone cannot bypass generated-command policy.
- Added injection-safe path/application preflight parsing and exact mandatory
  requirement codes.
- Reject future-dated, stale, wrong-target, wrong-identity, and wrong-revision
  preflight or live-test receipts.
- Prevent corrupt automation storage from being silently overwritten; surface
  explicit recovery/reset UI instead.
- Scrub legacy persisted run histories so old SSH output and target details are
  not re-emitted.
- Added a deterministic, source-bound AI evaluation receipt covering policy,
  compiler, catalog, parser, runner, conversion, and automation tests.

## Backup, update, and support recovery

- Added bounded backup input and a consistent bounded exact-recovery format.
- Publish typed corrupt/recovery-required outcomes and quarantine malformed
  recovery records.
- Keep exact rollback snapshots until rollback is proven successful.
- Clean aged temporary recovery residue and expose bounded quarantine
  inventory/deletion.
- Propagate cancellation and fatal parser failures correctly.
- Tightened GitHub update URL normalization to the exact repository release
  path.
- Added foreground-only update cancellation with a latched
  `NotForeground` result, including background-then-resume races.
- Added durable support-bundle discovery, retry, share, close, and explicit
  delete behavior.
- Back/outside dismissal retains recoverable support bundles; deletion is never
  implicit.
- Verify deletion before clearing pending support state.

## Accessibility and adaptive layouts

- Added large-text reflow and stable status semantics.
- Corrected focus order, one-shot failure focus, modal Back/Esc behavior, and
  non-duplicated error announcements.
- Preserved meaningful status detail for TalkBack.
- Added Compose runtime semantics coverage using current v2 test APIs.
- Tightened width/font-scale policy tests and named viewport tests honestly;
  they do not claim physical Samsung DeX proof.

## Validation

- Android release unit suite: 816 tests, 0 failures, 3 skipped.
- Android debug lint, debug APK assembly, release compilation, release
  Android-test compilation, and architecture boundary checks passed.
- Gradle-managed Pixel 6 API 35 signed-release suite: 5 tests, 0 failures,
  1 intentional live-SSH skip.
- Gradle-managed Pixel 6 API 35 debug suite: 19 tests, 0 failures,
  1 intentional live-SSH skip.
- Compose accessibility component tests run against the debug-only test host;
  the signed release APK does not ship a test activity.
- Production code minification and resource shrinking remain disabled.
- Secret-surface scan passed.
- All 46 bundled Mac actions passed JSON, shell, AppleScript, and tool checks.
- AI Creator offline corpus passed: 120 cases plus 19 adversarial bypass cases.
- Deterministic AI unit-gate receipt and report checks passed.
- Multiple independent all-priority audits were run until the final focused
  audits reported zero actionable findings.
- GitHub Actions rebuilds and tests the exact production-signed tag artifact
  before publication.

## Evidence boundaries

- Managed-emulator expanded-window tests are not physical Samsung DeX
  acceptance.
- GitHub runners cannot prove live Bluetooth HID delivery or live Mac SSH.
- Physical-phone install, lock/screen-off behavior, long-session battery use,
  TalkBack acceptance, and real-Mac SSH remain post-publication checks.
- The production phone app was not uninstalled, cleared, downgraded, or
  instrumented during development.

## Assets

- `codecks-release.apk`: production-signed, unshrunk Codecks APK.
- `SHA256SUMS.txt`: checksum for the signed APK.

**Full diff:** https://github.com/vaddisrinivas/codecks/compare/v0.1.33...v0.1.35
