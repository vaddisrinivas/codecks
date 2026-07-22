# Smart Evaluation Plan

## First Smart Deck checks

- Same context produces same ordering.
- No AI key is required.
- Suggestions do not execute automatically.
- Pinned Deck buttons keep their positions.
- Hide suppresses a candidate.
- Never for this app suppresses the same app/action pair.
- Pin copies the suggestion into an existing empty Deck slot only after user tap.

## Quality gates

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:check
./gradlew -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect :app:pixel6Api35DebugAndroidTest
python3 scripts/verify_mac_actions.py
```

## Release gate

Smart remains default-off until physical Android + Mac testing confirms the suggestion row is useful and non-disruptive.
