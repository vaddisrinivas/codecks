# Automation safety contract

Automatic runs use one gate for foreground trigger checks and WorkManager:

- the persisted recipe revision, live-test receipt, preflight receipt, policy version, and
  opaque Mac identity must still match;
- required tools, paths, applications, and permissions are probed again before dispatch;
- execution is pinned to one device ID;
- recipe revision, opaque identity, and device ID are rechecked before every step and cleanup;
- a target or recipe change stops the run with `Needs review`.

Preflight is interactive evidence and must be followed by a live test within 30 minutes.
A passing live test binds that exact preflight snapshot for 24 hours. During that 24-hour
enablement window, runtime requirements are still probed before every automatic run.

Trigger matches use an exact, compare-and-set durable lease. Only the evaluator that atomically
moves an observation into the running state may dispatch; concurrent UI and worker checks cannot
share execution ownership. Only the fingerprint, recipe revision, and claim ID
that reached a terminal decision can be committed. Codecks records `execution_started` before
dispatch. If the process dies after dispatch but before a terminal receipt, the outcome is
uncertain: the rule is disabled for review instead of replaying a potentially non-idempotent
action. This is fail-closed at-most-once behavior, not a claim that remote side effects are
transactional.

Foreground and worker execution share the same claim disposition. A transient block with no
terminal outcome releases only its exact claim before retry; terminal policy, trust, or requirement
failures complete the claim. Legacy Java-hash state remains readable after migration so colliding legacy
recipe IDs cannot consume each other's no-replay evidence.

Backup/import payloads are data, never execution proof. Imported rules are disabled and lose
validation, preflight, live-test, worker, approval, and gate receipts. They must pass the complete
local validation, preflight, and live-test sequence on the destination device.

Undecodable automation storage is not copied into quarantine. Codecks retains only bounded
quarantine metadata: schema, store name, timestamp, payload length, and SHA-256. The original
private app-data value remains untouched and all mutations are blocked until the user explicitly
restores or resets it. The Rules screen exposes both recovery actions and never silently replaces
corrupt user rules with defaults.
