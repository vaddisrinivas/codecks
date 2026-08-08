# TODO: Codecks dark commercial GA

Canonical contracts and acceptance criteria: `tasks/plan.md`.
Protected baseline: `v0.1.36` / `b6fc0ce`.

## A0 — Contract freeze

- [ ] T01 freeze tier, grandfathering, deletion, retention, refund, support, data-classification contracts
- [ ] Freeze `CommercialExecutionPolicy.PRODUCTION_DARK` below every flag/input
- [ ] Freeze monotonic AND gate algebra and typed decision reasons
- [ ] Keep local/Labs flags separate from commercial policy
- [ ] Add compiled `release.premium_enforcement=false`
- [ ] Freeze `ossRelease` / `playRelease` / `playInternal` matrix
- [ ] Prohibit any override parser/verifier/path in `playRelease`
- [ ] Freeze `playInternal` as `app.codecks.internal`, separate signer/backend/data
- [ ] Distinguish upload certificate from Play app-signing certificate/lineage
- [ ] Freeze explicit snapshot v1 DTO allowlist, bounds, and raw-command rejection
- [ ] Freeze backend account identity, session, deletion, entitlement, RTDN, reconciliation contracts
- [ ] Freeze transaction-bound Integrity request hash and replay contract
- [ ] Freeze commercial initializer/network policy; preserve valid local WorkManager
- [ ] Record deferred Reactive scope without claiming it complete
- [ ] Resolve every open contract request in `tasks/contract-requests/`

## A — Foundation

- [ ] T02 prove installed/GitHub/Play app-signing continuity; record upload cert separately
- [ ] T03 build all three artifacts; preserve package/data/no-shrink rules
- [ ] Scan `ossRelease` dependencies and merged manifest for commercial leakage
- [ ] Prove `playRelease` contains no internal override code
- [ ] Audit commercial providers, metadata, AndroidX Startup, Firebase, workers, eager DI, SDK constructors
- [ ] T04 implement typed commercial registry with owner/review/expiry metadata
- [ ] T05 implement monotonic resolver, decision explanation, schema-v5 local migration
- [ ] T06 implement no-op production-dark operational source and subtractive remote adapter
- [ ] Property-test that lower-authority allows cannot defeat any mandatory deny
- [ ] Adversarial-test corrupt/stale/cache/clock/deep-link/intent/restored state

## B — Account and sync

- [ ] T07 implement optional Credential Manager Google-token exchange and backend sessions
- [ ] Verify audience, issuer, signature, expiry, nonce, replay, rotation, revoke, account switch
- [ ] T08 ship in-app and public-web deletion before any account-creating test track
- [ ] Test idempotent deletion, session-first revocation, active subscription guidance, recreate
- [ ] T09 implement canonical allowlist-only snapshot codec and exhaustive adapters
- [ ] Reject shell actions and command/test/cleanup strings; never redact into runnable objects
- [ ] Fuzz sizes/counts/depth/strings/schemas/corruption; run secret canaries
- [ ] T10 implement explicit upload with checksum, idempotency, visible receipt/retry
- [ ] T11 implement preview, compatibility, local safety backup, merge/replace, rollback, conflicts
- [ ] Import automations disabled with no execution/preflight/live-test proof
- [ ] Prove two-device/account-switch/offline/quota/timeout/process-death behavior

## C — Play commerce

- [ ] T13a implement backend entitlement state machine, token ownership, RTDN dedupe, reconciliation first
- [ ] T12 create products/base plans/offers and Billing UX against approved T13a contract
- [ ] Grant nothing for pending/client-only state; bind purchase token to one account
- [ ] T13b verify server-side, persist before acknowledge, bind transaction Integrity, process voided purchases
- [ ] Test purchase/renewal/grace/hold/pause/cancel/expire/refund/revoke/chargeback
- [ ] Test RTDN duplicate/out-of-order/missed events and daily reconciliation
- [ ] T14 implement account-bound entitlement cache, restore/manage/support, bounded offline grace
- [ ] Keep premium enforcement OFF in public production

## D — Ads and privacy

- [ ] T15 isolate UMP; no launch initialization or request while production-dark
- [ ] Complete privacy, Data Safety, audience, content-rating, consent-withdrawal contracts
- [ ] T16 isolate Mobile Ads; no launch initialization or request while production-dark
- [ ] Require owner allow + no kill + rollout + consent + foreground + approved placement + not ad-free
- [ ] T17 allow only labeled Routine Bank/Theme cards after six organic items and opt-in rewarded preview
- [ ] Prohibit every operational/control/lockscreen/overlay/widget/notification placement
- [ ] Assert zero ad requests, not merely zero rendering, across lifecycle/rotation/DeX/failure cases
- [ ] Export only redacted commercial diagnostics; no tokens, IDs, contents, assignments, raw responses

## E — Product and GA

- [ ] T18 ship signed/validated Routine Bank with disabled-draft imports
- [ ] T19 ship coherent accessible Theme Gallery with preview/rollback
- [ ] T20 ship typed/preflighted SSH packs; never sell arbitrary shell execution
- [ ] T21 close setup/reconnect/clipboard/automation/accessibility/DeX/battery/device/macOS matrices
- [ ] T22 complete listing, policies, support, deletion URL, key recovery, rollback, incident runbooks

## F — Exact-artifact dark release

- [ ] T23 prove commercial E2E only in `playInternal`
- [ ] T23 prove exact `playRelease` has zero commercial UI, SDK construction, or startup/network calls
- [ ] Prove test-override replay is rejected by production package/certificate
- [ ] Prove protected `app.codecks` in-place update preserves data, SSH, HID, and local core
- [ ] T24 satisfy applicable closed-test/production-access gate with no open P0/P1
- [ ] Record source SHA, version, AAB SHA-256, signing lineage, dependency/manifest/evidence digests
- [ ] Promote the exact admitted AAB; any rebuild resets admission
- [ ] T25 stage production with account/sync/Billing/premium enforcement/ads all OFF
- [ ] Verify no release minification or resource shrinking
- [ ] Verify rollback rehearsal and stage-by-stage health gates

## Later owner decisions — not authorized

- [ ] T26 explicit separate go/no-go: account
- [ ] T26 explicit separate go/no-go: cloud sync
- [ ] T26 explicit separate go/no-go: Play Billing
- [ ] T26 explicit separate go/no-go: premium enforcement
- [ ] T26 explicit separate go/no-go: ads
- [ ] `Keep disabled indefinitely` remains valid for every surface

## Permanent constraints

- [ ] Never uninstall, clear, downgrade, differently sign, or instrument `app.codecks`
- [ ] Never accept a Play app-signing mismatch
- [ ] Never re-enable release minification/resource shrinking
- [ ] Never let Remote Config, prefs, cached state, or client Billing grant access
- [ ] Never sync credentials, clipboard, raw commands, host data, or execution proof
- [ ] Never activate a commercial surface without later explicit owner approval
