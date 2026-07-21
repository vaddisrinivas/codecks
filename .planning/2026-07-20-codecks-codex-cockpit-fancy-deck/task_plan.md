# Codecks Codex Cockpit + Fancy Deck Plan

Date: 2026-07-20

## Goal

Turn Codecks v2 into a phone/tablet/DeX control surface for Codex-style agent work, with a useful task cockpit and a playful deck layer: empty fancy buttons, emoji buttons, confetti, celebration effects, richer themes, and utility/productivity actions.

## Current baseline

- Root Codecks v0.1.4 is released and published from local work.
- Codecks v2 local foundation is checkpointed on `main` as WIP.
- Nested Android v2 build was repaired by parking AppFunctions and removing unresolved dependencies.
- AppFunctions source is preserved at `.planning/2026-07-20-codecks-v2-appfunctions-parked/CodecksAppFunctionService.kt.disabled`.
- Current v2 should be treated as buildable local foundation, not a finished physical-device/Mac-proven release.

## Product shape

Codecks becomes a cockpit for steering agent work from the S23 Ultra and larger Android surfaces:

- See every running/recent Codex task as big live cards.
- Tap into a task, continue it, or send a canned follow-up.
- See queue, elapsed time, status, unread/approval state, release/CI state, and usage hints where available.
- Use empty beautiful buttons for user-defined workflows.
- Make buttons feel alive with emoji, animation, color, glow, and celebrations.
- Keep everything local-first, private, and non-fragile by default.

## External signals already gathered

- ThreadDeck Reddit post: Stream Deck Neo dashboard for Codex with task monitoring, effort/fast toggles, push-to-talk, queue status, completion alerts, recent tasks, elapsed time, quota, unread alerts, local-first behavior, and no telemetry.
- AgentDeck GitHub project: multi-surface agent control via one daemon, Stream Deck/tablets/e-ink/ESP32/TUI, where each key reflects a live session state.
- OpenDeck Codex dashboard project: single-button status surface with local polling, clear working/complete/offline states, and no prompt/source reading.
- GitHub trending utility/productivity signals: voice interfaces, memory/cognitive layers, CLI agent tools, and automation/SEO utilities.
- 2026 agent-adoption research signal: visible oversight dashboards and peer-visible agent usage matter more than raw hidden automation.

## Principles

- Local-first: no telemetry, no prompt scraping by default, no server requirement.
- Useful before clever: a mocked dashboard and fancy deck should work before live Codex bridge complexity.
- Glanceable: S23 Ultra OLED should look great with bright localized button states, but Android apps control pixels through UI color/HDR surfaces, not raw per-pixel nit APIs.
- Guarded commands: approve, stop, destructive release actions, and credentialed actions require explicit user confirmation.
- Modular: fancy deck, Codex task model, bridge protocol, GitHub/release deck, and voice controls should be separable.
- Honest proof: local build is not device proof; mocked data is not live Codex integration; release is separate from WIP checkpoint.

## Non-goals for the first build slice

- Do not revive AppFunctions yet.
- Do not ship a new public APK until physical-device and Mac bridge proof exist.
- Do not read task prompt/source content by default.
- Do not depend on undocumented Codex metadata as the only path without a fallback.
- Do not build cloud sync or remote account infrastructure.

## Feature pillars

### 1. Codex Cockpit

Purpose: make running agent work visible and steerable.

Core UI:

- `TaskGrid`: large live cards for Codex tasks.
- `TaskDetail`: selected task status, elapsed time, branch, repo, latest output summary, blockers, and action buttons.
- `QueueStrip`: queued/running/needs-attention/done counters.
- `AttentionRail`: approvals, unread updates, failed checks, stuck tasks.
- `ModeChip`: effort/fast/approval policy indicators when known.
- `QuotaMeter`: usage/quota approximation when available; hide gracefully when unavailable.

Task states:

- `Idle`
- `Queued`
- `Running`
- `NeedsAttention`
- `Blocked`
- `Succeeded`
- `Failed`
- `Released`
- `Offline`

Actions:

- Open task in Codex app where possible.
- Continue task.
- Send canned follow-up.
- Mark watched/unwatched locally.
- Stop task after confirmation.
- Approve/reject only through guarded flows.

### 2. Fancy Deck Buttons

Purpose: make Codecks expressive, tactile, and fun even before every button is wired.

Button types:

- `EmptyFancyButton`: beautiful placeholder with no action yet.
- `EmojiButton`: large emoji/icon plus label and optional effect.
- `TaskButton`: live Codex task state button.
- `CommandButton`: local command or safe script launcher.
- `ReleaseButton`: CI/tag/APK/release action button.
- `SceneButton`: changes theme/effects/mode.
- `MacroButton`: sequence of safe actions.

Button anatomy:

- Big symbol or emoji.
- Short label.
- Optional sublabel/status.
- Color mood.
- Edge glow.
- Animated background.
- Press/hold/complete effects.
- Safety affordance for dangerous actions.

### 3. Effects and Celebrations

Purpose: make completion and attention feel satisfying without turning the app into soup.

Effects:

- `ConfettiBurst`: on task complete, release ready, successful build.
- `EmojiRain`: customizable emoji shower for success/fun buttons.
- `SparkTrail`: pointer/finger trail over the deck grid.
- `ShockwavePulse`: button press ripple across nearby keys.
- `FireworkGrid`: multiple buttons pop in sequence.
- `NeonSweep`: animated border pass for running tasks.
- `DangerPulse`: red/orange pulse for approvals/failures.
- `CalmGlow`: low-motion ambient breathing state.

Controls:

- Effects can be disabled.
- Reduced-motion mode must tone effects down.
- Battery-saver mode should reduce animation frequency.
- Effects should have max particle counts and bounded lifetimes.

### 4. Themes

Purpose: give the user several strong visual worlds, not one bland Material skin.

Initial theme presets:

- `Terminal Neon`: black glass, phosphor green, amber status, scanline texture.
- `Arcade Glass`: saturated tiles, beveled glow, playful completion bursts.
- `Studio Console`: broadcast/control-room look with meters, warning strips, and tactile panels.
- `Emoji Carnival`: bright emoji-forward mode with stickers, confetti, and rounded soft keys.
- `Focus Minimal`: calm white/cream surface with precise colored status lights.
- `Warning Ops`: orange/charcoal incident-control aesthetic.
- `Aurora Pixel`: S23 Ultra friendly OLED gradient, cyan/green glows, starry particle field.
- `Paper Desk`: notebook/index-card look for lower-energy planning sessions.

Theme model:

- Background treatment.
- Card surface.
- Primary/accent/status colors.
- Typography tokens.
- Key shape.
- Glow/elevation style.
- Default effect pack.

### 5. Local Codex Bridge

Purpose: feed live task status into Android without compromising privacy.

Preferred bridge shape:

- Mac-side local reader/daemon.
- Android connects over local network or ADB-assisted development transport.
- Bridge emits a small sanitized task snapshot.
- Prompt/source/content are excluded unless explicitly enabled.
- Use a stable internal protocol under `protocol/`.

Snapshot fields:

- Task id.
- Title.
- Repo.
- Branch.
- Status.
- Elapsed time.
- Last update time.
- Needs attention flag.
- Release/CI markers where known.
- Latest safe summary if available.

Bridge sources, in order:

- Supported Codex thread connector if available.
- Local Codex metadata/session files if stable enough.
- Git/GitHub/CI state for release deck.
- Manual/mock data fallback for UI development.

### 6. GitHub / Release Utility Deck

Purpose: make release work visible and less error-prone.

Buttons:

- Current branch status.
- Dirty worktree alert.
- CI status.
- Latest tag.
- Latest release.
- APK asset presence.
- Secret-scan status.
- Build/test gate status.
- Create release checklist.

Safety:

- Read-only status first.
- Mutating actions require confirmation.
- Release actions should run through existing checked scripts and documented gates.

### 7. Voice / Push-to-Talk

Purpose: follow the ThreadDeck signal without blocking the first build.

Initial version:

- UI button and state model only.
- Hold-to-talk visual affordance.
- Local transcript placeholder.
- Send transcript as canned follow-up later.

Later:

- Local Android speech recognizer.
- Optional Mac-side dictation.
- Confirmation before sending.

## Architecture sketch

Proposed module additions or areas:

- `android/domain/codex`: task/session models and state reducers.
- `android/domain/deck`: button/effect/theme domain contracts if existing module boundaries allow.
- `android/core/designsystem`: fancy key components, particle/effect overlays, theme tokens.
- `android/feature/codex`: Codex cockpit screen and mocked task dashboard.
- `android/feature/deck`: button editor and fancy deck grid if not already present.
- `protocol/codex-cockpit`: sanitized task snapshot schema and bridge notes.
- `mac-actions/codex-cockpit`: future local bridge/reader scripts.

Do not force this exact module split if the current v2 structure already has better homes. Keep contracts small and portable.

## Data contracts

`CodexTaskSnapshot`:

- `id`
- `title`
- `repoPath`
- `branch`
- `status`
- `startedAt`
- `updatedAt`
- `elapsedLabel`
- `needsAttention`
- `hasUnread`
- `safeSummary`
- `source`

`DeckButtonSpec`:

- `id`
- `slot`
- `type`
- `label`
- `symbol`
- `emoji`
- `themeRole`
- `action`
- `effect`
- `safetyLevel`
- `enabled`

`DeckEffectSpec`:

- `id`
- `kind`
- `trigger`
- `intensity`
- `durationMs`
- `emojiSet`
- `reducedMotionFallback`

`ThemePreset`:

- `id`
- `name`
- `description`
- `palette`
- `typography`
- `surfaceStyle`
- `keyStyle`
- `effectPack`

## Phased implementation plan

### Phase 0: Durable plan

Status: current planning pass.

Deliverables:

- `task_plan.md`
- `findings.md`
- `progress.md`

Acceptance:

- Scope covers cockpit, fancy buttons, effects, themes, bridge, risks, and release gates.

### Phase 1: Domain contracts

Deliverables:

- Codex task snapshot model.
- Deck button spec model.
- Deck effect spec model.
- Theme preset model.
- Mock repository with sample tasks and buttons.

Acceptance:

- Models compile.
- Mock data can represent ThreadDeck-style task states and empty fancy buttons.

### Phase 2: Fancy visual system

Deliverables:

- Fancy key composable.
- Theme presets.
- Confetti/emoji/spark overlay composables.
- Reduced-motion fallback.

Acceptance:

- A grid of empty and emoji buttons renders in multiple themes.
- Pressing mocked buttons triggers effects.
- Reduced motion disables particle chaos.

### Phase 3: Mock Codex Cockpit

Deliverables:

- `CodexCockpitScreen` with task grid, queue strip, attention rail, task details.
- Sample running/succeeded/failed/needs-attention tasks.
- Task completion triggers celebration.

Acceptance:

- Screen works entirely from mock data.
- User can see usefulness before live bridge exists.

### Phase 4: Button editor placeholders

Deliverables:

- Empty button detail sheet.
- Emoji picker placeholder.
- Theme selector.
- Action assignment placeholder.

Acceptance:

- Empty buttons feel intentional, not broken.
- User can preview emoji and effect choices even before persistence.

### Phase 5: Local bridge protocol

Deliverables:

- Protocol schema under `protocol/`.
- Example JSON snapshots.
- Privacy notes.
- Mac-side reader plan under `mac-actions/`.

Acceptance:

- Android mock repository can load an example snapshot.
- Sensitive content is excluded by default.

### Phase 6: Live bridge MVP

Deliverables:

- Mac-side local task snapshot reader.
- Android-side polling or local-network consumer.
- Offline and stale states.

Acceptance:

- Phone can display at least one real Codex task status from Mac-side data.
- No prompt/source content transmitted by default.

### Phase 7: GitHub/release utility deck

Deliverables:

- CI/release status buttons.
- Read-only GitHub release state.
- Secret/build gate badges.

Acceptance:

- Release deck can show latest tag/release/CI status without mutating repo.

### Phase 8: Voice/push-to-talk shell

Deliverables:

- Push-to-talk button state and UI.
- Transcript preview placeholder.
- Confirmation/send action placeholder.

Acceptance:

- User can understand the intended voice workflow without live dictation yet.

### Phase 9: Proof and release decision

Deliverables:

- Nested Android build/test proof.
- Root app build/test proof if root app touched.
- Device screenshot/video proof.
- Mac bridge proof if bridge included.
- Release checklist.

Acceptance:

- Public release only if APK, CI, secret scan, device smoke, and bridge proof are clean.

## Acceptance criteria

- CCF-001: Codecks has a mocked Codex cockpit showing task status, queue, elapsed time, attention, and completion.
- CCF-002: Codecks has fancy empty buttons that look deliberate and premium.
- CCF-003: Emoji buttons can trigger confetti/emoji/spark effects.
- CCF-004: At least six named themes are implemented or represented in theme data.
- CCF-005: Effects obey reduced-motion and battery-conscious bounds.
- CCF-006: Bridge schema excludes prompt/source content by default.
- CCF-007: Live bridge work is clearly separated from mock UI work.
- CCF-008: AppFunctions remains parked until dependency/API is confirmed.
- CCF-009: Release deck starts read-only and gates destructive actions.
- CCF-010: No public release claim is made without device and CI proof.

## Risks

- Codex local metadata may be undocumented and change under us.
- Quota/usage data may not be available in stable local form.
- AppFunctions dependency/API may have moved or require newer Android/plugin configuration.
- Animation-heavy UI can hurt battery, accessibility, and readability.
- Android cannot directly command arbitrary OLED pixels to a specific nit value; UI brightness must be approximated with color, HDR surfaces where supported, and system brightness constraints.
- Live bridge commands can become dangerous if confirmations are weak.
- The plan could sprawl into a platform; first slice must stay mock UI plus contracts.

## Recommended immediate build slice

Build a mock-first vertical slice:

1. Add Codex task, deck button, effect, and theme models.
2. Add mock task/button data.
3. Add fancy key composable and effect overlay.
4. Add Codex cockpit screen using mock data.
5. Add theme presets and a simple theme switcher.
6. Add button press effects for empty/emoji/task completion buttons.
7. Leave bridge, GitHub, voice, and AppFunctions as documented next phases.

This gives visible product momentum immediately and avoids getting trapped in fragile Codex metadata or AppFunctions dependency work before the fun/usefulness is real.

