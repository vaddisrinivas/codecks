# Codecks Top 15 Feature Research

## Goal

Select the best 15 features from the previously discussed product/reliability and sensor/AI lists, backed by current Codecks evidence plus GitHub, Reddit, official platform, and broader product research. Deliver a dependency-aware implementation roadmap without changing app code.

## Decision criteria

- User impact and frequency
- Fit with local-first Android-to-Mac control surface thesis
- Reliability and privacy risk
- Technical feasibility on Android and macOS
- Differentiation versus mature remote-control apps
- Implementation cost and dependency order

## Phases

1. **Current baseline** - complete
2. **GitHub landscape** - complete
3. **Reddit and platform evidence** - complete
4. **Top-15 scoring and selection** - complete
5. **Implementation roadmap and final report** - complete
6. **Wave 0/4 implementation slice: adaptive Trackpad feedback** - complete
7. **Focused validation and remaining-wave checkpoint** - complete
8. **Next implementation slice: release-quality HID/device proof and command receipts** - pending

## Deliverables

- `findings.md`: source-backed research notes and candidate scoring
- `progress.md`: work log and validation boundaries
- Final ranked top 15 with rationale, MVP scope, dependencies, and release waves

## Constraints

- No profiles feature; user rejected it.
- Keep Air Touch in Labs; do not rank or plan production investment for it.
- No backend/account/cloud sync as a prerequisite.
- Preserve the existing untracked v2 foundation.
- Physical Bluetooth HID behavior remains unverified until tested on the Samsung and Mac.
- Content-free gesture telemetry only: pointer counts, movement/duration buckets, classification, correction count; no typed text, clipboard contents, foreground app names, or remote output.

## Errors

- Initial GitHub keyword query combined too many terms and stopped before producing `SUMMARY.md` or deep scans. Resolution: inspect raw results, then run narrower landscape searches.
- Direct-scan helper crashed on repositories where `repositoryTopics` was null (`LiangLuDev/HidPeripheral`, `KDE/kdeconnect-android`, `Unrud/remote-touchpad`). Resolution: use `gh api`/README/issues directly for those repos; successful scans remain reusable.
