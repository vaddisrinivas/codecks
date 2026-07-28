WT05 engine, ranking, and provider policy

Status: FOCUSED_GREEN

Implemented:
- ReactiveControl metadata: providerId, actionId, confidence, explanation, policy, conflicts, stale behavior.
- Default policy filters expired, hidden, denied, unsupported-capability controls.
- Controls with conflicts cannot be auto-allowed.
- Stale state denies unsafe controls and downgrades safe controls.
- Deterministic ranking uses priority, source, risk, stale behavior, confidence, and stable id tie-break.
- Active app provider has default Browser/Finder/Terminal control mappings and emits typed front-app controls without shell/cloud/LLM behavior.

Validation:
- PASS: ./gradlew :app:testReleaseUnitTest --tests 'io.codecks.domain.reactive.*Reactive*' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
- PASS: git diff --check
