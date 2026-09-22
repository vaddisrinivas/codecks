# M09 local metrics

Source: `6ca6bb3d962ded531443da41c8c13ec32c0cfbb2`.
Baseline: `d1f1788f03fe59bb0dceb5822e9f5b19194090fd`.

The baseline census covers Git-tracked source at its bound commit. The current
census covers Git-tracked plus untracked non-ignored workspace source. Both use
the same path/source-set classification. Runtime numbers are not inferred from
source or historical APKs.

| Metric | Baseline | Current | Status |
| --- | ---: | ---: | --- |
| Classified source files (baseline tracked; current tracked + untracked non-ignored) | 515 | 640 | `PASS` |
| Classified physical source lines (same scopes) | 90,742 | 106,210 | `PASS` |
| Public-production lines | 54,900 | 60,270 | `PASS` |
| Largest public-production file | 3,329 lines | 997 lines | `PASS` |
| Production dependency declarations | 22 | 22 | `PASS` |
| Test/debug dependency declarations | 9 | 9 | `PASS` |
| Startup time | `NOT_RUN` | `NOT_RUN` | Device execution required |
| Clean/warm build time | `NOT_RUN` | `NOT_RUN` | No current build receipt |
| Exact public APK bytes | `NOT_RUN` | `NOT_RUN` | No current candidate |

The 10,270-line target excess is fully assigned to feature owners in
`tasks/test-evidence/autonomous-maturity-m09-current-source-census.json`.
No code was removed merely to meet the 50,000-line target.

This report is not startup, APK, device, signer, candidate, or release evidence.
