# WT19 Accessibility discovery scan

Status: implemented as a bounded Mac helper scan, focused-test verified.

Changed:
- `SystemAccessibilitySnapshotter` now uses macOS Accessibility APIs after `AXIsProcessTrusted()`.
- Locates the running app by bundle id.
- Traverses the app AX tree with hard caps:
  - max 200 nodes visited.
  - max requested action count returned.
- Counts discoverable AX action names without executing actions.
- Added runtime config parsing for Mac helper env startup.
- Added TCP helper server compile path for framed requests.

Safety:
- No Accessibility action execution.
- Missing Accessibility permission fails closed.
- No physical Mac permission prompt automation.
- No phone/APK touched.

Verification:
- `cd macHelper && swift test`
  - Passed: 22 tests, 0 failures.
