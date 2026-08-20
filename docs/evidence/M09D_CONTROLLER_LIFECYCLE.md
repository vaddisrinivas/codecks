# M09D controller lifecycle proof

Three immutable phases:

1. C1 source: exactly nine paths from base `91e4ff965737df8bbc68e5e3bb9b6852e85fc4a0`, including the pinned wrapper properties. Source review and commit first.
2. C2 artifacts: from clean C1, run the exact filtered Gradle command and commit only canonical JUnit XML, sanitized log, and artifact manifest.
3. C3 receipt: from clean C2, bind the exact C1/C2 topology, bytes, source symbols, test class/methods, tool hashes, JDK command, and compiled production/test tree digests. Commit receipt only.

Each phase requires an exactly clean worktree. Validation compares every C1, C2, and C3 working file byte-for-byte with `git show <phase-commit>:<path>` and requires each phase commit's changed-path closure to equal its declared path set.

Execution is closed to tokenized `$ANDROID_HOME`, `$JAVA_HOME`, `$HOME`, task-owned `$GRADLE_USER_HOME`, allowlisted `$GRADLE_RO_DEP_CACHE`, fixed `PATH`, locale, and empty JVM/Gradle option variables. The bound local Gradle binary runs directly with `--offline`; OS-level network denial is `NOT_PROVEN` and is not an admission dependency. It never invokes the wrapper. Pre/post source, index, required SDK, immutable toolchain, wrapper, ZIP/extracted tree, and dependency manifests must remain equal; the entire writable Gradle home is separately bound before and after. JVM outputs are parsed; Gradle Launcher and Daemon JVM must equal `$JAVA_HOME`. Init scripts, substituted `gradle.properties`, and leaked private paths anywhere in C2 text/JSON are rejected.

Gradle 9.4.1 checksum content source: `https://gradle.org/release-checksums/`. Its retained body hash and the matching distribution checksum are bound, but acquisition authentication is explicitly `NOT_PROVEN` because redirect/status/header evidence was not retained. Official distribution URL: `https://services.gradle.org/distributions/gradle-9.4.1-bin.zip`; redirect asset: `https://github.com/gradle/gradle-distributions/releases/download/v9.4.1/gradle-9.4.1-bin.zip`. ZIP SHA-256 `2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb` is pinned as `distributionSha256Sum`.

Exact C1 command:

```sh
env -i ANDROID_HOME="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" HOME="$HOME" GRADLE_USER_HOME="$GRADLE_USER_HOME" GRADLE_RO_DEP_CACHE="$GRADLE_RO_DEP_CACHE" PATH="$JAVA_HOME/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin" LANG=C.UTF-8 LC_ALL=C.UTF-8 GRADLE_OPTS= JAVA_TOOL_OPTIONS= _JAVA_OPTIONS= "$GRADLE_DISTRIBUTION_ROOT/bin/gradle" :app:validateReleaseSurface :app:testOssReleaseUnitTest --tests io.codecks.m09d.M09DControllerLifecycleTest --rerun-tasks --no-build-cache --no-configuration-cache --no-daemon --offline
PYTHONDONTWRITEBYTECODE=1 python3 tools/evidence/test_m09d_controller_lifecycle.py
```

After independent C1 approval and commit:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/evidence/collect_m09d_controller_lifecycle.py --artifacts
```

After independent C2 approval and commit:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/evidence/collect_m09d_controller_lifecycle.py --receipt
```

After C3 commit:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tools/evidence/validate_m09d_controller_lifecycle.py
```

The dedicated ten-test class proves Home edit/assign, reassign-clears-undo, move/remove with real `undoLastDeckEdit` restoration, AI create/test/refine/save-only/delete through repository-backed controller recreation proxies, and the production AppCompositionRoot placement bridge driving Home placement and its repository save path. Save-only starts with an absent artifact, generates and persists it, calls the non-null-ID `markSavedOnly` branch, proves repository bytes unchanged and placement absent, then recreates the repository proxy from those exact bytes.

Repository recreation is a controller-lifecycle proxy, not durable disk or process-recreation proof. AI delete is intentionally non-undoable.
