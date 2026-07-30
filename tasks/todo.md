# TODO: Product trust and daily-use closure

## Frozen contracts

- [x] Record artifact lifecycle and catalog/placement distinction
- [x] Record automation Validate/Preflight/Live-test distinction
- [x] Record clipboard platform boundary
- [x] Partition files and release/device stop conditions

## V1 Deck + AI

- [ ] Remove redundant Deck pencil; retain explicit bulk customization
- [ ] Empty slot opens catalog with contextual AI creation
- [ ] Generated artifact persists without an empty Deck slot
- [ ] Add slot picker with replace/other Deck/new Deck
- [ ] Add artifact lifecycle states, revision history, real previews
- [ ] Add focused tests and Maestro flow

## V2 Clipboard

- [ ] Remove Mac dependency from local refresh
- [ ] Add terminal Send/Get receipts and actionable failures
- [ ] Separate Manual, Visible live sync, and unavailable background read
- [ ] Add user-driven Share/quick-action path
- [ ] Avoid redundant app clipboard toast and mark sensitive clips
- [ ] Add lifecycle, conflict, reconnect, and platform-restriction tests

## V3 Automations

- [ ] Rename current dry run to Validate
- [ ] Add typed Mac preflight checks
- [ ] Add explicit bounded live test with assertions and cleanup
- [ ] Bind enablement to revision/Mac/capability/time receipt
- [ ] Add trigger simulator and scheduling explanations
- [ ] Add failure, invalidation, WorkManager, and real-Mac contract tests

## V4 DeX + battery + regression

- [ ] Fix clipped secondary-display navigation at 1280x720
- [ ] Test resize, focus, Back, reconnect, and restore behavior
- [ ] Profile HID 15-second keepalive before changing it
- [ ] Record repeatable battery evidence
- [ ] Add cross-vertical isolated-emulator Maestro flows

## Integration and acceptance

- [ ] Review file ownership and evidence for all verticals
- [ ] Integrate on one branch
- [ ] Run Android, Mac-helper, no-shrink, secret, AI, and Maestro gates
- [ ] Run user-driven real-Mac and real Samsung DeX acceptance
- [ ] Make a separate release decision; no worker releases or installs

---

# TODO: v0.1.30 preservation, bug repair, and consolidation

## Preservation

- [x] Commit current four-file deck cleanup
- [x] Safety-commit old `/Projects/codecks` dirty state
- [x] Safety-commit Shortcuts-handler dirty state
- [x] Record all dirty/clean worktrees before removal

## Confirmed bug repairs

- [x] Back from AI/Settings returns inside Codecks
- [x] Offline long-press opens deck management
- [x] Immediate-save deck contract; remove staged Apply layout
- [x] Forget confirmation and undo
- [x] Search-bounded editor catalog
- [x] Clear move semantics and labels
- [x] Template/Favorite isolation
- [x] Landscape accessibility names
- [x] Keyboard and trackpad copy/layout cleanup
- [x] Unified animated Mac visual effects
- [x] Reactive provider failure isolation
- [x] Shortcuts runner/importer/capability hardening

## Verification

- [x] Focused Android tests
- [x] Full release unit tests
- [x] Lint/no-shrink/build
- [x] Mac-helper Swift tests
- [x] Maestro emulator regression suite
- [x] No physical-phone instrumentation

## Consolidation

- [x] Merge verified branch to `main`
- [x] Make `/Projects/codecks` canonical updated main checkout
- [x] Remove only clean/safety-committed obsolete worktrees

---

# Historical TODO: Post-v0.1.21 gap closure

## Current decision

- [x] User approved bounded `v0.1.23` / versionCode 23 release
- [x] Remaining full Reactive Platform phases explicitly deferred
- [x] Audit latest GitHub release and current branch
- [x] Inventory tracked and untracked work
- [x] Compare both governing plans to implementation
- [x] Identify release blockers
- [x] Run no-shrink check
- [x] Run release-unit/lint/check/debug assembly on Java 17
- [x] Finish managed Pixel 6 API 35 debug instrumentation audit
- [x] Preserve the historical no-release audit as a superseded snapshot

## P0 before wider testing

- [ ] G0.1 Freeze ownership groups without dropping work
- [x] G0.2 Correct stale plan/baseline/minified wording
- [x] G0.2 Remove private home paths from public plan docs
- [x] G1.1 Bind confirmation/review to actual resolved action revision
- [x] G1.2 Revalidate Mac/state/capability/registry at execution
- [x] G1.3 Fix Reload semantics and default catalog parity

## README evidence

- [x] G2.1 Recapture named products/SKUs and current prices
- [x] G2.1 Add deterministic arithmetic verification
- [x] G2.2 Add direct README source links
- [ ] G2.2 Re-run forbidden-claim and Markdown checks

## Lockscreen

- [x] G3.1 Check first-unlock state before DataStore/settings access
- [ ] G3.1 Prove no start/register/connect path
- [x] G3.2 Release buttons on every lifecycle/policy-loss path
- [x] G3.2 Remove stale keyguard dispatch window
- [x] G3.3 Add exact-route and secure restricted-Activity AndroidTests
- [ ] G3.3 Add remaining lifecycle/Compose AndroidTests
- [ ] G3.3 Prove forbidden surfaces absent at runtime

## Widget/notification/ledgers

- [x] G4.1 Ship and document pointer-only widget entry
- [x] G4.1 Enforce decision in release-surface validation
- [x] G4.2 Update permission/privacy ledger
- [x] G4.2 Update public-component/release ledger

## Papercuts

- [ ] G5.1 Reproduce real-finger left-click issue
- [ ] G5.1 Fix and add regression test
- [ ] G5.2 Define/test Keyboard multi-touch behavior
- [ ] G5.2 Prove controlled Mac text + exactly one Enter
- [ ] G5.3 Complete TalkBack, focus, target, Back, rotation matrix
- [ ] Close ledger rows only with automated and physical evidence

## Android gates

- [x] G6.1 Secret-surface check
- [x] G6.1 No-shrink check
- [x] G6.1 AI corpus check
- [x] G6.1 Release unit tests
- [x] G6.1 Lint/check/debug build
- [x] G6.1 Mac action verifier
- [x] G6.2 Managed debug instrumentation
- [x] G6.2 Add and verify required lockscreen test classes in XML report

## Approved physical debug

- [ ] G7 Preserve `app.codecks`
- [ ] G7 Use only `app.codecks.debug`
- [ ] G7 Movement/click/drag/right-click/scroll
- [ ] G7 Keyboard send/Enter/clear/failure/multi-touch
- [ ] G7 Connected/disconnected/pre-first-unlock lockscreen matrix
- [ ] G7 Bluetooth-off releases and closes
- [ ] G7 notification/widget/Back/Home/Recents
- [ ] G7 screen cycle/Mac sleep/30-minute idle
- [ ] G7 Restore user's preferred production HID state

## Optional bounded Android release checkpoint

- [x] User accepts Android-slice release before full platform completion
- [x] G8.1 Unique version and detailed notes
- [ ] G8.2 Signed unshrunk artifact
- [ ] G8.2 Candidate/install certificate equality
- [ ] G8.2 Hash preserved before testing
- [ ] G8.3 Exact-artifact SSH/HID/lockscreen proof
- [ ] G8.3 Approved in-place update with data preserved
- [ ] G8.4 Tag/push/GitHub workflow
- [ ] Downloaded GitHub asset hash equals tested hash

## Full Reactive Platform

- [ ] R0 clean baseline, ADRs, threat/privacy/progress
- [x] R1 KMP shared scaffold
- [x] R2 scoped typed protocol models/schema/fixtures
- [x] R3 scoped authenticated replay-safe protocol scaffold
- [ ] R4 native Mac helper
- [x] R5 scoped Android helper-client scaffold
- [ ] R6 unified helper/SSH Mac state
- [ ] R7 complete provider suite
- [ ] R8 executor/receipts/undo
- [ ] R9 production UI/performance
- [ ] R10 real Mac MVP matrix
- [ ] R11 modes/window/cursor/screenshots
- [ ] R12 clipboard/explicit selection
- [ ] R13 profiles/gestures
- [x] R14 iOS device/simulator shared-framework target configuration
- [ ] R15 Smart integration
- [ ] R16 cross-platform hardening

## DeskDock and integrations

- [ ] D0 exact Tasker/NFC verification
- [ ] D1 native score/hysteresis/cooldown/suppress
- [ ] I1 Apple Shortcuts
- [ ] I2 app shortcut importer
- [ ] I3 Spotlight/SFTP
- [ ] I4 supported brightness adapter
- [ ] I5 bounded Accessibility discovery last

## Final

- [ ] Every final-definition-of-done checkbox has evidence
- [ ] Exact tested artifacts equal published artifacts
- [ ] Public docs claim only proven capabilities
- [ ] Release
