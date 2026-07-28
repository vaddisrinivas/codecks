# WT15A Mac helper framed transport service

## Scope

- Added `ReactiveFramedTransportService`.
- Accepts `ReactiveFrameCodec` frames.
- Routes typed payloads by shape:
  - `ReactiveHello` → `ReactiveSessionCoordinator.handleHello`.
  - `ReactiveProof` → `ReactiveSessionCoordinator.handleProof`.
  - `ReactiveRequestEnvelope` → `ReactiveSessionCoordinator.handleRequest`.
- Returns encoded response frames.
- Keeps listener/socket ownership out of this seam for focused validation.

## Verification

```text
cd macHelper && swift test
```

Result: 17 tests passed, 0 failures.

Covered:

- framed hello/proof/execute route.
- bad frame rejection.
- unknown payload rejection.
- replay rejection at frame boundary.

## Device safety

- No Android phone used.
- No production app touched.
- No release shrink setting changed.
