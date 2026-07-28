# WT08 Profiles/Gestures Evidence

Status: FOCUSED_GREEN

Scope:
- Added `ReactiveProfilePreferences` and `ReactiveProfileResolver`.
- Extended `ReactiveTrackpadContext` with pinned controls, disabled providers, and per-profile max controls.
- Updated `DeterministicReactiveEngine` to skip disabled providers, rank pinned controls first in user order, and honor context max controls.

Acceptance:
- Hidden controls are merged from base context and profile.
- Hidden controls are removed from pinned controls.
- Disabled providers are merged into resolver output and skipped by engine.
- Pinned controls lead engine output in profile order.
- Profile max controls limits ranked output.
- Invalid max controls and TTL are rejected.

Validation:
- `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.domain.reactive.*Profile*' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`
- Result: BUILD SUCCESSFUL
