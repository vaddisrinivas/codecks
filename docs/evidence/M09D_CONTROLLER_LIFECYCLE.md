# M09D controller lifecycle proof

Three immutable phases:

1. Prior source `b4482ba03feaceebe09c4fd9690897c93dc92a40` preserves the reviewed exact-six lifecycle evidence source. This append-only sanitizer source phase changes exactly five evidence paths and is direct-parented by `b4482ba`.
2. Future C2 artifacts: from clean sanitizer source, run the exact filtered Gradle command and commit only deterministic sanitized JUnit XML, canonical sanitized log, and artifact manifest; C2 must be direct-parented by sanitizer source.
3. Future C3 receipt: from clean C2, bind the exact source/C2 topology, bytes, source symbols, test class/methods, tool hashes, JDK command, and compiled production/test tree digests. Commit receipt only.

Each phase requires an exactly clean worktree. Validation compares every C1, C2, and C3 working file byte-for-byte with `git show <phase-commit>:<path>` and requires each phase commit's changed-path closure to equal its declared path set.

Execution is closed to tokenized `$ANDROID_HOME`, `$JAVA_HOME`, `$HOME`, task-owned writable `$GRADLE_USER_HOME`, host-local mutable `$GRADLE_RO_DEP_CACHE`, fixed `PATH`, locale, and empty JVM/Gradle option variables. The dependency-cache aggregate is measured before and after execution and must remain equal solely as an execution-stability check. Its origin and immutability are `NOT_PROVEN` and are not admission provenance. The bound local Gradle binary runs directly with `--offline`; OS-level network denial is `NOT_PROVEN`. Before running, the focused raw JUnit result is deleted and required absent. Start/end timestamps bracket Gradle; the new result mtime, UTC suite timestamp, and macOS birthtime when available must lie within that interval, and its inode is recorded. On filesystems without birthtime the new-file proof is `NOT_PROVEN`; even with birthtime this is bounded local evidence, not protection from a same-user attacker.

The raw XML is strictly parsed and privacy-scanned everywhere except its producer hostname value, then immediately transformed by sanitizer `EXACT_HOSTNAME_ATTRIBUTE_REPLACEMENT` version `1`: exactly one literal hostname attribute value is replaced with `$HOST`, with every other byte preserved. Raw XML is never durable. C2 retains only sanitized XML. The artifact manifest records sanitizer algorithm/version/token, sanitized SHA-256, freshness metadata, `rawMaterialRetention=NOT_RETAINED`, and `rawTransformationRevalidation=NOT_POSSIBLE`. It records no raw material or hostname, so final validation makes no claim that it can reprove the raw transformation. Final validation requires the exact `$HOST` token and rejects real host/private values. The closed XML grammar and privacy rules remain unchanged for all durable bytes.

The durable log is a fixed canonical projection of direct subprocess stdout/stderr: command header, release task, focused-test task, `50 actionable tasks: 50 executed`, and normalized `BUILD SUCCESSFUL`, in that order. Final validation requires the sanitizer to be byte-idempotent, so duplicates, warnings, extra/evil tasks, reordering, and suffixes are rejected.

Named manifest aggregates appear once and references are checked directly without recursive expansion. Full external path-entry arrays are excluded from C2; it stores only capped `{root, files, bytes, digest}` aggregates plus the minimal executable paths/hashes needed for binding. Collection and final validation recompute the distribution, dependency-cache, JDK, SDK, Gradle-home, and compiled-tree aggregates. Internal traversal requires sorted, unique, canonical POSIX/NFC/casefold-safe paths and exact integer counts. ZIP EOCD/central-directory bounds are checked before `ZipFile`; ZIP streaming, JDK/tree hashing, artifact serialization, and JSON loading fail closed on byte/count limits. Artifact and C3 receipt JSON are capped at 1 MiB before parsing.

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
