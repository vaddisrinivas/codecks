# M12 current-Mac matrix

Evidence scope: `CURRENT_MAC_ONLY`, plus deterministic injected failures and
read-only proxies. The harness never changes sleep state, launchd service state,
accounts, authorization, SSH/helper keys, HID state, or clipboard contents.

Run:

```sh
python3 scripts/run_m12_current_mac.py \
  --output tasks/test-evidence/autonomous-maturity-m12-current-mac.json
python3 scripts/verify_m12_current_mac_receipt.py \
  tasks/test-evidence/autonomous-maturity-m12-current-mac.json
python3 -m unittest scripts/tests/test_m12_current_mac.py
```

The verifier uses only Python's standard library. Its recursive schema subset
validates constants, enums, types, numeric/array bounds, required and additional
properties, ordered/base array items, patterns, and RFC3339 calendar timestamps.
It checks the schema's fail-closed shape and exact ordered lane policies, then binds
the receipt to the Git commit, schema, harness sources, and this plan's M12
evidence parent with SHA-256 digests.

Live lanes are read-only: platform identity, loopback SSH reachability and a
bounded no-side-effect command when existing authentication/trust permits it,
installed helper config validation, launchd status query, helper TCP reachability,
clipboard read discarded to `/dev/null`, and `pmset -g assertions`.

Failure injections are isolated: unused loopback port, bounded subprocess
timeout/output, authentication with no accepted method, a temporary synthetic
public host key (no private key generated), nonexistent tool, and deterministic
bounded backoff.

Command stdout and stderr are drained concurrently. At most 64 KiB per stream
is retained in memory; excess bytes are discarded and marked truncated. The
receipt contains only typed status/code/duration fields and rejects private
paths, account/credential/clipboard keys, email/IP/MAC addresses, fingerprints,
and private-key markers at every nested value.

The following are always `NOT_RUN` without an isolated target or explicit
per-run approval: real sleep/wake, helper restart, account mutation, host-key
replacement, and permission mutation. Intel Mac, other macOS versions, physical
Samsung DeX, and physical Bluetooth HID remain external evidence.
