# Theme Studio and icon library

Theme Studio is an offline, preview-first appearance tool. It ships twelve accessible presets, visual role swatches, scoped Deck and Trackpad overrides, and a bounded named library stored in the app's private DataStore.

- Preset and color controls expose radio semantics and 48dp minimum targets.
- Raw `#FFRRGGBB` and opacity controls stay under **Advanced**.
- A maximum of 12 custom themes is retained. Names are trimmed, bounded, unique ignoring case, and contain no control characters.
- Library JSON is closed-field, size bounded, contrast checked, and migrates the prior v1 `label` field to v2 `name` without accepting unknown fields.
- Import/export remains local JSON. It never executes commands, reads files, or contacts a service.
- Icon previews search stable semantic actions; each supported pack resolves those actions through dependency symbols verified by compilation.

M09D proof is bounded to the Play-internal repository lifecycle test. It does not prove a broader controller, process lifecycle, physical device, or public release; those remain `NOT_RUN`.

The durable M09D receipt binds the exact source diff, target/test APKs, final 13-test XML, device-info, sanitized textproto, API 35 phone/tablet topology, and the final empty Impeccable result. Reproduce the detector gate after the last UI edit with:

```sh
python3 -B tools/evidence/run_m09d_impeccable_detector.py --detector /absolute/path/to/detect.mjs
```

The detector path is never persisted; its filename and SHA-256 are. The final validator reruns it and requires an empty result: `python3 -B tools/evidence/validate_m09d_theme_studio.py --detector /absolute/path/to/detect.mjs`.

The managed proof checks the real API 35 emulator resources and actual decor/window bounds: phone is `<600dp` smallest width and tablet is `>=600dp`. The injected `1280x720` editor environment is only a layout-policy input; it is not DeX runtime proof.

Final provenance is three commits: C1 contains only the reviewed product/tests/tools/docs, C2 contains only the ten rebuilt runtime/detector artifacts, and C3 contains only the receipt. Validation requires the exact `C3 -> C2 -> C1 -> reviewed base` chain and a clean worktree.
