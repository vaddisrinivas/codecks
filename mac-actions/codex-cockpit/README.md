# Codex Cockpit Bridge

This folder contains the Mac-side helpers for Codecks Codex Cockpit preview data.

## Privacy defaults

- Emit status-only snapshots.
- Do not include prompt content by default.
- Do not include source file content by default.
- Prefer task id, title, repo, branch, state, elapsed time, updated time, attention flags, and short safe summaries.

## Bridge order

1. Use a supported Codex thread connector if one is available.
2. Fall back to stable local Codex metadata only when the format is understood.
3. Add GitHub/CI/release state separately.
4. Keep mock JSON usable for UI development.

## Current artifact

The `v0.1.5` release includes `codecks-v2-cockpit-preview-v0.1.5.apk`. The preview can render mock cockpit data, paste/fetch bridge snapshots, and fetch the local bridge server from emulator/dev setups. The protocol shape is captured in `protocol/codex-cockpit/snapshot.schema.json` and `protocol/codex-cockpit/example-snapshot.json`.

The signed root APK is still `codecks-v0.1.5.apk`; the cockpit APK is a debug-signed preview artifact.

## Mock emitter

Use `emit-mock-snapshot.sh` to generate a status-only bridge payload while the live reader is still being built:

```bash
mac-actions/codex-cockpit/emit-mock-snapshot.sh /tmp/codecks-codex-cockpit-snapshot.json
```

The script prints the output path and never includes prompt or source content.

## Release status emitter

Use `emit-release-snapshot.sh` to generate a status-only release/GitHub deck payload from a local Git checkout:

```bash
mac-actions/codex-cockpit/emit-release-snapshot.sh <repo> /tmp/codecks-release-cockpit-snapshot.json
```

It reports dirty worktree count, latest local tag, and GitHub release URL when `gh` is available and authenticated. It does not read prompt or source content.

## Local Codex metadata emitter

Use `emit-local-codex-snapshot.py` to generate a status-only snapshot from local Codex metadata:

```bash
mac-actions/codex-cockpit/emit-local-codex-snapshot.py /tmp/codecks-local-codex-snapshot.json
```

This reader only uses:

- `~/.codex/session_index.jsonl`
- `~/.codex/process_manager/chat_processes.json`

It does not read session bodies, prompts, tool outputs, source files, or conversation content.

## Local bridge server

Use `serve-local-bridge.sh` to emit the mock, release, and local Codex metadata snapshots, then serve them locally:

```bash
mac-actions/codex-cockpit/serve-local-bridge.sh <repo> 8765
```

Android emulator URL:

```text
http://10.0.2.2:8765/codecks-codex-cockpit-snapshot.json
```

Local Codex metadata URL:

```text
http://10.0.2.2:8765/codecks-local-codex-snapshot.json
```

Physical phone over USB can use `adb reverse`:

```bash
adb reverse tcp:8765 tcp:8765
```

Then fetch:

```text
http://127.0.0.1:8765/codecks-codex-cockpit-snapshot.json
```

## v0.1.5 validation status

- Mock, release, and local Codex metadata emitters were smoke-tested.
- The local HTTP bridge was rendered through the Android emulator.
- Physical Android-device proof is still pending.
