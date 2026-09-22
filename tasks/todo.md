# Codecks production-dark commercial checklist

Updated: August 10, 2026

Canonical contracts: [`tasks/plan.md`](plan.md)

Public release: `v0.1.37`

Machine truth: [`docs/release/production-state.json`](../docs/release/production-state.json)

Legend: `[x]` implemented with repository evidence; `[ ]` incomplete or
`NOT_RUN`. An implementation check does not authorize public activation.

## A0 — Frozen contracts

- [x] Production-dark root deny, monotonic gate algebra, typed reasons, and
  compiled owner policy — [`CommercialExecutionPolicyTest.kt`](../app/src/test/java/io/codecks/domain/commercial/CommercialExecutionPolicyTest.kt).
- [x] Local/Labs flags separated from commercial authority —
  [`TypedFeatureFlagRegistryTest.kt`](../app/src/test/java/io/codecks/domain/features/TypedFeatureFlagRegistryTest.kt).
- [x] `ossRelease` / `playRelease` / `playInternal` package and source-set
  matrix; internal override isolated to `app.codecks.internal` —
  [`PlayReleaseCommercialAdapterArchitectureTest.kt`](../app/src/test/java/io/codecks/domain/commercial/PlayReleaseCommercialAdapterArchitectureTest.kt).
- [x] Snapshot v1 allowlist/bounds/raw-command rejection —
  [`PortableSnapshotTest.kt`](../shared/src/commonTest/kotlin/io/codecks/shared/snapshot/PortableSnapshotTest.kt).
- [x] Backend account/session/deletion/retention, entitlement/RTDN,
  reconciliation, and transaction-bound integrity contracts —
  [`AccountServiceTest.kt`](../backend/src/test/kotlin/io/codecks/backend/contracts/AccountServiceTest.kt)
  and [`EntitlementServiceTest.kt`](../backend/src/test/kotlin/io/codecks/backend/contracts/EntitlementServiceTest.kt).
- [x] Commercial initializer/network policy frozen; empty contract-request
  queue — [`CommercialServiceArchitectureTest.kt`](../app/src/test/java/io/codecks/domain/commercial/CommercialServiceArchitectureTest.kt)
  and [`run_commercial_static_proof.sh`](../scripts/run_commercial_static_proof.sh).
- [x] Deferred Reactive scope recorded without launch claims —
  [documentation drift tests](../tools/tests/test_verify_release_documentation.py).
- [ ] Business decisions for tier split, prices, regions, retention duration,
  grandfathering, refund handling, and support SLA — owner decision required.

## A — Build and policy foundation

- [x] All three variants compile and enforce separate package/data/no-shrink
  boundaries — Gradle validators and
  [`verify_release_no_shrink.sh`](../scripts/verify_release_no_shrink.sh).
- [x] OSS dependency/manifest leakage, Play internal-override exclusion, and
  initializer reachability have deterministic static gates —
  [`commercial_proof_harness.py`](../tools/commercial_proof_harness.py) and app
  architecture tests.
- [x] Typed commercial registry/policy, corrupt/stale/cache/clock migration,
  lower-authority-deny property tests, and no-op production services exist —
  [`CommercialExecutionPolicyTest.kt`](../app/src/test/java/io/codecks/domain/commercial/CommercialExecutionPolicyTest.kt).
- [ ] Existing production signer to Play app-signing lineage continuity — no
  Play Console or exact signing-lineage evidence available.
- [ ] Exact future `playRelease` AAB dependency/manifest/startup admission — no
  next-version artifact is assigned or admitted.

## B — Account and cloud snapshot foundations

- [x] Account/session contracts cover nonce consumption, assertion replay,
  session rotation/revocation, account switching, and deletion —
  [`PlayInternalAccountAdapterTest.kt`](../app/src/testPlayInternal/java/io/codecks/commercial/auth/PlayInternalAccountAdapterTest.kt)
  and [`AccountServiceTest.kt`](../backend/src/test/kotlin/io/codecks/backend/contracts/AccountServiceTest.kt).
- [x] Account deletion state machine is idempotent, revokes sessions first,
  deletes snapshots, and leaves failed deletion fail-closed —
  [`AccountServiceTest.kt`](../backend/src/test/kotlin/io/codecks/backend/contracts/AccountServiceTest.kt).
- [x] Portable snapshot codec/import pipeline rejects unsafe fields and covers
  checksum, bounds, schemas, conflicts, preview, merge/replace, and rollback —
  [`PortableSnapshotTest.kt`](../shared/src/commonTest/kotlin/io/codecks/shared/snapshot/PortableSnapshotTest.kt)
  and [`PlayInternalSnapshotAdapterTest.kt`](../app/src/testPlayInternal/java/io/codecks/commercial/sync/PlayInternalSnapshotAdapterTest.kt).
- [x] Public adapters deny before constructing backend/storage clients;
  `playInternal` has isolated deterministic account/sync tests and lab UI —
  [`ProductionPlayCommercialAdaptersTest.kt`](../app/src/testPlay/java/io/codecks/commercial/auth/ProductionPlayCommercialAdaptersTest.kt).
- [ ] Live Google identity/backend exchange, public deletion web endpoint,
  two-device cloud service, quotas, retention, and operational support.
- [ ] Public sign-in or cloud-sync activation — **OFF; not authorized**.

## C — Billing and entitlement foundations

- [x] Backend-authoritative entitlement, RTDN dedupe/out-of-order handling,
  reconciliation, token ownership, and transaction integrity contracts/tests —
  [`EntitlementServiceTest.kt`](../backend/src/test/kotlin/io/codecks/backend/contracts/EntitlementServiceTest.kt).
- [x] Internal sandbox covers purchase lifecycle, replay, account switching,
  restore/manage, offline/error states, refunds/revokes, and reconciliation —
  [`InternalBillingSandboxTest.kt`](../app/src/testPlayInternal/java/io/codecks/internalcommercial/billing/InternalBillingSandboxTest.kt).
- [x] Public Play purchase, entitlement, and integrity adapters are inert and
  production-dark; premium enforcement remains compiled off —
  [`PlayReleaseCommercialAdaptersTest.kt`](../app/src/testPlay/java/io/codecks/commercial/PlayReleaseCommercialAdaptersTest.kt).
- [ ] Play Console products/base plans/offers, real sandbox purchase/RTDN,
  server credentialing, and exact AAB admission.
- [ ] Public Billing or premium activation — **OFF; not authorized**.

## D — Ads and privacy foundations

- [x] Public consent/ad services deny before SDK or network construction;
  commercial startup is absent under production-dark policy —
  [`PlayProductionDarkAdsPrivacyTest.kt`](../app/src/testPlay/java/io/codecks/commercial/PlayProductionDarkAdsPrivacyTest.kt).
- [x] Placement model excludes operational, control, lockscreen, overlay,
  widget, and notification surfaces; internal adapters/tests are isolated —
  [`InternalAdsAdaptersTest.kt`](../app/src/testPlayInternal/java/io/codecks/internalcommercial/ads/InternalAdsAdaptersTest.kt).
- [x] Commercial diagnostics are bounded/redacted by typed contracts and tests
  — [`InternalPrivacyAdaptersTest.kt`](../app/src/testPlayInternal/java/io/codecks/internalcommercial/privacy/InternalPrivacyAdaptersTest.kt).
- [ ] UMP/Mobile Ads production SDK integration, Data Safety submission,
  audience/content-rating review, consent-withdrawal and real ad-policy tests.
- [ ] Public ads activation — **OFF; not authorized**.

## E — Product and GA

- [x] Typed offline Routine Bank, Theme Gallery foundations, and preflighted
  SSH packs exist; imports are bounded, conflict checked, and rollback capable
  — [`CatalogInstallEngineTest.kt`](../app/src/test/java/io/codecks/domain/catalog/CatalogInstallEngineTest.kt).
- [ ] Final public catalog/theme UX acceptance and complete setup, reconnect,
  clipboard, automation, accessibility, DeX, battery, Android/macOS matrices.
- [ ] Store listing/policies/support/deletion URL/key recovery/rollback/incident
  operations and external review.

## F — Exact-artifact release

- [x] Commercial E2E is structurally isolated to `playInternal`; public tests
  assert production-dark adapters and internal namespace exclusion —
  [`ManagedCommercialDarkInstrumentedTest.kt`](../app/src/androidTestPlay/java/io/codecks/commercialproof/ManagedCommercialDarkInstrumentedTest.kt).
- [x] Source, unit, architecture, managed-emulator, reachability, cold-start,
  jobs/alarms, bundletool, and no-shrink proof lanes exist —
  [static](../scripts/run_commercial_static_proof.sh),
  [managed](../app/src/androidTestPlay/java/io/codecks/commercialproof/ManagedCommercialDarkInstrumentedTest.kt),
  [reachability](../scripts/commercial_surface_attack.sh),
  [cold-start](../scripts/collect_commercial_cold_start.sh),
  [bundletool](../scripts/build_play_proof_artifacts.sh), and
  [no-shrink](../scripts/verify_release_no_shrink.sh).
- [ ] Repeat every proof against the exact next-version signed AAB/APK and
  preserve its immutable evidence bundle.
- [ ] Prove protected `app.codecks` in-place update preserves data, SSH, HID,
  and core behavior on a physical phone and real Mac.
- [ ] Play closed-test/production-access gate, no-open-P0/P1 review, staged
  rollout, rollback rehearsal, and exact-artifact promotion.

## Later owner decisions — not authorized

- [ ] Sign-in go/no-go — currently **OFF**.
- [ ] Cloud sync go/no-go — currently **OFF**.
- [ ] Play Billing go/no-go — currently **OFF**.
- [ ] Premium enforcement go/no-go — currently **OFF**.
- [ ] Ads go/no-go — currently **OFF**.
- [x] `Keep disabled indefinitely` remains a supported decision for every
  surface — [`CommercialExecutionPolicyTest.kt`](../app/src/test/java/io/codecks/domain/commercial/CommercialExecutionPolicyTest.kt).

## Permanent constraints

- [x] Never uninstall, clear, downgrade, differently sign, or instrument the
  protected `app.codecks` package without current explicit approval —
  [`verify_release_no_shrink.sh`](../scripts/verify_release_no_shrink.sh).
- [x] Never accept a Play app-signing mismatch —
  [commercial proof harness tests](../tools/tests/test_commercial_proof_harness.py).
- [x] Never re-enable release minification/resource shrinking without explicit
  approval and exact physical SSH proof —
  [`verify_release_no_shrink.sh`](../scripts/verify_release_no_shrink.sh).
- [x] Never let remote config, preferences, cached state, or client Billing
  override a mandatory commercial deny —
  [`CommercialExecutionPolicyTest.kt`](../app/src/test/java/io/codecks/domain/commercial/CommercialExecutionPolicyTest.kt).
- [x] Never sync credentials, clipboard, raw commands, host data, execution
  proof, or diagnostics —
  [`CommercialServiceArchitectureTest.kt`](../app/src/test/java/io/codecks/domain/commercial/CommercialServiceArchitectureTest.kt).
- [x] Never activate a commercial surface without later explicit owner
  approval for that surface — [`CommercialExecutionPolicyTest.kt`](../app/src/test/java/io/codecks/domain/commercial/CommercialExecutionPolicyTest.kt).

## Context Deck expansion

- [x] Closed Context Deck contracts and reducers —
  [`ContextDeckPolicyTest.kt`](../app/src/test/java/io/codecks/domain/contextdeck/ContextDeckPolicyTest.kt).
- [x] App-follow offer, local three-item context strip, and modifier layer —
  [`HomeContextDeckCoordinator.kt`](../app/src/main/java/io/codecks/ui/home/HomeContextDeckCoordinator.kt).
- [x] Live-state rail and analog controls —
  [`ContextDeckLiveRail.kt`](../app/src/main/java/io/codecks/ui/home/ContextDeckLiveRail.kt).
- [x] Current-Space window map, File Drop, workflow recorder, and Multi-Mac handoff —
  [`ContextDeckDialogs.kt`](../app/src/main/java/io/codecks/ui/home/ContextDeckDialogs.kt).
- [x] Opt-in four-control lock-screen Mini Deck —
  [`LockscreenMiniDeckInstrumentedTest.kt`](../app/src/androidTest/java/io/codecks/ui/mouse/lockscreen/LockscreenMiniDeckInstrumentedTest.kt).
- [x] Focused/full JVM, managed-phone semantics, privacy, no-shrink, lint, and clean-diff gates —
  [`ContextDeckInstrumentedTest.kt`](../app/src/androidTest/java/io/codecks/ui/home/ContextDeckInstrumentedTest.kt).
- [ ] Tablet, RTL, 200% text, live Mac transfer/window-state, and physical-device proof remain `NOT_RUN`.

Exact acceptance criteria and stop conditions: [`CONTEXT_DECK_PLAN.md`](CONTEXT_DECK_PLAN.md).
