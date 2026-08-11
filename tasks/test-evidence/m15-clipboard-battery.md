# M15 clipboard/background/battery implementation evidence

Scope: CPU implementation only. No emulator, physical phone, protected package, Mac sleep/wake, release, install, or background-service mutation.

Implemented:

- Last clipboard result appears near the top of Clipboard.
- Active Battery Saver exposes a direct settings action and states the visible/unlocked limitation.
- Every Mac-to-phone clipboard write carries `ClipDescription.EXTRA_IS_SENSITIVE`; the label is generic.
- Existing visible-only 15-minute authority, process-death reset, bounded retry, duplicate suppression, conflict ordering, last-sync privacy, and no-worker/no-wake-lock rules are source-bound.

CPU command:

`ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew :app:testOssReleaseUnitTest --tests 'io.codecks.ui.clipboard.M15ClipboardCharacterizationTest' :app:compileOssReleaseAndroidTestSources --no-daemon --stacktrace`

Result: 10/10 unit tests passed; OSS release Android-test sources compiled.

Managed proxy command:

`ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew :app:pixel6Api35PlayInternalReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.codecks.ui.clipboard.M15ClipboardPrivacyInstrumentedTest --no-daemon --stacktrace`

Result metadata records 2/2 passed on the isolated `app.codecks.internal` API-35 managed device. The checked-in artifact is sanitized JUnit metadata; APK and executed-binary digests are not bound. It does not prove Samsung clipboard UI behavior.

Held `NOT_RUN`: emulator foreground/background, screen off/on/lock, battery restriction; current-Mac sleep/wake/reconnect; physical Samsung clipboard toast; physical battery characterization.
