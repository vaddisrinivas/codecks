# M02A dependency replacement decisions

Base: `16e07d34be1505b6568e3d7af99caa279193545d`.

All five candidates are **REJECTED for production adoption**. The standalone
spike proves dependency resolution, API compilation, six focused tests, and
exact source/artifact proxies. It does not claim APK, device, API 28 runtime,
startup, memory, TalkBack, keyboard, screenshot, or migration evidence.

| Candidate | Decision | Decisive result |
| --- | --- | --- |
| Room 2.8.4 | REJECT | 32.5% representative net LOC proxy, but zero-data-loss, migration, rollback, and backup parity are `NOT_RUN` |
| KStateMachine 0.38.1 | REJECT | Representative production code grows by 29 lines before 41 test lines; BSL-1.0 review required |
| Compose-Settings 3.1.0 | REJECT | 0 net lines removed versus 300-line threshold; focus/200% text/screenshots `NOT_RUN` |
| colorpicker-compose 1.2.0 | REJECT | Bound/contrast tests pass; keyboard, TalkBack, crash/jank, and custom-picker comparison `NOT_RUN` |
| Extra compose-icons 1.1.1 packs | REJECT | Removes no custom vectors; raw Font Awesome + Simple Icons artifacts total 12,547,812 bytes / 3,477 classes |

No production dependency, source, manifest, release setting, device, signer, or
`app.codecks` data changed. The reproducible spike remains under `spikes/` only;
rollback is deletion of that standalone directory and this evidence set.

Canonical measurements and proof statuses are in
`tasks/test-evidence/m02a/dependency-spike-results.json`. Validate with:

```sh
python3 tools/evidence/validate_m02a_dependency_spikes.py
ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk ./gradlew \
  -p spikes/dependency-replacements testDebugUnitTest assembleDebug --no-daemon
```
