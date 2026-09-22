# M09 current source census

Current source: `08c9ae50ee0a46ad1682b03c4989130ae78f2bad` plus this
evidence-only Batch 1 patch. Method and file-level hashes are in
[`autonomous-maturity-m09-current-source-census.json`](../../tasks/test-evidence/autonomous-maturity-m09-current-source-census.json).

## Result

| Category | Baseline LOC | Current LOC | Delta |
| --- | ---: | ---: | ---: |
| Public production | 54,900 | 60,267 | +5,367 |
| Internal lab | 2,474 | 2,553 | +79 |
| Companions | 3,699 | 5,270 | +1,571 |
| Tests | 29,642 | 36,662 | +7,020 |
| Debug / other source sets | 5 | 281 | +276 |
| Build definitions | 22 | 27 | +5 |
| **All tracked Kotlin/Java/Swift** | **90,742** | **105,060** | **+14,318** |

The public-production target is 50,000 physical lines. Current source is 10,267
lines above it. No code was cut to reach the target. The inventory assigns every
public-production file and line to a feature owner. The largest owners are:

| Feature owner | LOC | Product reason retained |
| --- | ---: | --- |
| App shell/composition | 5,198 | Variant-safe composition, lifecycle, navigation, and feature wiring |
| Trackpad UI | 4,891 | HID gestures, pointer controls, layouts, and safety feedback |
| Deck UI | 3,179 | Persistent controls, editing entry, and execution feedback |
| Settings | 3,134 | Connection, helper pairing, privacy, appearance, and support configuration |
| Connection UI | 2,443 | SSH discovery, pinning, repair, and readiness UX |
| Reactive domain | 2,413 | Typed helper state, authorization, and transport-independent policy |
| Rules UI | 2,280 | Review, test, enable, retry, cancellation, and undo surfaces |
| AI UI | 2,238 | Provider setup and reviewable disabled-draft workflow |
| Automation domain | 2,109 | Preflight, revision, bounded execution, and receipts |
| Automation data | 2,072 | Versioned persistence, quarantine, history, and scheduling |
| Other explicit feature owners | 30,310 | Clipboard, keyboard, themes, editor, design system, catalog, persistence, privacy, commercial-dark seams, protocols, and remaining typed feature packages |
| **Public production** | **60,267** | **Every line assigned; 10,267-line excess explicitly feature-owned** |

This is LOC accountability, not runtime or release admission. Startup, APK,
device, signing, and exact-artifact measurements remain separate gates. The
largest current production file is `AppCompositionRoot.kt` at 997 lines; no
tracked production Kotlin/Java/Swift file exceeds 1,000 physical lines.
