# M23 local candidate preflight

Status: `NOT_A_CANDIDATE`.

The current working source contains dependency PR merge commits #18–#24, but no
new version/tag is assigned and no exact signed APK/AAB is admitted. The
validator confirms only that its supplied ambient environment has no release
signing inputs. A durable canonical private-agent-environment presence receipt
does not exist, so that check remains `NOT_RUN`. No signing values or material
are read or copied.

Locally finishable preparation:

- validate PR #18–#24 ancestry, current-source commercial-dark static proof, no-shrink settings,
  release workflow boundaries, release-note template, and gate inventory;
- run CPU/shared/lint/managed/static gates without promoting their result to an
  exact signed candidate;
- build an unsigned artifact only as compilation evidence, never as the release
  candidate;
- keep next version, tag, checksum, signer, APK/AAB, and evidence-bundle digest
  blank until the exact signed build exists.

Still `NOT_RUN`: production signing, candidate APK/AAB admission, signer
continuity, exact candidate emulator/physical/Mac/DeX tests, protected-package
in-place update, tag/push/GitHub release, Play upload, and rollout.

Draft release-note heading: `Codecks next candidate — version unassigned`.
It must not overwrite or reuse `v0.1.37`.
