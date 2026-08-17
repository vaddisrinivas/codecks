# Codecks autonomous maturity checklist

Canonical plan: `tasks/AUTONOMOUS_MATURITY_PLAN.md`.

Status: implementation and bounded evidence exist through M20, but proof lanes
remain deliberately mixed. `STRUCTURE_VALID` proves receipt shape only; a
historical milestone receipt does not become current-source proof after later
changes. M21 and M23 are deferred. M24 remains `NO_GO` until an exact candidate,
signer continuity, and physical-phone gates exist.

Current Batch 1 truth:

- Phase A/B helper and QR/per-phone pairing: exact Phase B source blobs plus
  source-hashed Swift/Android/shared CPU tests `PASS`; six
  artifact/signing/runtime/phone/human lanes `NOT_RUN` —
  [`pairing-phase-ab-source.json`](test-evidence/pairing-phase-ab-source.json).
- M17 deterministic offline corpus: `PASS`, 120/120 expected outcomes, 112/112
  parser, 120/120 safe semantic validity, zero bypasses in 19 attempts; four
  live providers remain `NOT_RUN` —
  [`autonomous-maturity-m17-ai-creator-v2.json`](test-evidence/autonomous-maturity-m17-ai-creator-v2.json).
- M09 current census: 60,267 public-production LOC; the 10,267-line excess is
  assigned to explicit feature owners, with no code cut for the target —
  [`M09_CURRENT_SOURCE_CENSUS.md`](../docs/architecture/M09_CURRENT_SOURCE_CENSUS.md).

## Phase 0 — Truth

- [x] M00 validate closed baseline/inventory receipt structures and adversarial mutations
- [x] M00 keep `STRUCTURE_VALID`, live attestation, and overall maturity `NOT_ASSESSED` distinct
- [x] M00 bind source, tag, artifact, signer, shrink settings, critical files, and immediate evidence parent without claiming a self-hash
- [x] M00 live-attest clean HEAD, exact evidence diff, required gates, PR ancestry, and public v0.1.37 artifact
- [x] M01 generate feature-level ownership/reachability map — [`CODEBASE_MAP.md`](../docs/architecture/CODEBASE_MAP.md#fourteen-feature-boundaries)
- [x] M01 generate a symlink-safe inventory of tracked Kotlin/Java/Swift sources with repository-derived classification, digests, and LOC — [`autonomous-maturity-source-inventory.json`](test-evidence/autonomous-maturity-source-inventory.json)
- [x] M01 generate sub-1,000-line `docs/architecture/CODEBASE_MAP.md` — [`CODEBASE_MAP.md`](../docs/architecture/CODEBASE_MAP.md)
- [x] M01 document symbol-level reachability dossiers for every current deletion candidate — [reachability classifications](../docs/architecture/CODEBASE_MAP.md#current-reachability-classifications-and-deletion-candidate-dossiers)

## Phase 1 — Simplification

- [x] M02 remove only reachability-proven dead code with rollback receipts
- [x] M02A spike Room, KStateMachine, Compose-Settings, and colorpicker-compose
- [ ] M02A measure net LOC, APK, startup, memory, migration, license, and rollback (runtime/APK lanes remain `NOT_RUN`; all candidates rejected)
- [ ] M02A admit only dependencies meeting explicit thresholds; remove failed spikes (all rejected; standalone evidence spikes retained)
- [x] M03 replace repeated navigation metadata with one typed registry
- [x] M04 reduce MainActivity to less than 400 lines
- [x] M04 prove optional/commercial clients are lazy or absent at startup
- [x] M05 split every production file above 1,000 lines
- [x] M05 preserve Trackpad/HID timing and gesture behavior exactly
- [x] M06 unify typed connection status, diagnosis, repair, and redacted support codes
- [x] M07 unify action review, preflight, execution receipt, retry, and undo contracts
- [x] M07 retain separate HID, SSH, helper, and local-route transports
- [x] M08 standardize bounded atomic persistence and migrations without a mega-store
- [x] M08 prove corruption, process-death, downgrade, rollback, and Keystore-loss behavior
- [ ] M09 publish before/after LOC, dependencies, file size, startup, build, and APK metrics
- [x] M09 reach <=50k production LOC or document feature-owned excess
- [x] M09 keep tests and safety boundaries independent of LOC targets

## Phase 1B — Icons, themes, and content

- [x] M09A define one typed Material 3 Codecks token system
- [ ] M09A apply semantic tokens across every core, lock-screen, overlay, widget, and helper surface (Android core/lock/overlay/widget source and managed proof exist; the native helper uses platform-semantic styling, so the literal cross-platform claim is not closed)
- [x] M09B keep robot-face launcher identity as default
- [x] M09B add robot-grid, pointer-grid, and minimal-green alternate launcher icons
- [x] M09B add adaptive, round, legacy, monochrome, splash, widget, and notification assets
- [x] M09B prove exactly one recoverable launcher component across update/reboot/restore (source/unit proxy; physical update/reboot remains external)
- [x] M09C ship eight offline accessible theme presets
- [x] M09C add full color-scheme picker with live contrast, preview, undo, reset, export/import
- [x] M09C support safe scoped Deck/Trackpad appearance and global semantic fallbacks
- [x] M09D add curated Tabler/Feather/Material/rounded semantic icon packs
- [x] M09D add search, categories, favorites, recents, blank colored buttons, and fallback icons
- [x] M09D expand offline Routine Bank for developer, presentation, meeting, media, focus, and safety
- [x] M09D make every AI-created artifact catalog-first, reviewable, placeable, and recoverable (deterministic CPU proof; live providers remain `NOT_RUN`)
- [ ] M09D pass long-press edit/reassign/delete, reorder, create/test/refine/save/place/undo E2E (full runtime E2E remains `NOT_RUN`)

## Phase 2 — Device and UX proxies

- [x] M10 add managed API 31/32/33/34/35/36 compact-phone, standard-phone, and tablet profiles
- [x] M10 pass the 18-profile startup/recreation/rotation/locale/theme/offline/permission managed proxy matrix (true process kill and old-APK update remain `NOT_RUN`)
- [x] M11 pass freeform, secondary-display, 1280x720, and 1920x1080 desktop proxies
- [x] M11 test resize, rotation, mouse, keyboard, focus, display move, and restore
- [ ] M12 pass current-Mac SSH/helper/clipboard sleep-wake and failure matrix
- [ ] M12 label unavailable Intel/macOS/DeX hardware as external evidence
- [x] M13 pass semantics, TalkBack-action proxies, 200% text, RTL, reduced motion, and contrast in the source-bound managed accessibility matrix
- [x] M13 pass keyboard/D-pad/switch-like traversal proxies and 48dp targets in the source-bound managed accessibility matrix
- [x] M14 run 100 deterministic clean-profile setup and repair journeys (historical source-bound receipt)
- [x] M14 reach >=95% scripted success with zero silent dead ends (100/100 historical deterministic profiles)
- [ ] M14 preserve moderated-human pairing gate as external evidence

## Phase 3 — Reliability

- [x] M15 characterize Clipboard lifecycle, conflicts, privacy events, battery, and reconnect (historical source-bound CPU/managed receipt; later source is not re-attested)
- [x] M15 expose truthful last-sync state and actionable background-policy limits (historical source-bound receipt; later source is not re-attested)
- [ ] M16 run 20 isolated profiles for 168 hours: >=3,360 eligible profile-hours and >=336,000 acknowledged operations
- [ ] M16 achieve >=99.5% automated crash/ANR-free sessions and zero P0/P1
- [ ] M16 report human-tester evidence separately
- [x] M17 version and run >=100 AI prompt corpus
- [x] M17 achieve >=99% parse, >=95% safe semantic validity, zero policy bypass
- [x] M17 prove generated items persist to catalog and remain reviewable/disabled where required
- [x] M18 prove automation preflight, tests, receipts, cancellation, retry, and undo
- [x] M18 adversarial-test quoting, limits, host changes, permissions, and partial failure

## Phase 4 — Operations and release readiness

- [x] M19 ship redacted error/support screen and diagnostic export (historical source-bound CPU receipt; managed/physical/human lanes remain `NOT_RUN`)
- [x] M19 prove the closed CPU export schema excludes secret/content/account/purchase identifiers (historical source-bound receipt; share-picker/runtime lanes remain `NOT_RUN`)
- [x] M20 rehearse rollback, forward-fix, backup recovery, incident intake, and clean verification
- [ ] M20 verify signer continuity without exposing or copying private keys (`NOT_RUN` for real key custody)
- [ ] M21 finalize and test self-service GitHub-only distribution/support path
- [ ] M21 prepare Play listing, Data Safety, policy, and staged-rollout drafts only
- [ ] M21 keep commercial systems dark and Play publication unauthorized
- [x] M22 reconcile production plan from v0.1.36 to actual release/candidate state — [`PRODUCTION_LAUNCH_PLAN.md`](../docs/release/PRODUCTION_LAUNCH_PLAN.md)
- [x] M22 reconcile commercial plan/checklist using evidence-linked completion — [`tasks/todo.md`](todo.md)
- [x] M22 update release ledger, feature guide, README, limitations, and deferred scope — [`CODECKS_RELEASE_LEDGER.md`](../docs/release/CODECKS_RELEASE_LEDGER.md)
- [x] M22 add documentation drift checks to CI — [`verify_release_documentation.py`](../tools/verify_release_documentation.py)
- [ ] M23 build candidate containing dependency PRs #18-#24
- [ ] M23 run all unit/shared/lint/managed/artifact/split/no-shrink/security gates
- [ ] M23 run cold-start, reachability, migration, soak, AI, support, and rollback gates
- [ ] M23 bind exact source/artifacts/signers/checksums/evidence digest
- [ ] M23 prepare notes/version without pushing, tagging, releasing, or installing

## Phase 5 — Verdict

- [x] M24 publish the fail-closed preflight evidence matrix — current verdict `NO_GO`, not release admission
- [x] M24 list exact external hardware, people, Play authority, and key-custody gaps in the preflight/deferred lanes
- [x] M24 stop the candidate when its exact artifact and physical-phone gates are unresolved (`NO_GO`)
- [x] M24 avoid any GA claim; no autonomously hardened release candidate exists until the deferred candidate gates pass

## Permanent gates

- [ ] Never uninstall, clear, downgrade, instrument, or differently sign `app.codecks`; rollback is withdrawal plus forward-fix
- [ ] Never mutate primary-Mac sleep, services, accounts, keys, permissions, or HID state without a disposable target or explicit per-run approval
- [ ] Never enable minification or resource shrinking
- [ ] Never activate sign-in, sync, Billing, premium enforcement, ads, or commercial startup
- [ ] Never let remote, cache, prefs, entitlement, or restored state override production-dark
- [ ] Never sync/export/log credentials, clipboard, hosts, raw commands, or execution proof
- [ ] Never claim emulator/scripted evidence as physical-device or human evidence
- [ ] Never cut code solely to reach a line-count target
