# Codecks autonomous maturity plan

Updated: August 10, 2026

Status: execution-ready plan. Planning only; no production code, release, device,
or commercial activation is authorized by this document.

## Objective

Take the current feature-rich beta to the strongest maturity level an autonomous
agent can honestly prove. Preserve the local-first product, reduce accidental
complexity, close reproducible reliability and UX gaps, automate release and
support evidence, and prepare a post-dependency release candidate.

This plan does **not** claim that emulators are physical devices or that scripted
profiles are human testers. It ends with two results:

1. `AUTONOMOUS_COMPLETE`: every gate executable with repository, emulator, CI,
   current Mac, and approved non-destructive tooling is green.
2. `EXTERNAL_EVIDENCE_REMAINS`: an exact ledger of facts requiring unavailable
   hardware, Play Console authority, or real people.

## Frozen product decisions

- Existing Deck, Trackpad, Keyboard, Clipboard, local automation, SSH, AI
  drafting, and local backup remain available without an account or payment.
- Sign-in, cloud sync, Billing, premium enforcement, ads, and commercial network
  startup remain production-disabled and unreachable.
- `CommercialExecutionPolicy.PRODUCTION_DARK` stays below flags, restored state,
  entitlements, navigation, intents, deep links, and remote input.
- `ossRelease` contains no commercial runtime. `playRelease` is inert.
  `playInternal` remains the isolated `app.codecks.internal` lab.
- No commercial feature is activated by this plan. No provider, price, product,
  retention, region, ad placement, or rollout decision is needed.
- Production package `app.codecks` is never uninstalled, cleared, downgraded,
  instrumented, or replaced with a differently signed APK.
- Release minification and resource shrinking remain disabled.
- `main` after dependency PRs #18-#24 is the implementation baseline. A later
  release candidate must contain those commits; `v0.1.37` remains immutable.

## Evidence vocabulary

Every result must use exactly one label:

| Label | Meaning |
| --- | --- |
| `PASS` | The exact stated automated check ran successfully. |
| `FAIL` | The check ran and violated its contract. |
| `NOT_RUN` | Tool, hardware, authority, or input was unavailable. |
| `AUTONOMOUS_PROXY` | Automated evidence approximates but does not replace a physical or human gate. |
| `EXTERNAL_EVIDENCE_REMAINS` | Only people, unavailable hardware, or store authority can close it. |

No static scan may be reported as runtime proof. No emulator result may be
reported as Samsung, DeX, Intel Mac, Play admission, or human usability proof.

## Scope and measurable targets

### Product maturity targets

- Zero open P0/P1 defects in autonomous lanes.
- All core flows pass process death, rotation, background/foreground, offline,
  permission denial/recovery, and corrupt-state tests.
- Automated sessions are at least 99.5% crash/ANR free over a seven-day soak.
- AI draft benchmark: at least 100 versioned prompts, at least 99% schema parse,
  at least 95% safe semantic validity, and zero policy bypasses.
- Every setup/reconnect failure produces a typed diagnosis and repair action.
- Accessibility automation covers TalkBack semantics, 200% text, keyboard/D-pad
  traversal, contrast, touch targets, reduced motion, and screen-size changes.
- One reproducible evidence bundle binds source SHA, artifacts, checks, logs,
  environment, failures, and `NOT_RUN` lanes.

### Codebase targets

The reproducible baseline contains 54,900 public-production lines, 2,474
internal-lab lines, 5 debug-only lines, 3,699 companion lines, 29,642 test lines,
and 22 Swift build-definition lines. These are physical lines in tracked
Kotlin/Java/Swift files; blank and comment lines are included. The generated
inventory and method live in
`tasks/test-evidence/autonomous-maturity-source-inventory.json` and
`tools/evidence/generate_autonomous_maturity_source_inventory.py`.

The earlier roughly 61,000 figure combined public production, internal lab,
debug, and companions. It is whole-system deployable code, not the public APK
production denominator. Reducing the complete mature product to 1,000 lines is
not credible: it would remove about 98% of behavior and safety boundaries.

Use these targets instead:

- Remove only compiler- and test-proven dead code first: expected 800-1,500
  production lines.
- Reduce repeated orchestration and presentation: expected net 4,000-8,000
  production lines.
- Target at most 50,000 production lines; stretch target 45,000 only when
  behavior and evidence remain equivalent.
- No hand-written production file over 1,000 lines; preferred ceiling 800.
- `MainActivity.kt` below 400 lines and limited to composition/navigation.
- Tests may grow. Do not delete tests or compress code merely to improve LOC.
- Moving code, generating opaque code, or excluding a module does not count as
  reduction. Report public production, internal lab, debug-only, companions,
  tests, generated sources, and build definitions separately by source set and
  language. Public-production LOC is the <=50,000 target denominator.
- Generate a separate codebase map under 1,000 lines for agent comprehension;
  do not confuse that map with the application.

### Third-party reduction policy

GitHub exploration on August 10, 2026 covered 149 broad results and deep scans
of Compose-Settings, colorpicker-compose, KStore, FlowRedux, Workflow Kotlin,
Ktor, and KStateMachine. A dependency is admitted only when a representative
spike proves lower **net** production LOC, equal behavior, acceptable binary and
startup cost, maintained releases, compatible license, and a reversible data
migration.

| Candidate | Decision before implementation | Expected effect |
| --- | --- | --- |
| AndroidX Room | Spike structured Deck, automation, catalog, history, and non-secret connection metadata; keep secrets separate | Potentially remove 1,500-3,000 lines of hand-rolled persistence/migration code |
| KStateMachine | Spike setup/repair and automation execution lifecycle only | Potentially remove 500-1,200 lines while making transitions testable/diagrammable |
| Compose-Settings | Spike standard settings rows/groups/sliders; retain Codecks tokens and special workflows | Potentially remove 300-700 UI lines |
| colorpicker-compose | Adopt if accessibility/performance checks pass | Avoid roughly 400-800 new lines for HSV/alpha/brightness picking |
| Existing compose-icons modules | Reuse through a semantic Codecks icon facade | Avoid custom vector/icon implementation; supports additional curated packs |
| KStore / Multiplatform Settings | Defer unless shared iOS persistence becomes active | Parallel store now would duplicate DataStore/Room and migration policy |
| Compose Destinations / Voyager | Reject for this plan | Codecks already uses official Navigation 3; a second navigation system adds migration and reachability risk |
| Workflow Kotlin / FlowRedux / Circuit-style app frameworks | Reject wholesale migration | Broad architecture rewrite likely increases adapters and LOC; use a narrow state-machine spike instead |
| Ktor client | Reject solely for LOC reduction | Provider payload/policy code remains while unshrunk runtime and dependencies grow |
| Alternate-launcher-icon library | Reject | Android activity aliases are sufficient and keep launcher identity auditable |

The combined realistic **current-code** saving from admitted libraries is about
2,300-4,900 lines. Icon and color libraries mainly prevent future LOC. No
dependency is added merely because its repository is popular.

## Execution model

Use isolated worktrees and integrate only reviewed commits in dependency order.
One integrator owns shared build files, manifests, navigation, and release docs.

| Lane | Owned scope | Must not change |
| --- | --- | --- |
| A — architecture | shell/navigation, shared presentation, persistence utilities, codebase map | feature behavior, commercial activation |
| B — quality matrix | managed devices, test harnesses, accessibility, soak, AI benchmark | production behavior except proven bug fixes |
| C — operations | diagnostics, release evidence, rollback/support drills, distribution docs | signing material, protected phone data |
| D — integration | Gradle/CI, manifests, canonical docs, version candidate | owner policy, minify/shrink settings |

Each lane follows: baseline -> failing proof -> minimal change -> focused tests ->
full owned gates -> independent review -> commit. Integration follows dependency
order and reruns exact combined gates. Ownership overlap is a blocker.

## Phase 0 — Truth baseline

### M00 — Freeze the source and artifact ledger

Dependencies: none.

Actions:

- Record `main` SHA, tags, merged PRs, dirty state, Gradle/JDK/SDK versions, and
  all build variants.
- Map commits in `v0.1.37` versus current `main`; prove PRs #18-#24 are excluded
  from the old tag and included in the candidate baseline.
- Inventory current artifact identities, minimum/target SDK, signer fingerprints
  available without exposing credentials, and no-shrink settings.
- Snapshot existing unit, shared, managed-device, artifact, static, and proof
  harness results. Expired evidence is rerun, not copied forward.

Acceptance:

- Machine-readable baseline receipt committed under `tasks/test-evidence/` and
  accepted by `tools/evidence/validate_autonomous_maturity_evidence.py` against
  the checked-in JSON Schema. Receipt validity is separate from maturity.
- Repository remains clean and `app.codecks` is untouched.

### M01 — Reconcile feature and reachability inventory

Dependencies: M00.

Actions:

- Map all 14 feature boundaries: shell, Deck, Trackpad/HID, lock-screen,
  Keyboard, Clipboard, Rules, SSH setup, AI, Settings/backup/theme/support,
  Smart/context, Reactive/helper, commercial/catalog, and companion modules.
- For every entry point record route, manifest component, composition owner,
  persistence, execution path, tests, build variants, and default exposure.
- Generate an exhaustive inventory of every tracked Kotlin/Java/Swift source
  file with source set, exposure category, feature owner, language, LOC, and
  digest. Feature-level classification does not claim symbol-level completeness.
- For every `DEAD_CANDIDATE`, record definition symbols plus exact text,
  manifest, route, DI, serializer, WorkManager, reflection, JNI, preview, and
  migration reachability. Classify uncertain candidates `UNKNOWN_DYNAMIC`.
- Generate `docs/architecture/CODEBASE_MAP.md` under 1,000 lines.

Acceptance:

- Every tracked source file is inventoried. No deletion candidate relies only
  on text search; manifest, reflection, serialization, JNI, Compose navigation,
  DI, preview, WorkManager history, and migrations are considered.
- Unknown dynamic reachability blocks deletion but not mapping.

## Phase 1 — Safe simplification

### M02 — Delete confirmed dead surfaces

Dependencies: M01.

Initial candidates requiring proof include the unused connection and device
screens/view-models, preview-only code, obsolete settings callbacks, stale
wrappers, and duplicate test fixtures.

Actions:

- Require zero production callers, zero manifest/route/serialization references,
  clean compilation of every variant, and relevant UI-flow parity before removal.
- Delete in small commits grouped by feature boundary.
- Record removed files, lines, replacement path, and verification.

Acceptance:

- Every deletion has a reachability receipt and rollback commit.
- Core and dark-commercial artifact gates remain green.

### M02A — Dependency replacement spikes

Dependencies: M01.

Its relevant decision must finish before M08, M09A, or M09C adopts a dependency.

Actions:

- Implement isolated, non-production spikes for Room, KStateMachine, and
  Compose-Settings against one representative Codecks flow each.
- Measure handwritten LOC removed versus adapters/tests/migrations added;
  compile time; APK method/resource/size delta with shrinking still disabled;
  cold start; memory; accessibility; API 28 compatibility; license/SBOM;
  maintainer/release health; older-reader refusal; and forward rollback behavior.
- Test colorpicker-compose and additional modules from the already-used
  compose-icons project against theme/icon requirements.
- Delete each spike unless it reaches its admission threshold.

Admission thresholds:

- Room: at least 20% net persistence LOC reduction, zero data loss, transactional
  migration/rollback proof, and no secret/backup-policy regression.
- KStateMachine: at least 20% net transition LOC reduction, exhaustive transition
  table tests, no hidden side effects, and serializable stable state where needed.
- Compose-Settings: at least 300 net lines removed with identical Codecks tokens,
  semantics, focus order, 200% text behavior, and screenshot parity.
- colorpicker-compose: no crash/jank, keyboard/TalkBack usable, bounded color
  state, contrast warnings, and less code than a custom accessible picker.

Acceptance:

- A committed decision record states `ADOPT` or `REJECT` with measurements for
  every candidate. Only `ADOPT` dependencies enter production.
- Lockfiles/checksums, license notices, dependency boundaries, and update policy
  are added for admitted libraries.

### M03 — Make routing single-source

Dependencies: M01.

Actions:

- Replace repeated route metadata across `Routes`, `AppNavigator`, `PrimaryTab`,
  and `MainActivity` with one typed route registry.
- Encode visibility, destination class, deep-link eligibility, lock-screen policy,
  and test tags in the descriptor.
- Generate navigation and reachability tests from the registry.

Acceptance:

- Adding or removing a route requires one descriptor change.
- Unknown/restored/forged routes fail closed.
- Public builds contain no lab or commercial route.

### M04 — Thin the composition root

Dependencies: M03.

Actions:

- Move feature construction into explicit feature binders/factories.
- Keep `MainActivity` responsible only for app shell, lifecycle forwarding,
  navigation host, and top-level dependencies.
- Prevent eager construction of SSH, AI, helper, Smart, Reactive, and all
  commercial clients.

Acceptance:

- `MainActivity.kt` below 400 lines.
- Cold-start proof shows no new work, network, worker, alarm, or service.
- Core startup succeeds when every optional binder throws or is absent.

### M05 — Split oversized UI and controllers

Dependencies: M04.

Actions:

- Split `MouseScreen`, `SettingsScreen`, `HomeScreen`, `AutomationsScreen`, and
  their large view-models into state, events, pure reducers, sections, and
  platform adapters.
- Keep raw pointer handling isolated from decorative UI and experimental sensors.
- Preserve exact gesture timing, click suppression, HID dispatch, orientation,
  overlay, lock-screen, and browser-back behavior.

Acceptance:

- No hand-written production file exceeds 1,000 lines.
- Golden UI semantics and gesture engine tests pass before and after refactor.
- Pointer dispatch never waits for SSH, AI, helper, state persistence, or UI.

### M06 — Consolidate connection presentation

Dependencies: M04.

Actions:

- Introduce one typed connection-state presentation model for Bluetooth HID,
  SSH, helper, clipboard bridge, Mac sleep, permission, and host-key failures.
- Reuse status language, repair actions, timestamps, and redacted diagnostics.
- Keep underlying transports independent.

Acceptance:

- Every failure state maps to one cause, visible recovery, and support code.
- No raw exception, host secret, key, clipboard content, or credential is shown
  or exported.

### M07 — Unify action assurance, not transports

Dependencies: M04.

Actions:

- Route Deck, Rules, AI drafts, Reactive actions, and typed SSH catalog actions
  through one authorization/preflight/receipt/undo contract.
- Retain separate HID, local route, SSH, helper, and future provider adapters.
- Remove parallel review/test/enable logic only after migration tests prove
  equivalent safety.

Acceptance:

- No execution path bypasses current revision, review, permission, or policy.
- Every partial failure has typed component receipts and safe retry behavior.
- Raw shell remains governed by `RawCommandPolicy`; imported automation is off.

### M08 — Standardize bounded persistence

Dependencies: M01, M02A.

Actions:

- Provide shared versioning, length/count/depth bounds, checksum, atomic write,
  migration, backup, rollback, corruption quarantine, and redaction utilities.
- Migrate stores incrementally; do not create one mega-database or generic
  reflection serializer.
- Retain special handling for encrypted secrets and non-backup stores.

Acceptance:

- Process-death, truncated-write, corrupt-version, forward migration,
  older-reader refusal, recovery, and Keystore-loss tests pass. Version-rollback
  tests use isolated lab identities/data only; the protected app is never
  downgraded.
- No secret-bearing store becomes cloud/device-transfer eligible.

### M09 — Re-measure complexity

Dependencies: M02, M02A, M03, M04, M05, M06, M07, M08.

Actions:

- Publish before/after production/test/generated LOC, largest files, dependency
  edges, startup owners, duplicate concern count, build time, and APK size.
- Stop reduction if it increases coupling, binary size, cold start, or defects.

Acceptance:

- Production <=50,000 lines or every remaining excess has a feature-owned
  justification. Stretch <=45,000 is optional.
- No line-count achievement weakens tests, readability, SSH, or release safety.

## Phase 1B — Visual identity, icons, themes, and bundled content

### M09A — One Material 3 Codecks design system

Dependencies: M02A, M05.

Actions:

- Define typed color, typography, spacing, shape, border, elevation, grid,
  motion, haptic, opacity, focus, and state-layer tokens.
- Make Deck, Trackpad, Keyboard, Clipboard, Rules, Settings, lock-screen,
  overlay, widgets, dialogs, notifications, and Mac-helper UI consume the same
  semantic tokens.
- Separate semantic intent (`success`, `danger`, `connected`, `selected`) from
  raw color so every theme preserves meaning.

Acceptance:

- No feature screen owns an unrelated hard-coded palette or typography scale.
- Dynamic text, RTL, reduced motion, OLED black, high contrast, and DeX/window
  sizes pass screenshot and semantics checks.

### M09B — Multiple launcher icons

Dependencies: M09A.

Actions:

- Keep the selected robot-face identity as the default.
- Add at least three alternatives: robot grid, pointer grid, and minimal green
  control surface; each includes adaptive foreground/background, round, legacy,
  and Android 13 monochrome assets.
- Implement selection through explicit disabled-by-default activity aliases,
  with one valid launcher component at all times and a safe default recovery.
- Apply the active identity consistently to splash, widget preview, notification,
  about screen, and Mac helper where the platform permits.
- Record asset source, license, generation inputs, human-readable meaning, and
  trademark/copyright review. Do not ship Apple/OpenAI/third-party marks.

Acceptance:

- Icon choice survives update, reboot, restore, process death, and launcher
  refresh without losing the app entry or creating duplicates.
- Mask/crop/alpha/contrast tests pass across circle, squircle, rounded-square,
  teardrop, monochrome, light, and dark launchers.

### M09C — Theme library and full color-scheme editor

Dependencies: M02A, M09A.

Actions:

- Ship offline presets: Codecks Green, OLED, Light Glass, Aurora, Cyber, Warm,
  Monochrome, and High Contrast.
- Add a full scheme editor for primary, secondary, tertiary, surface, background,
  border, success, warning, danger, button, grid, glow, and opacity values.
- Generate accessible tonal roles from user seed colors; show live contrast
  results and block unreadable critical-state combinations.
- Support preview without commit, atomic apply, undo, reset, export/import,
  duplicate-as-new, stable IDs, missing-theme fallback, and transactional restore.
- Permit optional scoped Deck/Trackpad appearance while keeping navigation,
  dialogs, and destructive states semantically safe.

Acceptance:

- Every preset and custom scheme passes contrast, 200% text, reduced motion,
  orientation, overlay, lock-screen, and DeX proxy tests.
- A failed/corrupt import rolls back; unknown theme IDs fall back visibly.
- Theme snapshots contain typed bounded values only—no arbitrary resources,
  paths, commands, URLs, or executable content.

### M09D — Deck/button icon packs and useful bundled content

Dependencies: M07, M09A.

Actions:

- Add curated semantic packs using maintained Compose icon artifacts already in
  the dependency family: Tabler, Feather, Material Symbols, and one friendly
  rounded/robotic pack if license and binary measurements pass.
- Map actions to semantic icon IDs rather than library class names so users can
  switch packs without changing behavior.
- Add search, categories, favorites, recent icons, blank colored button, custom
  color, preview, missing-icon fallback, and TalkBack labels.
- Expand the offline Routine Bank with useful developer, presentation, meeting,
  media, focus, browser, Finder, accessibility, and safety packs. Installation
  always previews changes, detects conflicts, and can roll back.
- AI-created buttons/decks/automations/themes save to the catalog first, remain
  reviewable, and can then be placed without requiring an empty Deck.

Acceptance:

- Switching icon packs changes appearance only, never action identity.
- No copyrighted logo enters a generic pack without an approved license.
- Empty colored buttons, catalog delete, long-press edit/reassign/delete,
  reorder, AI create, test, refine, save, place, undo, and restore pass E2E.
- Added packs are offline, accountless, free, deterministic, and commercial-dark.

## Phase 2 — Autonomous device and UX proof

### M10 — Expand managed Android matrix

Dependencies: M00.

Actions:

- Add reproducible managed profiles for API 31, 32, 33, 34, 35, and 36; compact phone,
  standard phone, tablet, portrait/landscape, and large-window configurations.
- Exercise clean install, update migration, process death, rotation, locale,
  dark/light themes, offline mode, Bluetooth/permission toggles, and background
  restrictions using debug or isolated lab identities only.
- Shard long tests and retain device/system images by exact version.

Acceptance:

- Core smoke, data migration, no-crash, and artifact checks pass on every profile.
- Results are labeled `AUTONOMOUS_PROXY`, never Samsung/vendor physical proof.

### M11 — DeX and large-screen proxy matrix

Dependencies: M10.

Actions:

- Use freeform windowing, desktop mode where available, secondary displays,
  1280x720 and 1920x1080 sizes, mouse/keyboard input, resize, rotation, focus
  transfer, backgrounding, and window restore.
- Assert Deck grid, trackpad hit regions, overlay bounds, dialogs, navigation,
  and persisted state across display moves.

Acceptance:

- No clipped controls, unusable focus, wrong display launch, lost state, or crash.
- Real Samsung DeX remains `EXTERNAL_EVIDENCE_REMAINS` unless hardware appears.

### M12 — Current-Mac automation matrix

Dependencies: M00.

Actions:

- Automate read-only SSH/helper health, timeout, bounded command, network-failure
  simulation, clipboard bridge, and typed receipt checks against the available
  Mac without changing its sleep, service, account, authorization, key, or HID
  state.
- Host sleep/wake, service restart, test-account creation/removal, host-key
  replacement, permission changes, and equivalent primary-Mac mutations run
  only on a disposable target or after explicit per-run approval in the current
  conversation. Otherwise each lane is `NOT_RUN`.
- Physical Bluetooth HID verification is external evidence. Automation never
  manipulates the protected release app or its existing Mac pairing.

Acceptance:

- Apple Silicon/current-macOS results are exact and reproducible.
- Intel and additional macOS versions remain `EXTERNAL_EVIDENCE_REMAINS` when
  unavailable.

### M13 — Accessibility automation

Dependencies: M05, M09A, M09B, M09C, M09D, M10.

Actions:

- Enforce unique labels, roles, state descriptions, headings, traversal order,
  live-region restraint, 48dp targets, contrast, and non-color-only status.
- Test 100%, 130%, 150%, and 200% font scale; display scaling; RTL; reduced
  motion; keyboard/D-pad/switch-like traversal; TalkBack focus and actions.
- Add screenshots and semantics dumps for all primary screens, dialogs, launcher
  identities, icon packs, theme presets, and custom schemes.

Acceptance:

- Zero unlabeled actionable nodes, focus traps, clipped critical text, or
  inaccessible destructive confirmations.
- Human comprehension remains `EXTERNAL_EVIDENCE_REMAINS`.

### M14 — First-run and repair benchmark

Dependencies: M06, M10, M12.

Actions:

- Create 100 clean-profile scripted journeys across HID pairing, SSH setup,
  permission denial, wrong host, sleeping Mac, host-key mismatch, lost key,
  Bluetooth off, network loss, and recovery.
- Measure completion, time, retries, dead ends, error comprehension proxy, and
  whether recovery returns to the intended feature.
- Fail any flow needing developer tools, hidden settings, or undocumented SSH.

Acceptance:

- At least 95% scripted success and zero unrecoverable silent failure.
- The original 80% moderated-human criterion remains external; automated success
  is not relabeled as human pairing success.

## Phase 3 — Reliability and content quality

### M15 — Clipboard/background/battery characterization

Dependencies: M10, M12.

Actions:

- Test foreground/background, screen off/on, phone lock, Mac sleep/wake,
  reconnect, Samsung-style clipboard privacy events where emulatable, duplicate
  suppression, conflict ordering, large payloads, and battery restrictions.
- Capture last-sync truth, failures, worker/service state, wake locks, CPU,
  network bytes, and battery estimates without clipboard contents.

Acceptance:

- No phantom success, sync loop, silent stale state, clipboard leak, or runaway
  background work. UI states the actual operating limitation.

### M16 — Seven-day autonomous soak

Dependencies: M10, M11, M12, M13, M14, M15.

Actions:

- Run at least 20 isolated scripted profiles continuously for 168 hours across
  Deck, Trackpad, Keyboard, Clipboard, Rules, SSH failures, rotation, process
  death, and idle/reconnect cycles.
- Capture session counts, crashes, ANRs, hangs, memory slope, reconnect latency,
  battery proxy, operation latency, and failure clustering.
- Automatically file a local failure packet with seed, device, logs, screenshot,
  state transition, and minimal reproduction.

Acceptance:

- A session is one profile-hour after successful harness admission. It must last
  at least 50 minutes, execute at least 100 acknowledged user-level operations,
  and cover at least five named core-flow categories. A harness outage is
  excluded only when no app process was launched and the exclusion is recorded;
  every admitted session remains in the denominator. Minimum evidence is 3,360
  eligible sessions and 336,000 acknowledged operations. Crash/ANR-free rate is
  `(eligible sessions - sessions with >=1 crash or ANR) / eligible sessions`.
- At least 99.5% automated sessions crash/ANR free, zero P0/P1, no unbounded
  resource growth, and all failures classified.
- This is `AUTONOMOUS_PROXY`; it does not equal 20 human testers.

### M17 — AI 100-prompt reliability benchmark

Dependencies: M07.

Actions:

- Version a privacy-safe corpus of at least 100 intents covering buttons, decks,
  automations, themes, catalog insertion, refinement, malformed output,
  adversarial commands, prompt injection, oversized content, and provider errors.
- Run deterministic fixtures for all supported provider response formats.
- When credentials exist through `agent-env`, run bounded live OpenAI-compatible,
  Anthropic, and OpenRouter/Azure-compatible samples without logging secrets.
- Validate schema, semantic intent, catalog persistence, disabled-by-default
  automation, test/refine/regenerate lifecycle, retry cost, and policy denial.

Acceptance:

- >=99% parse, >=95% safe semantic validity, zero bypass, zero lost generated
  artifact, and deterministic actionable error for every failure.
- Live-provider absence is `NOT_RUN`, not silently replaced by mocks.

### M18 — Automation proof and recovery

Dependencies: M07, M12, M17.

Actions:

- Expand permission/host/tool preflight, generated test cases, revision binding,
  partial-failure receipts, cleanup limits, cancellation, retry, and undo.
- Run adversarial quoting, shell metacharacter, timeout, output-bound, changed
  host key, unavailable app, and permission-loss suites.

Acceptance:

- No automation can become enabled without current successful evidence.
- Test, refine, delete, regenerate, catalog-save, and deck-place flows preserve
  one canonical draft and never spend credentials without an explicit action.

## Phase 4 — Operations and distribution readiness

### M19 — Diagnostics and support package

Dependencies: M06, M15, M16, M17, M18.

Actions:

- Add one error/support surface with redacted health, connection state, build,
  last operation receipts, permissions, battery policy, and export.
- Exclude credentials, hosts/IPs/usernames, clipboard, prompts/responses, tokens,
  account IDs, purchase data, snapshot contents, raw output, and device serials.
- Provide stable support codes linked to public repair documentation.

Acceptance:

- Secret canaries and a deterministic, fail-closed export-schema validator pass.
  The validator rejects unknown keys, wrong types, unbounded collections,
  unapproved payload classes, and content-bearing values; no manual allowlist
  can close this gate.
- A clean machine can diagnose every injected failure using only the package.

### M20 — Rollback, key, and incident rehearsal

Dependencies: M00, M19.

Actions:

- Rehearse bad-release detection, release withdrawal instructions, forward-fix,
  previous-artifact verification, backup export/import, schema rollback refusal,
  compromised-token response, SSH host-key incident, and support escalation.
- Verify CI signing continuity using fingerprints and a disposable rehearsal;
  never copy or print real private keys.
- Check release checksum and source correspondence from a clean environment.
- Never install an older APK over `app.codecks`. Rollback means withdrawal plus
  forward-fix; previous artifacts are verified offline only.

Acceptance:

- Timestamped drill receipts, recovery time, failure injection, and follow-up
  actions exist. Offline custody of real signing keys remains external if not
  available to automation.

### M21 — GitHub-first distribution package

Dependencies: M19, M20.

Autonomous default: finalize a GitHub-only support strategy now. Play readiness
may be prepared, but no account/commercial activation or store publication occurs.

Actions:

- Reconcile install/update/rollback/support docs, checksum verification, minimum
  Android/macOS support, privacy, security, issue intake, and known limitations.
- Test clean-machine download, signature/checksum, in-place update compatibility,
  update checker behavior, broken-network recovery, and old-release rollback
  guidance.
- Produce Play listing copy, screenshots plan, Data Safety draft, privacy and
  account-deletion declarations as reviewable artifacts only.

Acceptance:

- GitHub path is fully self-service and tested.
- Play Console submission, Data Safety declaration acceptance, and staged rollout
  remain `EXTERNAL_EVIDENCE_REMAINS` until explicitly authorized.

### M22 — Documentation reconciliation

Dependencies: M09, M09B, M09C, M09D, M11, M13, M14, M16, M18, M21.

Actions:

- Update `PRODUCTION_LAUNCH_PLAN.md` from stale `v0.1.36` language to the actual
  release/candidate baseline.
- Reconcile `tasks/plan.md` and `tasks/todo.md` against implemented commercial
  commits. Mark only evidence-backed items complete; archive superseded work.
- Update release ledger, feature inventory, README maturity claim, build matrix,
  commercial-dark status, deferred Reactive scope, limitations, and support docs.
- Link each completed checkbox to a test, receipt, commit, or artifact digest.

Acceptance:

- No contradictory version, feature, activation, or maturity claim remains.
- Documentation checks fail CI on future drift.

### M23 — Post-dependency release candidate

Dependencies: M09, M09B, M09C, M09D, M11, M13, M14, M16, M18, M21, M22.

Actions:

- Build the candidate from current `main` including PRs #18-#24.
- Run OSS/Play/playInternal units, shared JVM/iOS compilation tests, lint,
  managed-device matrix, commercial boundaries, exact APK/AAB split inspection,
  no-shrink, cold-start, reachability, migration, support, soak, and AI gates.
- Bind source SHA, dependency lock state, signer expectations, APK/AAB checksums,
  manifests, DEX scans, and evidence bundle digest.
- Prepare release notes and draft tag/version only after every autonomous gate
  passes. Do not overwrite `v0.1.37`.

Acceptance:

- Exact candidate is reproducible and has zero open autonomous P0/P1.
- Publishing, pushing a tag, GitHub release creation, Play upload, and phone
  installation occur only under a later explicit execution request.

## Phase 5 — Final maturity verdict

### M24 — Evidence-based decision

Dependencies: M23.

Produce a final table for every original GA gate:

| Original gate | Autonomous closure |
| --- | --- |
| Android 12-16 matrix | Managed-emulator proxy; physical Samsung/non-Samsung/tablet remains external. |
| Real Mac and DeX | Current available Mac may pass exactly; Intel/other macOS/real DeX remain external. |
| Accessibility | Automated semantics/input/layout pass; human assistive-tech comprehension remains external. |
| 20 testers / seven days | Seven-day 20-profile automation can pass; 20 humans remains external. |
| Pairing without developer help | 100 scripted clean-profile journeys can pass; moderated-human rate remains external. |
| Store readiness | GitHub-only strategy can close; Play submission/approval remains external. |
| AI 100 prompts | Fully autonomous when fixtures and permitted provider credentials are available. |
| Rollback/key/support | Drills can pass; real offline-key custody or organizational response remains external. |
| Documentation | Fully autonomous. |
| Dependencies missing from v0.1.37 | Closed by a new candidate; historical v0.1.37 remains unchanged. |

Final terminology:

- Call it `autonomously hardened release candidate` when M00-M23 pass.
- Call it `general-consumer GA` only after the remaining external evidence is
  actually supplied, or after product policy explicitly chooses a GitHub-only
  scope whose published support promise excludes those unavailable matrices.

## Global stop conditions

Stop integration immediately for:

- protected-package signer mismatch, uninstall/data-clear/downgrade request, or
  migration that risks user data;
- SSH regression, changed host verification, HID latency regression, or release
  minification/resource shrinking;
- commercial UI, SDK construction, provider, worker, alarm, route, or network
  activity in public production;
- secret, clipboard, host, raw-command, execution-proof, auth, or purchase data
  entering backup, diagnostics, logs, fixtures, or source;
- evidence reported above its actual level;
- code reduction that removes behavior, tests, security boundaries, or readable
  ownership merely to hit a line count.

## Autonomous completion order

### Minimum schedule

The 168-hour soak fixes the critical path at a minimum of seven elapsed days.
Other work overlaps it:

| Window | Work |
| --- | --- |
| Day 0-1 | M00-M01 baseline, inventory, managed-image preparation |
| Day 1-3 | M02-M08 architecture, dependency spikes, and safe simplification in parallel |
| Day 2-5 | M09A-M09D visual system, launcher icons, themes, and content |
| Day 1-4 | M10-M15 device, desktop, Mac, accessibility, and setup proxies |
| Day 3-10 | M16 uninterrupted seven-day soak; fixes restart affected evidence |
| Day 3-6 | M17-M18 AI and automation assurance |
| Day 5-8 | M19-M22 diagnostics, drills, distribution, documentation |
| After soak | M23 exact candidate gates, then M24 verdict |

This is a dependency schedule, not a promise. Any P0/P1, migration risk, SSH/HID
regression, or evidence corruption extends it and reruns the affected downstream
gates.

Canonical direct dependency graph:

| Milestone | Direct prerequisites |
| --- | --- |
| M00 | none |
| M01 | M00 |
| M02 | M01 |
| M02A | M01 |
| M03 | M01 |
| M04 | M03 |
| M05 | M04 |
| M06 | M04 |
| M07 | M04 |
| M08 | M01, M02A |
| M09 | M02, M02A, M03, M04, M05, M06, M07, M08 |
| M09A | M02A, M05 |
| M09B | M09A |
| M09C | M02A, M09A |
| M09D | M07, M09A |
| M10 | M00 |
| M11 | M10 |
| M12 | M00 |
| M13 | M05, M09A, M09B, M09C, M09D, M10 |
| M14 | M06, M10, M12 |
| M15 | M10, M12 |
| M16 | M10, M11, M12, M13, M14, M15 |
| M17 | M07 |
| M18 | M07, M12, M17 |
| M19 | M06, M15, M16, M17, M18 |
| M20 | M00, M19 |
| M21 | M19, M20 |
| M22 | M09, M09B, M09C, M09D, M11, M13, M14, M16, M18, M21 |
| M23 | M09, M09B, M09C, M09D, M11, M13, M14, M16, M18, M21, M22 |
| M24 | M23 |

Each milestone's `Dependencies` line is normative and must match this table.

Maximum parallelism is four lanes. Long emulator, soak, and AI jobs run in the
background while architecture work proceeds, but no lane may edit another
lane's owned files.
