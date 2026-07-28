# WT11 DeskDock scoring core

Status: FOCUSED_GREEN

Scope:
- Pure `DeskDockScoringEngine`; no Android background launch behavior.
- Signals: Bluetooth-near, charging, face-up, stationary, ambient light stability, NFC, manual override, and fresh Mac state.
- Hysteresis, dwell, cooldown, manual override, deterministic reasons.

Validation:
```text
./gradlew :app:testReleaseUnitTest --tests '*DeskDock*' -PreleaseStoreFile=/tmp/codecks-unit-placeholder.jks -PreleaseKeyAlias=unit -PreleaseStorePassword=unit -PreleaseKeyPassword=unit
```

Result: passed July 27, 2026. Dummy signing properties only; no APK signed or installed.
