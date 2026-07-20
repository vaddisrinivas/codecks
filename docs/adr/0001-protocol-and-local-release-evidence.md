# ADR 0001: Protocol and Local Release Evidence Assets

## Status

Accepted for fixture and QA evidence baseline.

## Context

Codecks v2 localRelease needs a stable, local-first action protocol and evidence trail before platform adapters are complete. The release boundary excludes login, billing, hosted backend, cloud database, remote config, Firebase, and silent telemetry.

## Decision

Keep non-Android protocol contracts in `protocol/`, reviewed Mac action fixtures in `mac-actions/`, QA evidence in `docs/evidence/`, and release boundary tooling in `tools/`.

The first protocol baseline contains:

- action definition schema and fixture
- action invocation schema and fixture
- action plan schema and fixture
- action receipt schema and fixture
- Mac action catalog fixture covering core local actions
- localRelease forbidden-string and JSON syntax checker

## Consequences

Android and Mac adapter work can target stable fixture shapes without embedding evidence-only assets in implementation code. QA can track current status and remaining gates per C2 item while localRelease boundary checks stay scriptable.
