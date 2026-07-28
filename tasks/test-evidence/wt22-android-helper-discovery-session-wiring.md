# WT22 Android helper discovery/session wiring

## Scope

- Added Android `MdnsReactiveHelperDiscovery` for `_codecks-reactive._tcp`.
- Added encrypted Android helper credential store:
  - stores pinned helper identities in DataStore.
  - stores shared secret material through existing Android Keystore-backed secure store.
  - exposes only secret bytes by alias.
- Added `StateFlowReactiveHelperActionClient`.
- Added `StateFlowReactiveHelperClientMacStateSource`.
- Exposed authenticated `ReactiveHelperClient` from `ReactiveHelperSessionManager`.
- Wired `MainActivity` to:
  - start helper discovery.
  - connect to the first discovered helper when a selected Mac has a pinned identity.
  - pass live helper action client into `DefaultReactiveActionExecutor`.
  - pass live helper state source into `LiveMacStateRepository`.
- Added Bonjour advertising to the Mac TCP helper listener.

## Verification

```text
./gradlew :app:testReleaseUnitTest \
  --tests 'io.codecks.platform.helper.ReactiveHelperSessionManagerTest' \
  --tests 'io.codecks.platform.helper.ReactiveHelperTcpTransportTest' \
  --tests 'io.codecks.data.reactive.LiveMacStateRepositoryTest' \
  --tests 'io.codecks.core.reactive.DefaultReactiveActionExecutorTest' \
  -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks \
  -PreleaseKeyAlias=unit \
  -PreleaseStorePassword=unit \
  -PreleaseKeyPassword=unit
```

Result: `BUILD SUCCESSFUL`.

```text
cd macHelper && swift test
```

Result: 24 tests passed, 0 failures.

```text
git diff --check
scripts/verify_release_no_shrink.sh
```

Result: clean diff check; release no-shrink invariant verified.

## Remaining

- First-time pairing UI/QR/import path still not built.
- Live phone + Mac helper validation not run.
