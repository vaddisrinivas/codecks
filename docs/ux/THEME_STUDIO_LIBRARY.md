# Theme Studio and icon library

Theme Studio is an offline, preview-first appearance tool. It ships twelve accessible presets, visual role swatches, scoped Deck and Trackpad overrides, and a bounded named library stored in the app's private DataStore.

- Preset and color controls expose radio semantics and 48dp minimum targets.
- Raw `#FFRRGGBB` and opacity controls stay under **Advanced**.
- A maximum of 12 custom themes is retained. Names are trimmed, bounded, unique ignoring case, and contain no control characters.
- Library JSON is closed-field, size bounded, contrast checked, and migrates the prior v1 `label` field to v2 `name` without accepting unknown fields.
- Import/export remains local JSON. It never executes commands, reads files, or contacts a service.
- Icon previews search stable semantic actions; each supported pack resolves those actions through dependency symbols verified by compilation.

M09D proof is bounded to the Play-internal repository lifecycle test. It does not prove a broader controller, process lifecycle, physical device, or public release; those remain `NOT_RUN`.

The durable M09D receipt contains exactly eight fresh runtime artifacts (two APKs plus XML, device-info, and sanitized textproto for phone and tablet), one fresh detector artifact, and one retained excluded-attempt XML. It binds the exact source diff, APKs, final 13-test results, API 35 topology, and empty Impeccable result. Reproduce the detector gate after the last UI edit with:

```sh
python3 -B tools/evidence/run_m09d_impeccable_detector.py --detector /absolute/path/to/detect.mjs
```

The detector path is never persisted; its filename and SHA-256 are. The final validator reruns it and requires an empty result: `python3 -B tools/evidence/validate_m09d_theme_studio.py --detector /absolute/path/to/detect.mjs`.

The managed proof checks the real API 35 emulator resources and actual decor/window bounds: phone is `<600dp` smallest width and tablet is `>=600dp`. The injected `1280x720` editor environment is only a layout-policy input; it is not DeX runtime proof.

Final provenance has three phases: sourceCommit is the direct single-parent C1e child of reviewed C1d `6d04c51c19c8cc64becfc80c950eb53d558b68c2`; its three-path cleanup repair preserves the exact reviewed 22-path diff from base `28b3e53613b8c0cd189ba58f4a673aafbed653b2`. C2 is its direct child and contains only the ten artifacts; C3 is C2's direct child and contains only the receipt. Receipt collection requires a byte-empty Git status and no pre-existing receipt; its atomic create refuses placeholders and concurrent replacement. Final validation requires that exact chain, a clean worktree, and a bounded detached clean-source Gradle rebuild with build/configuration caches disabled and all tasks rerun. Cleanup identifies only the temporary checkout's exact Git administration entry, removes only that checkout, proves its administration entry and temporary directory absent, and tolerates unrelated worktree additions or removals without pruning them. The temp volume must retain 2 GiB projected build space plus a 5 GiB reserve; Git worktree operations are capped at 120 seconds and Gradle at 900 seconds. Its target/test APK bytes, hashes, packages, versions, and signers must exactly match committed C2.

Normal and production builds retain Android SDK dependency metadata. Only exact-byte evidence builds opt out of its randomized encrypted APK signing-block payload, using this mandatory initial C2 and detached-rebuild command:

```sh
./gradlew :app:clean :app:assemblePlayInternalRelease :app:assemblePlayInternalReleaseAndroidTest --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks -PcodecksEvidenceBuild=true
```

The validator rejects target or test APKs containing the `PKDS` dependency-info signing-block record. Minification and resource shrinking remain disabled.
