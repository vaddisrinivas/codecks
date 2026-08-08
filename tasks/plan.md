# Implementation Plan: Codecks Play GA, Accounts, Flags, Commerce, and Ads

Updated: August 8, 2026

Status: governing implementation plan. This supersedes the pre-commercial
local-product plan captured at Codecks `v0.1.36` / commit `b6fc0ce`; it does not
erase that baseline or convert its deferred Reactive Platform work into a GA
dependency.

## Baseline preservation and supersession

- `v0.1.36` / `b6fc0ce` is the protected local-only reference behavior.
- Existing Deck, Trackpad, Keyboard, Clipboard, SSH, HID, automation, backup,
  AI-drafting, and settings behavior must survive every migration.
- The following Reactive work remains deferred and independently scoped:
  native Mac-helper completion; authenticated pairing/pinned helper identity;
  full helper transport; unified helper/SSH live Mac state; complete provider,
  receipt, and undo execution; full iOS app; DeskDock native detection; Apple
  Shortcuts; Spotlight/SFTP; monitor brightness; Accessibility discovery; and
  full cross-platform Reactive validation/release.
- A commercial task may reuse an already-proven local contract, but may not
  silently claim or activate any deferred Reactive capability.

## Outcome

Ship Codecks on Google Play without weakening its local-first control surfaces:

- core Deck, Trackpad, Keyboard, Clipboard, and local automation remain useful
  without an account or payment;
- optional sign-in can restore portable, non-secret configuration when approved;
- verified Play purchases can grant official-service entitlements when approved;
- typed flags support dark launch, staged rollout, and immediate kill switches;
- all account, sync, Play commerce, entitlement, and ad surfaces ship disabled
  until the owner explicitly approves a separate activation;
- the existing `app.codecks` install, data, signing lineage, SSH behavior, and
  unshrunk release configuration remain protected.

## Product decision

### Tiers

| Tier | Price model | Includes | Ads |
| --- | --- | --- | --- |
| Free | Free | Core local control, local backup/export, starter themes/routines | Discovery ads may be enabled later |
| Pro Lifetime | One-time Play product | Premium themes, premium routine packs, advanced typed Mac actions, permanent ad removal | None |
| Codecks Sync | Subscription | Everything in Pro plus cloud snapshots, cross-device restore/sync, continuously maintained content | None |

Active Sync includes Pro. Pro Lifetime does not include ongoing cloud storage.
Exact prices and trial/intro offers remain Play Console decisions, not constants
inside the app.

### Account policy

- Account, sync, Billing, premium gating, and ads are implementation-ready but
  production-disabled. No decision to activate them has been made.
- Sign-in is optional for local core use.
- Sign-in is required for cloud sync and stable cross-device entitlement
  association.
- First sign-in never silently replaces local state.
- SSH keys, AI keys, passwords, clipboard contents, host secrets, and sensitive
  run output never enter cloud backup.
- Existing users keep their local data and core behavior.
- Account deletion exists both in-app and on a public web page.

### Activation policy

The first production-capable build is a **dark commercial release**:

- `release.account = false`
- `release.cloud_sync = false`
- `release.play_billing = false`
- `release.premium_enforcement = false`
- `release.explore = false` for commercial catalog/network behavior
- `release.ads_sdk = false`
- every commercial backend mutation and ad placement kill switch is closed;
- no account, upgrade, subscription, premium lock, restore-from-cloud, consent,
  or ad UI is reachable;
- no auth, sync, Billing, Integrity, UMP, Mobile Ads, or commercial Remote
  Config client initializes merely because the app launches;
- existing local users see the same free/local product.

Only `playInternal` testers may use compile-time test capability plus
test-account/backend controls. Closed-track `playRelease` testers use the same
immutable production-dark path intended for production. Activating any
commercial surface requires a new written owner decision, policy/data review,
activation checklist, and staged rollout. Completing this implementation plan
does not authorize activation.

`CommercialExecutionPolicy.PRODUCTION_DARK` is a compile-time, non-remotely
overridable root deny below navigation, UI, preferences, cached state, Remote
Config, entitlements, deep links, and backend responses. Public `playRelease`
contains no internal-override parser, verifier, preference, route, or receiver.

### Ad policy

Do not enable ads in the first public production rollout. Ship the consent,
placement, entitlement, test, and kill-switch infrastructure dark. Ads require
their own later decision even if account, sync, or Billing is activated first.

Allowed placements:

1. A clearly labeled native card in the dedicated Routine Bank/Explore feed,
   after at least six organic items.
2. A clearly labeled native card in the Theme Gallery feed, after at least six
   organic items.
3. An explicitly requested rewarded ad for one temporary premium theme or
   routine preview. The user must choose `Watch to preview`; no core capability
   may depend on it.

Forbidden placements:

- Trackpad, overlay trackpad, lock-screen trackpad, Keyboard, Deck, button
  catalog, button placement, Clipboard, SSH setup, connection repair,
  automation validation/execution, AI generation, purchase, sign-in, restore,
  diagnostics, notifications, widgets, app open, app exit, and DeX control
  surfaces.
- No interstitial ads.
- No ads over other apps.
- No ad may appear where a control was previously located or cause content to
  shift after loading.

Pro Lifetime and Sync suppress ad requests, not merely ad rendering.

## Non-negotiable safety contracts

1. Never uninstall, clear, downgrade, or differently sign `app.codecks`.
2. Play App Signing must import the current production signing key. Create a
   separate upload key. Stop if the Play certificate differs from the installed
   and GitHub release certificate.
3. Release minification and resource shrinking stay disabled.
4. GitHub/local-only and Play variants use explicit source-set boundaries.
   Commercial SDKs must not silently enter the FOSS artifact.
5. Client booleans never prove payment. The backend is authoritative for
   official cloud/content entitlements.
6. Remote Config is rollout input, not authorization and not secret storage.
7. Play Integrity is risk evidence for sensitive server calls, not a reason to
   break offline core functionality.
8. Ads and analytics are separate consent decisions. Do not add general-purpose
   analytics merely because the ads SDK can emit data.
9. Purchase, sync, and deletion failures must be visible and recoverable.
10. Every release claim distinguishes unit, emulator, Play-track, physical
    phone, real-Mac, and human acceptance evidence.

## Architecture

```text
Build capability AND compiled owner activation AND compatibility
       AND NOT emergency deny AND rollout eligibility
       AND required entitlement AND required consent AND user opt-in
                                |
                                v
                  typed CommercialDecision
                     (allowed + reason)

Credential Manager -> backend token verification -> Account
                                             |          |
Play Billing -> backend verification -> entitlement -> snapshots
                    ^          |
          request-bound Integrity + RTDN/reconciliation
```

### Root execution invariant and flag algebra

Commercial decisions are monotonic conjunctions, never "highest priority wins."
A less-trusted allow cannot defeat a deny from build capability, compiled owner
policy, compatibility/security policy, emergency control, entitlement, consent,
or explicit user opt-in.

```text
allowed = buildCapability
       && compiledOwnerActivation
       && compatible
       && !emergencyDeny
       && rolloutEligible
       && entitlementSatisfiedWhenRequired
       && consentSatisfiedWhenRequired
       && userOptedInWhenRequired
```

Every result is a typed `CommercialDecision` containing the surface, allowed
value, first denying reason, contributing evidence revisions, and safe support
detail. It never contains secrets, purchase tokens, account identifiers, ad
identifiers, snapshot contents, or rollout-assignment identifiers.

Keep local product/Labs flags separate from commercial policy. Commercial
concepts are distinct types: `BuildCapability`, `OwnerActivation`,
`Compatibility`, `EmergencyDeny`, `RolloutAssignment`,
`EntitlementProjection`, `ConsentState`, and `UserOptIn`. They are not stored in the existing
user-editable `Map<FeatureFlag, Boolean>`.

Unknown, malformed, expired, stale, corrupt, clock-invalid, or unavailable
inputs deny commercial execution. A last-known-good emergency deny may continue
to subtract access within a bounded TTL; no cache or remote value may add access
when the compiled owner policy denies it.

### Initial flag namespaces

```text
release.account
release.cloud_sync
release.play_billing
release.premium_enforcement
release.explore
release.ads_sdk

kill.account_sign_in
kill.cloud_upload
kill.cloud_download
kill.purchase_launch
kill.purchase_grant
kill.ads_all
kill.ads_routine_bank
kill.ads_theme_gallery
kill.ads_rewarded_preview

rollout.first_run_v2
rollout.connection_repair_v2
rollout.account_entry
rollout.cloud_restore
rollout.routine_bank
rollout.theme_gallery
rollout.advanced_ssh_actions

entitlement.pro
entitlement.sync
entitlement.ad_free
entitlement.premium_themes
entitlement.premium_routines
entitlement.advanced_ssh

preference.clipboard_live_sync
preference.deskdock
preference.theme_motion
preference.ads_personalization
```

`release.*` entries are compiled capability/owner-policy metadata, not remotely
writable flags. `kill.*` has one unambiguous meaning: `true` means denied.
`entitlement.*` is a backend projection, never Remote Config or local
SharedPreferences. Remote rollout and emergency controls are subtractive only.
The local-product entries `rollout.first_run_v2`,
`rollout.connection_repair_v2`, `preference.clipboard_live_sync`,
`preference.deskdock`, and `preference.theme_motion` stay in the local flag and
preference system; they are never inputs to a commercial allow decision.

Never remotely disable SSH host-key verification, credential encryption,
dangerous-command policy, confirmation requirements, diagnostic redaction,
account deletion, or subscription management.

### Commercial build boundary

Use one source tree and three explicit artifacts:

| Artifact | Package/signer | Commercial behavior |
| --- | --- | --- |
| `ossRelease` | `app.codecks`; current app-signing lineage | GitHub APK. Commercial SDK dependencies, components, initializers, and transports absent. Generic `androidx.credentials` may remain because Codecks already uses it for local SSH credential handling. |
| `playRelease` | `app.codecks`; Play app-signing certificate matching the existing lineage | Play AAB. Commercial implementations may be compiled, but `PRODUCTION_DARK` denies every commercial surface. No test override parser/verifier exists. No commercial SDK or network client starts at launch. |
| `playInternal` | `app.codecks.internal`; separate internal signer and test backend/project | Commercial E2E artifact. Compile-time test policy may allow signed/test-account overrides. It can never update, read, or clear `app.codecks`. |

Play-only SDKs live under `playImplementation`/Play source sets. Internal-only
override code lives under an internal source set unavailable to
`playRelease`. Both public artifacts remain unshrunk. Variant migration tests
prove eligible `app.codecks` in-place updates preserve local configuration.

The initializer audit targets only commercial startup paths: AndroidX Startup
entries, manifest providers and metadata, Firebase initializers, commercial
WorkManager jobs, eager DI construction, Billing, Integrity, UMP, Mobile Ads,
auth, sync, and operational-config clients. It does not prohibit Codecks' local
WorkManager jobs or other proven local-only startup behavior.

The upload certificate and Play app-signing certificate are different roles.
Update compatibility is decided by the Play app-signing certificate/lineage,
not by the upload certificate. T02 records all roles without exposing keys.

### Backend boundary

Use managed Google services behind typed interfaces for the first release:

- Credential Manager + Sign in with Google;
- a token-verifying auth service that keys accounts by verified Google `sub`,
  never email; avoid an eager Firebase Android client when direct Credential
  Manager token exchange can meet the contract;
- Cloud Run/Functions for account, sync, purchase, and deletion APIs;
- Firestore for versioned configuration snapshots and entitlement projections;
- Pub/Sub for Real-time Developer Notifications;
- Cloud KMS/Secret Manager for service credentials;
- request-bound Play Integrity for sensitive mutations. Integrity is requested
  immediately for a transaction and binds a canonical request hash; it never
  runs during app launch or gates offline/local core behavior.

No service credential or Play Developer API credential ships in the APK.
The backend is authoritative for entitlements. A purchase token is bound to one
account; pending purchases grant nothing; verification is persisted before
acknowledgement. RTDN is an untrusted change signal: handlers deduplicate it,
re-read Google Play state, apply an idempotent lifecycle transition, and support
scheduled reconciliation for missed or out-of-order events.

Account deletion is available before any internal or closed test can create an
account. Deletion reauthenticates, revokes all sessions first, blocks new cloud
mutations, deletes snapshots, minimizes or pseudonymizes strictly required
purchase/fraud records, and provides separate Play subscription-management
guidance. The in-app and public-web operations are idempotent and observable.

### Synced snapshot

The cloud codec uses explicit versioned DTOs and exhaustive domain adapters. No
reflection-based/general serializer, persistence-model upload, blacklist, or
"serialize then redact" path is allowed. Secret scanning is defense in depth,
not proof that a field is eligible.

Include in snapshot v1:

- schema/app version;
- deck slot identity/span and allowlisted catalog-action or safe local-route
  references, labels, icons, and colors;
- theme and layout preferences;
- disabled automations composed only of allowlisted typed/catalog steps;
- routine favorites and catalog references;
- non-secret Mac profile labels.

Exclude:

- SSH private keys, passwords, AI provider keys, clipboard content/history;
- host names/IPs/users, pinned host identity or secrets, connection settings;
- arbitrary command/test/cleanup strings, shell steps, execution authorization,
  command review, run/test/preflight receipts, histories, and output;
- notification contents, raw diagnostics, device identifiers;
- automation execution receipts and sensitive command output.

If one object contains an ineligible action, the encoder omits or rejects the
whole object with a visible reason; it never emits a redacted object that could
later become runnable. DTOs enforce byte, object-count, list-count, string,
depth, enum, and identifier bounds and deterministic canonical encoding.

Restore is `preview -> compatibility check -> local safety backup -> explicit
merge/replace -> validation -> commit`. Imported automations remain disabled and
must repeat local validation, Mac preflight, and live test.

### Commercial diagnostics contract

Support output may contain typed status/reason codes, bounded timestamps,
artifact/version metadata, operation phase, safe counts, and opaque correlation
IDs. It never contains email, Google subject, ID/refresh/access token, purchase
token or order ID, Integrity token, snapshot content, command text, clipboard,
ad ID, consent string, rollout-assignment ID, backend credential, or raw server
response. Snapshot diagnostics expose counts, schema, and checksum only.

## Dependency graph

```text
T01 product/privacy freeze
  |
  +--> T02 signing and Play ownership proof
  +--> T03 build-variant boundary
  +--> T04 typed flag contract
         |
         +--> T05 resolver + local migration
         +--> T06 remote flag adapter
         |
         +--> T07 account vertical
         |      +--> T08 deletion/session recovery
         |
         +--> T09 snapshot codec
                +--> T10 cloud backup
                +--> T11 restore/conflict

T01 + T07 --> T13a entitlement/RTDN contract + backend state machine
T02 + T03 + T07 + T13a --> T12 Play catalog + Billing client
T12 --> T13b sandbox verification + RTDN integration
                  +--> T14 entitlement UI/restore

T03 + T04 --> T15 consent/privacy
                  +--> T16 dark ad runtime
                  +--> T17 eligible placements

T04 --> T18 Explore/Routine Bank
     --> T19 Theme Gallery
     --> T20 Advanced typed SSH pack

T07-T20 --> T21 accessibility/reliability
        --> T22 privacy/store artifacts
        --> T23 internal Play test
        --> T24 closed test
        --> T25 production rollout
```

## Tasks

### T01 — Freeze product, data, and commercial contracts

**Description:** Record the tier matrix, account-optional rule, snapshot
allowlist, ad policy, grandfathering, deletion behavior, refund behavior, and
support boundary as versioned contracts.

**Acceptance criteria:**

- One source defines Free, Pro Lifetime, and Sync capabilities.
- Every stored/transmitted field is classified local, synced, excluded, or
  retained for a stated legal/security reason.
- Existing local users and canceled/expired/refunded users have explicit
  behavior.

**Verification:** Contract tests parse the matrices; privacy/security review
finds no unspecified data path.

**Dependencies:** None.

**Likely files:** `docs/product/`, `docs/security/`, `shared/src/commonMain/`.

**Estimated scope:** M.

### T02 — Prove signing continuity and configure Play ownership

**Description:** Create the Play app for `app.codecks`, enroll with the current
production app-signing key, create a separate upload key, and record
certificate-only evidence.

**Acceptance criteria:**

- Installed APK, current GitHub APK, and proposed Play app-signing certificate
  are identical or form an explicitly supported app-signing lineage.
- The built AAB is signed by the separately registered upload certificate. Its
  signer authenticates the upload and is never treated as update-compatibility
  or app-signing-lineage evidence.
- Signing keys are backed up outside the developer account; no secrets enter
  Git or logs.
- Any mismatch stops the program before an AAB is uploaded.

**Verification:** Certificate fingerprints and Play App Signing page are
reviewed; a locally signed `ossRelease` update preserves app data. T23 separately
verifies the Play-delivered `playRelease` signer and data-preserving update.

**Dependencies:** T01.

**Likely files:** `docs/release/`, private release environment only.

**Estimated scope:** S.

### T03 — Split FOSS and Play commercial variants

**Description:** Add explicit `ossRelease`, `playRelease`, and `playInternal`
source/dependency boundaries while preserving public package, core architecture,
migrations, and unshrunk releases.

**Acceptance criteria:**

- `ossRelease` has no account, billing, Integrity, UMP, ads, Firebase, or data
  transport component.
- `playRelease` is compile-time production-dark, contains no test-override path,
  and initializes no commercial service or transport at launch.
- `playInternal` uses `app.codecks.internal`, a separate signer, and a test
  backend; it cannot update or access protected `app.codecks` data.
- Variant switching by valid in-place upgrade preserves local configuration.

**Verification:** Dependency/manifest diffs, both builds, certificate check,
offline launch, migration test, and release-surface policy tests pass.

**Dependencies:** T01, T02.

**Likely files:** `app/build.gradle.kts`, source-set manifests,
`CodecksApplication.kt`, release validators.

**Estimated scope:** M.

### T04 — Replace flat booleans with a typed flag registry

**Description:** Keep local feature flags separate; create typed commercial
capability, compiled owner policy, rollout, emergency-deny, entitlement,
consent, and preference concepts.

**Acceptance criteria:**

- Every flag is declared once with owner, type, defaults, expiry, and safety
  behavior.
- Settings UI derives labels/groups from metadata; it does not duplicate a
  second catalog.
- A lint/test gate rejects duplicate keys, missing owners, invalid defaults,
  and expired temporary flags.

**Verification:** Registry unit tests and source-policy tests pass.

**Dependencies:** T01.

**Likely files:** `domain/commercial/`, `domain/features/`, `data/features/`,
Settings flag UI/tests.

**Estimated scope:** M.

### T05 — Implement deterministic flag resolution and migration

**Description:** Resolve every commercial surface through the monotonic AND
algebra, returning an explainable typed deny without allowing any lower-trust
source to override a higher-authority deny.

**Acceptance criteria:**

- Every decision returns value, winning layer, and reason without exposing
  secrets.
- Existing SharedPreferences schema v5 values migrate without changing user
  choices.
- Stale/missing/invalid sources fail safely and never brick navigation.

**Verification:** Table-driven precedence, migration, corruption, offline, and
clock-skew tests pass.

**Dependencies:** T04.

**Likely files:** `domain/commercial/`, `data/features/`, navigation guards.

**Estimated scope:** M.

### T06 — Add remote rollout and emergency controls

**Description:** Add a Play-only operational-config adapter with validated,
bounded local cache, bounded fetch, minimum-version handling, and redacted audit
export. It may use Remote Config later; it is not treated as signed or trusted
unless a custom signature contract is actually implemented.

**Acceptance criteria:**

- Cold offline launch uses last-known-good or compiled defaults.
- Remote values cannot grant paid entitlement or weaken mandatory security.
- Backend can stop sync upload, purchase launch/grant, and each ad placement
  independently without an app update.
- Public production defaults every commercial release flag off. Remote absence,
  failure, or ambiguity cannot activate a feature.
- Production-dark startup binds a no-op source and performs no commercial
  operational-config fetch.

**Verification:** Fake remote source tests cover malformed values, expiry,
rollback, fetch timeout, no network, and emergency disable.

**Dependencies:** T03, T04, T05.

**Likely files:** Play source set under `data/features/`, DI, support diagnostics.

**Estimated scope:** M.

### T07 — Ship optional sign-in end to end

**Description:** Add Credential Manager Google sign-in, server nonce/token
verification, session refresh, sign-out, and an Account screen.

**Acceptance criteria:**

- Local core works before sign-in, after sign-out, and during auth outage.
- Server validates ID token audience, issuer, signature, expiry, and nonce.
- Account UI explains what sync includes/excludes before consent.

**Verification:** Unit/UI tests, backend auth tests, Play-services unavailable
test, cancellation/retry, process death, and physical-device sign-in pass.

**Dependencies:** T03, T04, T05.

**Likely files:** Play auth/data/UI source sets, backend auth module, navigation.

**Estimated scope:** M.

### T08 — Add account lifecycle, deletion, and session recovery

**Description:** Implement revoke-all-sessions, reauthentication, in-app
deletion, public deletion endpoint/page, tombstone/retention policy, and
subscription guidance.

**Acceptance criteria:**

- User can delete account and associated cloud config without contacting a
  human.
- Deletion does not silently claim to cancel a Play subscription; management
  and cancellation paths are explicit.
- Deleted sessions cannot upload/download; retained fraud/legal fields are
  minimal and documented.
- In-app and public-web deletion are operational before any test track permits
  account creation.

**Verification:** Auth revocation, deletion idempotency, retry, active
subscription, and recreated-account tests pass; public URL works anonymously.

**Dependencies:** T07.

**Likely files:** Account UI, backend account APIs, public deletion page,
`PRIVACY.md`.

**Estimated scope:** M.

### T09 — Build the portable snapshot codec

**Description:** Create versioned, deterministic, size-bounded snapshot models
with an explicit field allowlist and secret scanning.

**Acceptance criteria:**

- Current config round-trips without secrets or execution proof.
- Arbitrary command/test/cleanup strings and shell actions are rejected; safe
  typed/catalog actions alone can enter snapshot v1.
- Unknown future fields are quarantined or ignored according to schema rules.
- Restore preview lists additions, replacements, conflicts, and unsupported
  items.

**Verification:** Golden fixtures, property/fuzz tests, legacy migration,
oversize, malformed, secret-canary, and downgrade tests pass.

**Dependencies:** T01, T04.

**Likely files:** `shared/`, backup repository, fixtures and tests.

**Estimated scope:** M.

### T10 — Add explicit cloud backup

**Description:** Upload immutable, checksummed snapshots on explicit save first;
add optional debounced auto-backup only after correctness is proven.

**Acceptance criteria:**

- UI shows last successful backup, pending changes, target account, failure, and
  retry.
- Upload is authenticated, idempotent, encrypted in transit/at rest, bounded,
  and never contains excluded fields.
- Local work is never blocked by cloud outage.

**Verification:** Backend authorization rules, duplicate/retry, offline,
conflicting device, quota, timeout, and account-switch tests pass.

**Dependencies:** T07, T09.

**Likely files:** Sync repository/UI, worker, backend snapshot API/DB rules.

**Estimated scope:** M.

### T11 — Add safe restore and multi-device conflict handling

**Description:** Provide latest-version restore, snapshot history, explicit
merge/replace, local rollback, and disabled automation import.

**Acceptance criteria:**

- Sign-in never auto-overwrites local data.
- Restore creates a local safety snapshot and can undo the last apply.
- Conflicts are shown by object; invalid actions and automations are
  quarantined.

**Verification:** Two-device E2E, concurrent edits, partial failure, corrupted
snapshot, downgrade, undo, and process-death tests pass.

**Dependencies:** T09, T10.

**Likely files:** Sync UI/domain/data, merge engine, backend snapshot API.

**Estimated scope:** M.

### T12 — Configure Play products and Billing client

**Description:** Create stable product IDs/base plans/offers and implement
Billing connection, product display, purchase launch, pending states, and
management links.

**Acceptance criteria:**

- Product name, price, period, renewal, trial, and cancellation terms come from
  current `ProductDetails`.
- App handles unavailable Play Store, canceled, pending, already-owned, and
  retry states.
- No entitlement is granted from client purchase state alone.

**Verification:** License tester flows on internal Play track and Billing
response matrix tests pass.

**Dependencies:** T02, T03, T07, approved T13a entitlement/RTDN contract.

**Likely files:** Play billing source set, Upgrade screen, Play Console catalog.

**Estimated scope:** M.

### T13 — Verify purchases and process lifecycle events

**Description:** First freeze and implement the backend entitlement state
machine, token ownership, RTDN idempotency, reconciliation, and API contract
(T13a). Then integrate purchase tokens plus transaction-bound Integrity,
Google Play Developer API verification, persisted acknowledgement, RTDN, and
voided purchases after the Billing client exists (T13b).

**Acceptance criteria:**

- Unique purchase token can belong to only one account.
- Pending purchase grants nothing; purchase, renewal, grace, hold, pause,
  cancel, expire, refund, revoke, and chargeback produce correct entitlement.
- Duplicate/out-of-order RTDN is idempotent and reconcilable.

**Verification:** Backend contract/integration tests, replay/tamper tests,
sandbox purchase lifecycle, RTDN redelivery, and daily reconciliation pass.

**Dependencies:** T13a depends on T01 and T07 contracts; T13b depends on T12.

**Likely files:** Backend billing API, Pub/Sub handler, entitlement store,
Android purchase repository.

**Estimated scope:** M.

### T14 — Deliver entitlement, restore-purchase, and management UX

**Description:** Resolve server entitlements into app capability gates with
offline grace, visible source/expiry, restore, manage, and support diagnostics.

**Acceptance criteria:**

- Pro/Sync/ad-free state updates without reinstall and survives reasonable
  offline periods.
- Expired or revoked cloud entitlement never destroys local data.
- User can restore purchases, manage subscription, see last verification, and
  export a redacted billing diagnostic ID.

**Verification:** Clock, cache, offline grace, account switch, refund, stale
token, reinstall, and variant-switch tests pass.

**Dependencies:** T05, T13.

**Likely files:** Entitlement repository, Upgrade/Account UI, diagnostics.

**Estimated scope:** M.

### T15 — Update privacy, consent, and audience handling

**Description:** Isolate UMP in the Play variant, declare audience/content
rating, add persistent privacy options, and update Data Safety/privacy
disclosures for auth, sync, billing, Integrity, and ads.

**Acceptance criteria:**

- Production-dark launch never initializes or calls UMP. After separate ad
  activation, consent is requested/refreshed only before an approved discovery
  surface could request an ad.
- User can reopen privacy choices from Settings.
- EEA/UK/Swiss, California/US-state, under-age, unknown geography, offline, and
  consent-error behavior is explicit.

**Verification:** UMP test geography/device flows, Data Safety SDK audit,
privacy-policy review, and consent-withdrawal test pass.

**Dependencies:** T01, T03, T07.

**Likely files:** Play privacy source set, Settings, `PRIVACY.md`, Play Console.

**Estimated scope:** M.

### T16 — Build a dark, entitlement-aware ad runtime

**Description:** Add Mobile Ads behind consent, entitlement, placement, and
master kill switches, using test ads until Play review readiness.

**Acceptance criteria:**

- Production-dark never initializes the Ads SDK. After separate activation, it
  initializes lazily only after owner policy, placement, and consent allow it.
- Pro/Sync users, forbidden screens, background state, screen-off state, and
  lockscreen never request ads.
- Empty/error/slow ads reserve no unstable tappable layout; diagnostics expose
  placement eligibility without ad identifiers.

**Verification:** Fake adapter and official test-ad E2E cover all eligibility
combinations, lifecycle, frequency caps, and kill switches.

**Dependencies:** T05, T06, T14, T15.

**Likely files:** Play ads source set, entitlement-aware policy, DI/tests.

**Estimated scope:** M.

### T17 — Add only approved ad placements

**Description:** Render labeled native discovery cards and optional rewarded
preview without contaminating control workflows.

**Acceptance criteria:**

- Native ad is never before six organic items, next to action controls, or
  inserted after the user begins a gesture.
- Rewarded preview is opt-in, names the exact temporary benefit and expiry, and
  degrades cleanly when unavailable.
- No banner, interstitial, app-open, overlay, widget, notification, or
  operational-surface ad exists.

**Verification:** Screenshot/accessibility tests, accidental-click spacing,
rotation/DeX, slow-load layout stability, and Play ad-policy review pass.

**Dependencies:** T16, T18, T19.

**Likely files:** Explore/Theme UI in Play source set, ad placement tests.

**Estimated scope:** M.

### T18 — Create the Routine Bank/Explore surface

**Description:** Turn curated automations/actions into a searchable, previewable,
versioned discovery surface with compatibility and permission preflight.

**Acceptance criteria:**

- Users can inspect source, requirements, Mac capability, permissions, safety
  level, and test expectations before install.
- Imported routines enter disabled draft state and never inherit someone
  else's execution receipt.
- Free/premium ownership and update behavior are explicit.

**Verification:** Catalog signature/schema, install/update/remove, offline cache,
incompatible Mac, permission denial, and malicious payload tests pass.

**Dependencies:** T01, T04, T05.

**Likely files:** Routine catalog domain/data/UI, shared schema, backend/static
catalog.

**Estimated scope:** M.

### T19 — Create a coherent Theme Gallery

**Description:** Package full-app visual systems—not isolated colors—with
preview, accessibility metadata, ownership, download, and rollback.

**Acceptance criteria:**

- Theme affects all intended surfaces consistently and previews before apply.
- Contrast, large text, OLED, motion reduction, portrait/landscape, and DeX
  constraints are declared and tested.
- Corrupt/removed premium theme falls back safely without losing user layout.

**Verification:** Golden screenshots, accessibility checks, package validation,
offline ownership cache, refund, and rollback tests pass.

**Dependencies:** T01, T04, T05, T14.

**Likely files:** Theme domain/data/UI, theme package schema, Settings/Explore.

**Estimated scope:** M.

### T20 — Curate advanced typed SSH actions

**Description:** Add useful Mac actions only through typed, parameterized,
preflighted adapters; do not sell arbitrary shell execution.

**Acceptance criteria:**

- Each action declares inputs, supported macOS/tool versions, permissions,
  timeout, output limits, confirmation, and undo/recovery.
- Missing tools or permissions produce repair instructions.
- Premium gating affects curated convenience; existing safe local actions remain
  usable.

**Verification:** Command-injection corpus, fake/real-Mac preflight, timeout,
partial failure, unsupported-version, and undo tests pass.

**Dependencies:** T01, T04, T05, T14.

**Likely files:** Typed action contracts/providers, Mac helper/SSH adapters,
Routine Bank.

**Estimated scope:** M.

### T21 — Close GA reliability and accessibility gaps

**Description:** Finish first-run setup, reconnection, diagnostics, clipboard,
automation proof, backup migration, TalkBack, large text, switch access, DeX,
battery, and device/macOS matrices.

**Acceptance criteria:**

- Setup and repair explain exact failures; reconnect survives documented
  lifecycle transitions.
- Core surfaces meet accessibility and adaptive-layout requirements.
- Every physical claim has device/Mac evidence; no emulator result is mislabeled.

**Verification:** Existing release gates plus physical Android 12–16, Samsung
DeX, current macOS, long-session, battery, clipboard, HID, SSH, and accessibility
matrices pass.

**Dependencies:** T03–T20 relevant slices.

**Likely files:** Core product verticals, tests, `docs/release/`.

**Estimated scope:** Multiple M tasks; split by existing GA gate.

### T22 — Complete Play listing, policy, support, and operations

**Description:** Prepare store listing, screenshots, privacy/Data Safety, ads
declaration, content rating, subscription terms, deletion URL, support,
vulnerability intake, key recovery, rollback, and incident runbooks.

**Acceptance criteria:**

- Store claims match shipped flags and verified behavior.
- All SDK data collection appears in Data Safety and privacy policy.
- Billing, sync, ad, account, security, and outage support paths are rehearsed.

**Verification:** Play pre-review checks, SDK Index review, policy checklist,
clean-account support drill, and release rollback rehearsal pass.

**Dependencies:** T01–T21.

**Likely files:** `fastlane/`, `docs/release/`, `PRIVACY.md`, support website.

**Estimated scope:** M.

### T23 — Run internal capability and exact-artifact dark tests

**Description:** Exercise commercial flows with `playInternal` against test
services, then upload the exact production-dark `playRelease` AAB to validate
delivery, signing, migrations, and inert public behavior.

**Acceptance criteria:**

- `playInternal` proves sign-in, purchase, RTDN, restore, sync, deletion, flags,
  and consent without touching `app.codecks`.
- Play-delivered `playRelease` updates safely and matches the expected
  app-signing certificate.
- All purchase lifecycle and backend paths have real sandbox receipts.
- Ads use official test units; production ad serving remains killed.
- `playRelease` has no override parser/verifier and proves commercial routes are
  unreachable with zero commercial SDK construction or network request.

**Verification:** Internal capability evidence plus exact-production-artifact
dependency/manifest scan, adversarial state/intent tests, cold-start network
capture, and in-place data-preservation test pass.

**Dependencies:** T02–T22.

**Likely files:** Evidence only; no ad-hoc production changes.

**Estimated scope:** S plus observation.

### T24 — Run closed test and production-access gate

**Description:** Conduct the required closed test if the developer account is
subject to it, while measuring setup completion, reconnect recovery, crashes,
ANRs, battery, migration, and production-dark inertness. Commercial lifecycle
testing remains isolated to `playInternal`.

**Acceptance criteria:**

- Play Console testing requirement is satisfied when applicable.
- No open P0/P1; crash-free sessions meet the repository GA target.
- Testers prove core Mac workflows and zero production commercial reachability;
  linked `playInternal` evidence covers each commercial lifecycle.

**Verification:** Play Console status, tester matrix, issue ledger, and evidence
review pass.

**Dependencies:** T23.

**Likely files:** Test evidence and targeted fixes only.

**Estimated scope:** Minimum policy window plus fixes.

### T25 — Stage production with every commercial surface dark

**Description:** Roll out the production-capable Play build in bounded stages
while account, cloud sync, Billing, premium enforcement, and ads remain disabled.

**Acceptance criteria:**

- Start at the smallest Play percentage available, observe, then expand only
  when crash/ANR, auth, sync, purchase, refund, and support thresholds pass.
- Public production exposes no account, cloud, upgrade, subscription, premium
  lock, consent, or ad surface and initializes no commercial SDK.
- Compiled defaults, remote defaults, backend policy, and kill switches all
  agree on disabled; a single mistaken value cannot activate a surface.
- The staged AAB digest is byte-for-byte the digest admitted by T23/T24. No
  rebuild occurs between testing, review, promotion, and rollout.
- A later activation requires explicit owner approval and its own staged
  rollout. This task grants no activation authority.

**Verification:** Stage-by-stage release ledger, exact artifact digest, zero commercial UI
reachability, zero commercial SDK/network initialization, unchanged local-core
screens, support review, and final certificate/artifact checks pass.

**Dependencies:** T24.

**Likely files:** Release evidence and operational configuration.

**Estimated scope:** Staged observation.

### T26 — Make separate post-launch activation decisions

**Description:** After stable production, decide independently whether to
activate account, sync, Billing, premium entitlements, and ads. No decision is
implied by completed implementation.

**Acceptance criteria:**

- Each surface has a written go/no-go decision, current policy/data review,
  owner, rollout percentage, health thresholds, and rollback trigger.
- Account may activate without sync; account/sync may activate without Billing;
  Billing may activate without ads. Ads always remain a separate last decision.
- Any activated surface is canaried alone and can be killed independently.

**Verification:** Written decision and surface-specific activation/rollback
drill. `Keep disabled` is a valid result.

**Dependencies:** T25 and a stable observation period.

**Likely files:** Operational decision record; remote configuration only after
approval.

**Estimated scope:** S.

### Decisions intentionally deferred

These do not block dark implementation: prices, trial/intro offers, final Play
product merchandising, production ad unit IDs, later activation per surface,
ad audience/regions, cloud region/retention duration, and catalog premium split.
They become blocking before their affected surface is activated.

These cannot defer past Checkpoint A0: variant matrix, app-signing lineage,
root gate algebra, snapshot allowlist, backend identity key, deletion semantics,
entitlement authority/state machine, commercial initializer policy,
transaction-bound Integrity contract, cryptographic internal-test boundary, and
unshrunk release policy.

## Parallel delivery lanes

Every implementation lane uses its own `codex/` branch and worktree. One
integration lane owns shared hot spots. Agents never merge, push, release,
install, alter Play Console, or touch the protected phone unless a separately
authorized integration/release task says so.

### Phase 0 — Contracts and proof boundaries

Run these in parallel, then stop at Checkpoint A0:

| Lane | Exclusive ownership | Deliverable |
| --- | --- | --- |
| C0 Policy | `app/**/domain/commercial/**`, commercial additions under `domain/features/**`, focused tests | Immutable policy, typed algebra, decision reasons, property tests |
| C1 Snapshot | `shared/**/snapshot/**`, `protocol/schemas/commercial/**`, fixtures | DTO allowlist, bounds, canonical codec, rejection taxonomy |
| C2 Backend contract | new `backend/**`, commercial OpenAPI/schema except snapshot DTO | Account/session/deletion, entitlement state machine, RTDN/reconciliation, Integrity request binding |
| C3 Build audit | read-only Gradle/manifest/signing analysis | Variant/dependency/initializer/signing contract and exact commands |

Checkpoint A0 freezes the variant matrix, root gate algebra, initializer policy,
snapshot schema, identity key, deletion behavior, entitlement authority/state
machine, Integrity binding, no-shrink rule, and test-only boundary.

### Phase 1 — Foundations

After A0, run in parallel:

| Lane | Exclusive ownership | Work |
| --- | --- | --- |
| F0 Policy/data | `domain/commercial/**`, `data/features/**`, tests | Resolver, migration, no-op production source, diagnostics |
| F1 Portable snapshot | `shared/**/snapshot/**`, `data/sync/codec/**`, tests | Codec/adapters/goldens/fuzz/canaries |
| F2 Backend core | `backend/**` | Auth, sessions, deletion, snapshots, entitlement/RTDN state machines |
| F3 Build boundary | `app/build.gradle.kts`, source-set manifests, commercial startup validators | `ossRelease`/`playRelease`/`playInternal`, dependency and initializer gates |

Only F3 edits Gradle, manifests, `CodecksApplication`, startup providers, or DI
composition roots. Generic `androidx.credentials` remains OSS-safe.

### Phase 2 — Vertical implementations

After foundation contract tests pass, run in parallel:

| Lane | Exclusive ownership | Work |
| --- | --- | --- |
| V0 Account | `app/src/play/**/auth/**`, Account UI/tests | Credential Manager exchange, session recovery, sign-out/deletion |
| V1 Sync | `app/src/play/**/sync/**`, Sync UI/tests | Explicit backup, preview/merge/replace/rollback/conflicts |
| V2 Commerce | `app/src/play/**/billing/**`, Upgrade UI/tests | ProductDetails, purchase flow, backend verification, restore/manage |
| V3 Ads/privacy | `app/src/play/**/ads/**`, `privacy/**`, tests | Dark UMP/Ads adapters, consent, eligibility, forbidden surfaces |
| V4 Product value | isolated Explore/Theme/typed-SSH packages | T18–T20 without commercial hot-spot edits |

The integration owner alone edits navigation, `MainActivity`, `SettingsScreen`,
shared DI, versioning, migration dispatch, Explore/Theme placement composition,
and release workflows.

### Contract-request protocol

Before changing another lane's contract, add one file under
`tasks/contract-requests/` containing requester, owner, exact symbol/schema,
reason, compatibility effect, migration effect, tests, and requested decision.
The owning lane records `accepted`, `revised`, or `rejected`. No worker resolves
an ownership collision by editing both sides. Contract changes invalidate
dependent receipts and must rerun the owning and consumer test suites.

### Required review cycle per phase

1. implementation lane reports files and focused gates;
2. independent verification reruns claimed checks and inspects the diff;
3. anti-pattern review searches bypasses, duplicate catalogs, eager startup,
   unsafe serialization, and test-only leakage;
4. quality review closes P0/P1 findings;
5. only then may an integration task commit the phase.

## Checkpoints

### Checkpoint A — Foundation

- T01–T06 complete.
- All three artifacts build and their dependency/manifest boundaries pass.
- Signing continuity is proven.
- Existing local flags and app data migrate.

### Checkpoint B — Account and sync

- T07–T11 complete.
- Optional sign-in and explicit backup/restore work across two test accounts and
  two devices.
- Account deletion works in-app and on web.
- Secret-canary suite finds no excluded data in cloud.

### Checkpoint C — Commerce

- T12–T14 complete.
- Real Play sandbox purchases, RTDN, refund/revoke, restore, offline grace, and
  account switching pass.
- No client-only entitlement path exists.

### Checkpoint D — Ads dark launch

- T15–T17 complete.
- Consent and privacy controls pass.
- Production ad units remain disabled.
- Forbidden-surface scan and ad-request instrumentation show zero requests from
  operational surfaces.

### Checkpoint E — Product and GA

- T18–T22 complete.
- Core reliability/accessibility matrices pass.
- Store listing and policies reflect the commercial build.

### Checkpoint F — Dark Play release

- T23–T25 complete.
- Internal and closed test evidence pass.
- Production staged with account, sync, Billing, premium enforcement, and ads
  all disabled and unreachable.
- T26 remains a separate post-stability owner decision for each surface.

## Release gates

No public production release until:

- exact AAB source SHA, version, signer lineage, and Play-delivered certificate
  are recorded;
- `ossRelease` and `playRelease` are both unshrunk and pass release-surface,
  secret, unit, lint, managed-emulator, and migration checks;
- `ossRelease` dependency and merged-manifest scans prove no Firebase,
  commercial auth, Billing, Integrity, UMP, Mobile Ads, commercial provider,
  receiver, service, worker, metadata, or transport leaked in;
- exact `playRelease` cold-start capture proves zero commercial network calls
  and construction spies prove no commercial SDK/client initialization;
- corrupt preferences/cache/config, expired values, clock skew, crafted deep
  links/intents, saved navigation, restored state, and test-override replay
  cannot enable any production-dark surface;
- property tests prove a lower-authority allow never defeats build, owner,
  emergency-deny, entitlement, consent, or user-opt-in denial;
- snapshot golden/property/fuzz tests cover all bounds, future/old schemas,
  raw-command rejection, secret canaries, corruption, rollback, and process
  death;
- auth tests cover token audience/issuer/signature/expiry/nonce/replay, session
  rotation/revocation, idempotent deletion, and recreated account;
- Play sandbox evidence covers pending, purchase, renewal, grace, hold, pause,
  cancellation, expiration, refund, revoke, chargeback, account switch, RTDN
  duplicate/out-of-order delivery, and reconciliation;
- ad tests assert zero requests—not only zero rendering—on every forbidden
  surface, background, lockscreen, screen-off, rotation, and DeX state;
- physical-phone update preserves `app.codecks` data;
- real-Mac SSH and Bluetooth HID work on the exact candidate;
- sign-in, backup, restore, purchase, RTDN, refund, account deletion, consent,
  and kill-switch flows have `playInternal`/test-backend evidence;
- privacy policy, Data Safety, ads declaration, content rating, store assets,
  support URL, and deletion URL are live;
- no unresolved P0/P1 exists;
- rollback has been rehearsed.

The admitted artifact record contains Git commit, Gradle inputs, version,
SHA-256 digest, app-signing lineage, dependency/manifest reports, and evidence
bundle digest. Play promotion reuses that exact AAB digest; rebuilding resets
admission.

## Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Wrong Play signer | Existing installs cannot update | T02 hard stop before upload |
| Open-source client is modified | Local UI gates bypassed | Protect official services server-side; never claim unmoddable |
| Commercial SDK contaminates FOSS build | Trust/privacy regression | T03 source-set and manifest dependency gates |
| Sign-in overwrites local config | Data loss | Preview, local safety snapshot, explicit merge/replace |
| Purchase token replay/account theft | Revenue/support loss | Backend verification, unique token, request-bound Integrity, RTDN |
| Remote flag becomes authorization | Paid/security bypass | Monotonic conjunction; remote controls only subtract access |
| Ad causes accidental control click | Safety/policy failure | No operational ads; fixed discovery placement only |
| Dark feature accidentally activates | Privacy/commercial incident | Compiled, remote, backend, and kill-switch deny; reachability/network tests |
| Ads destroy local-first trust | Retention loss | Ads off; separate T26 evidence-based go/no-go |
| Subscription lacks recurring value | Churn/policy risk | Sell cloud/content maintenance; keep finite features in Lifetime |
| Account deletion conflicts with billing | User harm | Explain Play cancellation separately; deletion remains available |
| Cloud snapshot leaks secrets | Severe privacy issue | Allowlist codec, whole-object rejection, canaries, server limits, security review |
| Backend outage breaks core | Product outage | Local core independent; cached entitlement grace; kill switches |

## Rough critical path

- Contracts, signing, variants, flags: 1–2 focused weeks.
- Account, backend, sync, billing, entitlement: 3–5 weeks with parallel lanes.
- Consent, dark ads, Explore/themes/SSH packs, GA closure: 2–4 weeks.
- Internal testing and fixes: 1–2 weeks.
- Closed testing: add the Play-mandated window when applicable.

These are engineering estimates, not release promises. Play review, tester
eligibility, production-access approval, and policy review are external.

## Final definition of done

The implementation program is done when T01–T25 and all checkpoints have
evidence. Account, sync, Billing, premium enforcement, and ads still remain off.
T26 is not automatic execution: each surface may remain disabled indefinitely.
