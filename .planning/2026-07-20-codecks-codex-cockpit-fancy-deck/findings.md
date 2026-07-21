# Findings

Date: 2026-07-20

## Reddit / community product signals

### ThreadDeck

Source: https://www.reddit.com/r/codex/comments/1v1sbri/i_built_an_opensource_codex_task_dashboard_for/

Useful signals:

- Users want a glanceable Codex task dashboard outside the normal app window.
- Strong features include running task state, queue count, elapsed time, completion alerts, unread alerts, and quota.
- Push-to-talk plus send/follow-up actions are compelling.
- Local-first and no telemetry are important trust markers.
- The implementation reads undocumented local Codex metadata, which is useful but fragile.

Codecks implication:

- Build a dashboard/control surface, not just a generic macro pad.
- Start with sanitized local task snapshots and mock data.
- Treat live Codex metadata as a bridge layer with graceful failure, not as UI foundation.

### AgentDeck

Source: https://github.com/AgentDeckHQ/agentdeck

Useful signals:

- Agent work benefits from physical or persistent control surfaces.
- A key can be a live session card, not only a static shortcut.
- Multi-surface support is interesting: Stream Deck, tablets, e-ink, ESP32, and TUI.

Codecks implication:

- Android tablet/phone/DeX can be a first-class agent dashboard.
- Button state should repaint live and carry task meaning.
- Protocol should stay surface-agnostic so future Stream Deck/Mac/web surfaces can reuse it.

### OpenDeck Codex dashboard

Source: https://github.com/ChrisTitusTech/streamdeck-dashboard

Useful signals:

- A tiny status surface is valuable even with only working/complete/offline states.
- Clear colors beat over-detailed widgets for glanceability.
- Local-only status reading is attractive.

Codecks implication:

- Do not wait for a huge integration.
- A few clear states with strong button styling can be useful fast.

## 2026 utility/productivity trend signals

### GitHub trending snapshot

Source: https://github.com/trending

Observed categories:

- Voice tools.
- Memory/cognitive tools.
- CLI agent tools.
- SEO/automation utility tools.

Codecks implication:

- Voice follow-up and memory/context display are worth planning.
- A local task cockpit pairs well with CLI agent usage.
- Utility decks should support repeatable workflows, not just novelty.

### AI agent adoption research

Source: https://arxiv.org/abs/2607.01418

Signal:

- In a large software-engineering rollout, visible peer use and integration into real workflows mattered for adoption.

Codecks implication:

- Make agent work visible.
- Show what is running, what finished, what is blocked, and what needs human judgment.

Source: https://arxiv.org/abs/2607.14037

Signal:

- Agentic pull requests are growing but still often require human oversight.

Codecks implication:

- Codecks should emphasize oversight, review, approvals, and release readiness rather than pretending agents are fully autonomous.

## Device / display notes

OLED/AMOLED phones can show different brightness per pixel in normal rendered content because each OLED pixel emits independently.

Important limitation:

- Android app code does not normally get raw per-pixel nit control.
- Practical control comes from colors, alpha, composition, system brightness, HDR surfaces where available, and display mode constraints.

Codecks implication:

- Use OLED-friendly localized glow, dark/gradient backgrounds, and bright accent pixels.
- Do not promise “hardware per-pixel brightness control” as an app feature.

## Android assistant integration state

Problem found in Codecks v2:

- Android assistant integration dependencies were unresolved in the nested Android build.
- The WIP service imported experimental Android assistant integration APIs and registered a service in the manifest.
- Build could not resolve the declared AppFunctions service artifact/version.

Current handling:

- Android assistant integration is deferred, not discarded.
- The source is preserved at `.planning/2026-07-20-codecks-v2-appfunctions-parked/CodecksAppFunctionService.kt.disabled`.
- It should only be revived after confirming current official dependency coordinates, Android/API requirements, and integration shape.

Codecks implication:

- Android assistant integration is a later integration lane.
- It should not block the Codex cockpit/fancy deck slice.
