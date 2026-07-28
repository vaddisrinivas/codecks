# WT15D Android helper TCP transport

Status: implemented and focused-test verified.

Changed:
- Added `TcpReactiveHelperTransportFactory`.
- Added `TcpReactiveHelperTransport`.
- Transport writes existing `ReactiveFrameCodec` frames and reads one framed helper response.
- Response frame length is bounded by `REACTIVE_MAX_BODY_BYTES`.
- Connect failure closes the socket.
- Wired Spotlight controls to helper action `spotlight.search` instead of the local preview-only action.

Safety:
- No physical phone used.
- No production app touched.
- No secrets logged.
- No release shrink settings changed.

Verification:
- `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.platform.helper.ReactiveHelperTcpTransportTest' --tests 'io.codecks.platform.helper.ReactiveHelperSessionManagerTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`
  - Passed.
- `./gradlew :app:testReleaseUnitTest --tests 'io.codecks.platform.helper.ReactiveHelperTcpTransportTest' --tests 'io.codecks.platform.helper.ReactiveHelperSessionManagerTest' --tests 'io.codecks.domain.reactive.providers.SpotlightSftpReactiveControlProviderTest' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit`
  - Passed.
- `git diff --check`
  - Passed.
