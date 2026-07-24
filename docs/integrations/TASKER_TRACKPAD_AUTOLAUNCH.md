# Tasker Trackpad auto-launch

Codecks exposes one public, non-sensitive destination:

```text
codecks://trackpad
```

It opens the Trackpad directly. It does not execute commands, expose connection
data, or bypass Codecks safety checks.

## Exact desk position: NFC tag

This is the most deterministic option.

1. Put an NFC sticker under the phone stand.
2. In Tasker, use **NFC Tag** to write a URI payload.
3. Set the URI to `codecks://trackpad`.
4. Place the unlocked phone over the tag.

Android treats the tag scan as a user action and opens Trackpad directly.

## Zero-touch desk stand: Tasker profile

Use this when the phone is charged at its Trackpad position.

Profile contexts:

1. **State → Net → BT Near**: select the paired Mac. Keep **Non-Paired
   Devices** off.
2. **State → Power → Power**: select the stand's actual source, or **Any**.
3. **State → Sensor → Orientation**: select **Face Up**.

Entry task:

1. **App → Launch App → Codecks**.
2. Set **Data** to `codecks://trackpad`.
3. Keep **Always Use New Copy** off.

Do not add an exit task that kills Codecks. Bluetooth proximity alone is
room-scale and should not be treated as exact desk position.

## Android and Samsung reliability

- A locked phone or Android background-activity restrictions may prevent an
  automatic foreground launch.
- If Tasker becomes unreliable, allow **Draw over other apps**, permit
  background activity, and set Tasker's battery mode to **Unrestricted**.
- Prefer the NFC trigger when exact placement and predictable launch matter
  more than zero-touch behavior.
