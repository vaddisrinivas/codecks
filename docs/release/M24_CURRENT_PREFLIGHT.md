# M24 current release preflight

Canonical base: `9eb4bcd86a95440ee28c0e3191761c1c5725f5bc`.
Verdict: `NO_GO` (`PREFLIGHT_ONLY`).

Receipt provenance is two-phase and fail-closed: first commit the six exact
implementation paths; then collect and commit only the receipt in a descendant
commit. The receipt binds the base, implementation commit, exact changed-path
set, per-path before/after/patch hashes, and aggregate diff hash. Validation
requires the receipt commit to be exact clean `HEAD`, its direct parent to be the
implementation commit, its diff to contain only the receipt, and working bytes
to equal the committed blob. Extra-path commits or uncommitted drift fail.

Current local truth:

- M21 local GitHub distribution/support preparation is `LOCAL_COMPLETE`; its
  physical install/update and external-publication lanes remain outside this
  preflight.
- M23 is `NOT_A_CANDIDATE`: no version is assigned and no candidate artifact,
  source hash, checksum, or signer is admitted.
- M16 capacity execution and the 168-hour run are `NOT_RUN`. Source plans four
  AVDs and five isolated processes per AVD, but no runtime receipt exists; zero
  profiles, profile-hours, and operations are promoted here.
- Canonical agent-environment presence checks report all four release-signing
  input names unset. No values or signing material are recorded.
- Read-only ADB classification reports zero physical phones. It sends one raw,
  mutation-free `host:devices-l` request to the existing loopback ADB server;
  the `adb` CLI is never invoked. Wrong listeners, protocol mismatches, `FAIL`,
  malformed lengths/rows, offline, unauthorized, or unknown rows fail closed.
  It records only a response hash, no raw serial, and performs no
  package query, APK pull, install, data read, or instrumentation.

Therefore no protected-package update is authorized. Candidate creation,
signing, installation, publication, and external Play state remain outside this
receipt.
