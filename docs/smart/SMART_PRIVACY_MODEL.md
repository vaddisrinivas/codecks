# Smart Privacy Model

Smart stores learning data only when it is content-free.

## Allowed to persist

- action IDs
- candidate IDs
- counters
- coarse time buckets
- success/failure counts
- sanitized context keys, such as app IDs or coarse surface labels

## Never persist

- raw clipboard text
- typed text
- notification title or body
- command text
- command output
- file paths
- screenshots
- OCR text
- accessibility labels

## Retention

Local learning keeps only recent bounded records. Corrupt or unknown records are ignored and can be cleared without affecting Deck buttons, Rules, SSH trust, Bluetooth pairing, or AI settings.

## AI boundary

Local ranking never calls an LLM. AI can be used later only after the user explicitly asks to create a draft artifact, and that artifact still saves disabled/unverified.
