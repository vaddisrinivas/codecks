# Codecks rollback and incident runbook

This runbook is for release operators. It never authorizes uninstalling,
clearing, downgrading, or differently signing the protected `app.codecks`
package. App rollback means withdrawing the bad artifact and shipping a
forward fix.

## Decision order

1. Stop distribution and preserve redacted evidence.
2. Do not retry a partial download or install session until source, checksum,
   signer identity, and a strictly greater candidate version code are verified.
3. Do not mutate corrupt, future-version, failed-migration, or key-unavailable
   stores. Preserve the raw store; recover only from a verified backup.
4. For an interrupted M08 transaction, run journal recovery. Accept only the
   complete old pair or complete new pair; never mix generations.
5. If an installed release is bad, withdraw it, prepare a forward fix with a
   greater version code, and escalate through the support path. Never install
   an older APK over `app.codecks`.

## Incident matrix

| Signal | Required outcome | Follow-up |
|---|---|---|
| Corrupt data | Refuse mutation; preserve raw data | Diagnose; restore verified backup or forward-fix decoder |
| Future schema | Older reader refuses data | Use a compatible newer release; never schema-downgrade in place |
| Interrupted transaction | Recover one coherent generation | Retain receipt and investigate the interruption |
| Failed upgrade | Preserve older payload; refuse mutation | Correct migration and forward-fix |
| Encryption key loss | Keep ciphertext opaque; require repair/re-pair | Explain data boundary; do not reinterpret ciphertext as plaintext |
| Partial download | Discard partial candidate | Download again, then verify source/checksum/signer |
| Unverified candidate | Reject candidate | Escalate if checksum, signer, or source correspondence differs |
| Partial install session | Abandon session | Retry only the fully verified candidate |
| Equal or older version code | Reject downgrade before commit | Obtain a verified candidate with a greater version code |
| Bad installed release | Withdraw plus forward-fix | Publish incident status and recovery instructions |
| Compromised token | Revoke and rotate | Audit bounded metadata; never include token value in evidence |
| SSH host-key change | Disconnect and refuse commands | Reverify identity out of band before trusting again |

## Evidence and escalation

- Record UTC start/completion times, CPU recovery duration, injected failure,
  exact typed outcome, source commit, source digests, and test-result digest.
- Export only redacted diagnostics. Never include private keys, tokens,
  credentials, host identity, clipboard contents, prompts, or raw command output.
- Escalate incoherent transaction generations, missing verified backups,
  signer/source/checksum mismatch, repeated migration failure, or any suspected
  credential compromise.
- Real signing-key custody and CI signing continuity require separate authorized
  rehearsal. Do not copy or print production private keys.

## Rehearsal boundary

The M20 CPU rehearsal uses temporary files, fake encrypted envelopes, and pure
update-state models. It does not install an APK, touch a physical device, alter
the protected package, withdraw a live release, or access signing material.
