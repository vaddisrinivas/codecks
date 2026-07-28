# WT18 Release Evidence Harness

Status: focused green

Scope changed:

- `scripts/verify_release_no_shrink.sh`
- `tools/secret_surface_check.py`
- `docs/release/REACTIVE_RELEASE_EVIDENCE_TEMPLATE.md`
- `tasks/worktree-ledger.md`
- `tasks/test-evidence/wt03-android-helper.md`
- `tasks/test-evidence/wt18-release-evidence.md`

Implemented:

- No-shrink script reports exact release Gradle file lines for `isMinifyEnabled = false` and `isShrinkResources = false`.
- Secret scan includes `shared`, `protocol`, and `macHelper` surfaces plus private key/token-like pattern checks.
- Evidence docs no longer store local private home paths.
- Release evidence template separates implemented, focused, integration, emulator, physical debug, and signed release acceptance.

Validation:

- `python3 tools/secret_surface_check.py` -> `secret surface OK`
- `scripts/verify_release_no_shrink.sh` -> exact release lines verified, no shrinking enabled
- `git diff --check` -> clean
