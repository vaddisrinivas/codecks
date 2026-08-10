# Codecks autonomous maturity checklist

Canonical plan: `tasks/AUTONOMOUS_MATURITY_PLAN.md`.

Status: planned; nothing below is complete merely because an older test exists.
Fresh evidence must bind the current candidate SHA.

## Phase 0 — Truth

- [x] M00 validate the baseline receipt against its formal schema and deterministic validator
- [x] M00 keep receipt-validation `PASS` separate from overall maturity `NOT_RUN`
- [x] M00 record source, PR, tag, variant, SDK, signer, shrink, and evidence baseline — [`autonomous-maturity-m00-baseline.json`](test-evidence/autonomous-maturity-m00-baseline.json)
- [x] M00 prove dependency PRs #18-#24 are on candidate main but outside v0.1.37 — [`source.dependency_pr_ancestry`](test-evidence/autonomous-maturity-m00-baseline.json)
- [x] M01 generate feature-level ownership/reachability map — [`CODEBASE_MAP.md`](../docs/architecture/CODEBASE_MAP.md#fourteen-feature-boundaries)
- [x] M01 generate exhaustive tracked-source file inventory with reproducible per-source-set LOC — [`autonomous-maturity-source-inventory.json`](test-evidence/autonomous-maturity-source-inventory.json)
- [x] M01 generate sub-1,000-line `docs/architecture/CODEBASE_MAP.md` — [`CODEBASE_MAP.md`](../docs/architecture/CODEBASE_MAP.md)
- [x] M01 document symbol-level reachability dossiers for every current deletion candidate — [reachability classifications](../docs/architecture/CODEBASE_MAP.md#current-reachability-classifications-and-deletion-candidate-dossiers)

## Phase 1 — Simplification

- [ ] M02 remove only reachability-proven dead code with rollback receipts
- [ ] M02A spike Room, KStateMachine, Compose-Settings, and colorpicker-compose
- [ ] M02A measure net LOC, APK, startup, memory, migration, license, and rollback
- [ ] M02A admit only dependencies meeting explicit thresholds; remove failed spikes
- [ ] M03 replace repeated navigation metadata with one typed registry
- [ ] M04 reduce MainActivity to less than 400 lines
- [ ] M04 prove optional/commercial clients are lazy or absent at startup
- [ ] M05 split every production file above 1,000 lines
- [ ] M05 preserve Trackpad/HID timing and gesture behavior exactly
- [ ] M06 unify typed connection status, diagnosis, repair, and redacted support codes
- [ ] M07 unify action review, preflight, execution receipt, retry, and undo contracts
- [ ] M07 retain separate HID, SSH, helper, and local-route transports
- [ ] M08 standardize bounded atomic persistence and migrations without a mega-store
- [ ] M08 prove corruption, process-death, downgrade, rollback, and Keystore-loss behavior
- [ ] M09 publish before/after LOC, dependencies, file size, startup, build, and APK metrics
- [ ] M09 reach <=50k production LOC or document feature-owned excess
- [ ] M09 keep tests and safety boundaries independent of LOC targets

## Phase 1B — Icons, themes, and content

- [ ] M09A define one typed Material 3 Codecks token system
- [ ] M09A apply semantic tokens across every core, lock-screen, overlay, widget, and helper surface
- [ ] M09B keep robot-face launcher identity as default
- [ ] M09B add robot-grid, pointer-grid, and minimal-green alternate launcher icons
- [ ] M09B add adaptive, round, legacy, monochrome, splash, widget, and notification assets
- [ ] M09B prove exactly one recoverable launcher component across update/reboot/restore
- [ ] M09C ship eight offline accessible theme presets
- [ ] M09C add full color-scheme picker with live contrast, preview, undo, reset, export/import
- [ ] M09C support safe scoped Deck/Trackpad appearance and global semantic fallbacks
- [ ] M09D add curated Tabler/Feather/Material/rounded semantic icon packs
- [ ] M09D add search, categories, favorites, recents, blank colored buttons, and fallback icons
- [ ] M09D expand offline Routine Bank for developer, presentation, meeting, media, focus, and safety
- [ ] M09D make every AI-created artifact catalog-first, reviewable, placeable, and recoverable
- [ ] M09D pass long-press edit/reassign/delete, reorder, create/test/refine/save/place/undo E2E

## Phase 2 — Device and UX proxies

- [ ] M10 add managed API 31/32/33/34/35/36 phone and tablet profiles
- [ ] M10 pass clean/update/process-death/rotation/locale/theme/offline/permission matrix
- [ ] M11 pass freeform, secondary-display, 1280x720, and 1920x1080 desktop proxies
- [ ] M11 test resize, rotation, mouse, keyboard, focus, display move, and restore
- [ ] M12 pass current-Mac SSH/helper/clipboard sleep-wake and failure matrix
- [ ] M12 label unavailable Intel/macOS/DeX hardware as external evidence
- [ ] M13 pass semantics, TalkBack actions, 200% text, RTL, reduced motion, and contrast
- [ ] M13 pass keyboard/D-pad/switch-like traversal and 48dp targets
- [ ] M14 run 100 clean-profile setup and repair journeys
- [ ] M14 reach >=95% scripted success with zero silent dead ends
- [ ] M14 preserve moderated-human pairing gate as external evidence

## Phase 3 — Reliability

- [ ] M15 characterize Clipboard lifecycle, conflicts, privacy events, battery, and reconnect
- [ ] M15 expose truthful last-sync state and actionable background-policy limits
- [ ] M16 run 20 isolated profiles for 168 hours: >=3,360 eligible profile-hours and >=336,000 acknowledged operations
- [ ] M16 achieve >=99.5% automated crash/ANR-free sessions and zero P0/P1
- [ ] M16 report human-tester evidence separately
- [ ] M17 version and run >=100 AI prompt corpus
- [ ] M17 achieve >=99% parse, >=95% safe semantic validity, zero policy bypass
- [ ] M17 prove generated items persist to catalog and remain reviewable/disabled where required
- [ ] M18 prove automation preflight, tests, receipts, cancellation, retry, and undo
- [ ] M18 adversarial-test quoting, limits, host changes, permissions, and partial failure

## Phase 4 — Operations and release readiness

- [ ] M19 ship redacted error/support screen and diagnostic export
- [ ] M19 prove diagnostics exclude every secret/content/account/purchase identifier
- [ ] M20 rehearse rollback, forward-fix, backup recovery, incident intake, and clean verification
- [ ] M20 verify signer continuity without exposing or copying private keys
- [ ] M21 finalize and test self-service GitHub-only distribution/support path
- [ ] M21 prepare Play listing, Data Safety, policy, and staged-rollout drafts only
- [ ] M21 keep commercial systems dark and Play publication unauthorized
- [ ] M22 reconcile production plan from v0.1.36 to actual release/candidate state
- [ ] M22 reconcile commercial plan/checklist using evidence-linked completion
- [ ] M22 update release ledger, feature guide, README, limitations, and deferred scope
- [ ] M22 add documentation drift checks to CI
- [ ] M23 build candidate containing dependency PRs #18-#24
- [ ] M23 run all unit/shared/lint/managed/artifact/split/no-shrink/security gates
- [ ] M23 run cold-start, reachability, migration, soak, AI, support, and rollback gates
- [ ] M23 bind exact source/artifacts/signers/checksums/evidence digest
- [ ] M23 prepare notes/version without pushing, tagging, releasing, or installing

## Phase 5 — Verdict

- [ ] M24 publish `PASS` / `FAIL` / `NOT_RUN` / `AUTONOMOUS_PROXY` evidence matrix
- [ ] M24 list exact external hardware, people, Play authority, and key-custody gaps
- [ ] M24 report zero open autonomous P0/P1 or stop the candidate
- [ ] M24 use `autonomously hardened release candidate` unless true GA evidence exists

## Permanent gates

- [ ] Never uninstall, clear, downgrade, instrument, or differently sign `app.codecks`; rollback is withdrawal plus forward-fix
- [ ] Never mutate primary-Mac sleep, services, accounts, keys, permissions, or HID state without a disposable target or explicit per-run approval
- [ ] Never enable minification or resource shrinking
- [ ] Never activate sign-in, sync, Billing, premium enforcement, ads, or commercial startup
- [ ] Never let remote, cache, prefs, entitlement, or restored state override production-dark
- [ ] Never sync/export/log credentials, clipboard, hosts, raw commands, or execution proof
- [ ] Never claim emulator/scripted evidence as physical-device or human evidence
- [ ] Never cut code solely to reach a line-count target
