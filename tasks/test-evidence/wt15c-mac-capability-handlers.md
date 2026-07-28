# WT15C Mac capability handlers

Implemented:

- `spotlight.search` Mac helper handler using `/usr/bin/mdfind` argv, no shell string.
- `monitor_brightness.set` validation and explicit unsupported/missing-adapter denial.
- `accessibility.discover` bounded discovery seam with permission-aware system snapshotter.
- CLI registers Shortcuts, Spotlight, brightness, and Accessibility handlers.

Safety:

- Tests use fake runners/snapshotters only.
- No phone, APK, secrets, network, or real Mac mutation.
- Brightness does not mutate until a known supported adapter contract is proven.

Verification:

- `cd macHelper && swift test` -> pass, 14 tests.
