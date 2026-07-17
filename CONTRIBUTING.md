# Contributing

Codecks welcomes focused bug fixes, tests, accessibility improvements, and device-compatibility reports.

## Development

1. Use JDK 17 and the repository Gradle wrapper.
2. Keep production behavior local-only by default.
3. Do not commit credentials, keystores, device serials, local filesystem paths, screenshots containing private data, or generated build artifacts.
4. Run:

```bash
python3 tools/secret_surface_check.py
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Pull requests should explain user impact, include tests for logic changes, and include sanitized phone/DeX screenshots for visual changes.

Security issues belong in a private [GitHub security advisory](https://github.com/vaddisrinivas/codecks/security/advisories/new), not a public issue.
