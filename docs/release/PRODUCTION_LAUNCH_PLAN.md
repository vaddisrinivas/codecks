# Codecks production launch plan

Updated: July 17, 2026

## Release decision

Launch `v0.1.1` as a public local-only GitHub beta. Do not deploy a backend or public database. Keep account, billing, context intelligence, widget, clipboard, and advanced surfaces disabled. Promote to GA only after the field gates below pass.

## Completed for public beta

- [x] Product trimmed around Deck, Trackpad, Automations, Settings, editing, and optional AI drafting.
- [x] Consistent Codecks dark-green visual system across core screens.
- [x] Local-only defaults; no server initialization, account, billing, analytics, ads, or cloud sync.
- [x] Public application ID and semantic version established.
- [x] Personal names, workstation paths, device serials, screenshots, and old recovery history excluded from public source.
- [x] Android backup and cleartext traffic disabled.
- [x] Optional exported components off by default; system-facing components permission protected.
- [x] Immutable `PendingIntent` use and signed internal destination routing verified.
- [x] API keys and SSH private-key material protected by Android Keystore-backed encryption.
- [x] Public privacy, security, contribution, and release-signing documentation added.
- [x] CI runs privacy scan, unit tests, lint, and debug build on every change.
- [x] Tag/manual workflow rebuilds and publishes signed APK/checksum from public source.

## GA gates

| Ticket | Gate | Acceptance |
| --- | --- | --- |
| GA-01 | Physical device matrix | Core flows pass on Samsung phone, non-Samsung phone, tablet, and one DeX setup across Android 12–16. |
| GA-02 | macOS matrix | SSH and HID flows pass on the two latest macOS releases, Intel and Apple Silicon where available. |
| GA-03 | Accessibility | TalkBack order/labels, 200% font scale, switch access, contrast, and touch targets pass. |
| GA-04 | Reliability | At least 20 testers, seven days, no P0/P1 issue, and crash-free sessions at or above 99.5%. |
| GA-05 | Automation safety | Adversarial command suite passes; every enable path requires current successful test evidence. |
| GA-06 | Pairing UX | First-time Mac SSH and Bluetooth HID setup succeeds for at least 80% of moderated testers without developer help. |
| GA-07 | DeX QA | Resize, rotate, keyboard, mouse, focus, and window restore pass at 1280×720 and 1920×1080. |
| GA-08 | Release operations | Key backup verified, rollback procedure rehearsed, security-advisory intake tested, release checksum verified on a clean machine. |
| GA-09 | Store decision | Either remain GitHub-only with documented sideload support, or complete Play listing, Data Safety, screenshots, policy review, and staged rollout. |
| GA-10 | AI draft reliability | Versioned strict schemas pass every provider contract test; at least 100 representative prompts achieve 99% parse success, 95% safe semantic-validity, and zero generated actions bypass review or deterministic policy checks. OpenAI and Gemini meet the v0.1.1 live gate; Anthropic live verification is pending account credits. |

## Post-beta priorities

1. Fix real tester failures before adding features.
2. Improve pairing recovery and connection-state explanations.
3. Add instrumented accessibility and resize regression tests.
4. Move optional/incubator modules behind Gradle source-set boundaries, not only runtime flags.
5. Revisit Context Deck/widget only after core retention and reliability are healthy.

No backend work is a blocker for the local-only product.
