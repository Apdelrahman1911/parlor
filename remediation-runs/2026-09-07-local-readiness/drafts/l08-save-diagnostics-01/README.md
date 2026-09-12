# L08 native05 save failure — diagnostic proposal only

## Status and scope

**No production defect or fixture correction is proven. No changes have been
applied to repository controls or shipping code. No tests/build/native APIs have
been executed by this agent.** This is author preparation, not independent
approval. `/root/factory_review` must review the proposal; `/root` owns execution.

The draft is bound to HEAD `89dbe8aeaf4e2c83f491521629982be09706cf67`, tree
`c65463bdf1f3656f90dfe6e6dbe1d94b3c588dac`. Exact original and proposed file hashes
are in `draft-receipt.json`; the complete proposed control-only diff is
`proposed.patch`. The parent is running unchanged normal-ios06 at source06.
**Do not promote this draft or invalidate that freeze while the parent run is
active.** Any later promotion requires a fresh source binding and review.

## What native05 actually establishes

`evidence/ios-readiness-05/parlor-l08-storage-1-save.json` records boot 1/action
`save`, FAIL, `stage=real-store-save`, `reason=fixture_or_boundary_failure`.
The receipt is sanitized and identifies the owned-copy Compose framework by
SHA-256 `b393b2b8433473cb9143d6c6a605f49c1926a5433f187ddf2f6a02995b778b38`.
The original probe's lines 155–158 keep the same stage across the save result,
load result, complete envelope equality, and all protected-file observations.
There is no narrower failure observation. Do not label that receipt a failing
production save merely because the stage has that name.

Source-reachable remaining alternatives include actual save/load failure,
unequal envelope, canonical path/type/existence failure, backup-exclusion or
protection-attribute observation, and bounded encrypted-file/header checks.
The probe's 256 KiB+128 observer ceiling differs from the production 8 MiB
plaintext ceiling; no measured fixture size establishes this as the cause.
The proposal preserves that bound rather than guessing a replacement.

## Counter-evidence checked

- `GameSnapshot.equals` explicitly compares `payload.contentEquals`; ByteArray
  reference equality is not the demonstrated cause. Equality also checks every
  envelope identity/version/time/phase/metadata field.
- Production encoding/decoding uses the real GameSnapshot serializer and the
  full FileBackedSnapshotStore. The fixture still requires that actual DI
  binding, IosSnapshotFileSystem, and P2pKitRoomTransport.
- Native05 boots 1–3 recorded small raw SnapshotFileSystem write/read-match/
  delete success. This opposes a universal Keychain/encryption/filesystem
  failure, but does not exercise GameSnapshot envelope serialization or
  NSFileProtection attribute observations. It is not an L08 PASS.
- The fixture's NSNumber backup readback matches the production exclusion
  verifier. No Boolean bridge correction is supported by current evidence.
- No raw saved payload, Keychain value, actual player data, private material,
  or simulator container file was read for this diagnosis.

## Proposed diagnostic changes

1. Reset the closed stage to `context` for each accepted command.
2. Distinguish reachable checkpoint construction, actual save, actual load,
   and complete envelope equality. Apply to ordinary and dual-copy saves.
3. Emit nine closed metadata-stage literals before the existing checks:
   path, existence, directory/file backup exclusion, protection constants,
   directory/file protection, bounded encrypted read, and header validation.
4. Restore the enclosing stage after successful nested metadata checks only.
   A thrown failure retains the exact last boundary; no finally resets it.
5. Keep the FAIL schema/version, two reason values, PASS schema/check sets,
   deadlines, action ordering, cancellation rethrow, all assertions, payload
   zeroization, and owned-path restrictions unchanged. No API return values,
   exception messages, paths, ciphertext/plaintext, hashes of secrets, or
   private state are added to receipts.
6. Keep historical broad FAIL stages preservable. Add only a closed immutable
   allowlist and explicit string-type guards before enum membership. Host
   failure stages remain unchanged.

## Proposed verification (not executed)

Root should run the new `test_l08_diagnostics` together with existing
`test_l08_receipts` and `test_l08_host_receipts` in an exclusively owned full
control copy containing these three proposed files. Run Python with `-B` /
`PYTHONDONTWRITEBYTECODE=1`; retain compact output/status and delete the owned
copy. The new nine test methods cover all 27 known stage literals, preservation
of historical failures, failure-never-PASS, sensitive/unknown/non-string
rejection, host isolation, source ordering/bounds/zeroization, successful-only
stage restoration, and cancellation/context reset.

These are parser and source-contract tests, **not Kotlin compilation or iOS
runtime**. After independent review and a fresh source freeze, the parent may
run the actual copied app-host gate to obtain the missing boundary observation.
Do not remove any assertion or declare repair success to avoid that run.

## Cleanup and limits

This agent started no Gradle/Xcode/test/server/app/simulator processes and
created no generated build outputs. Only this new small draft/evidence directory
was created. This proposal leaves the two source06 shared L08 controls unchanged
and modifies no tracked file. Concurrent other-agent edits appeared in the
normal-launch controls; they are recorded in `draft-receipt.json` and
`final-draft-checkpoint.json`, not attributed to or overwritten by this agent.
Pre-existing material remains untouched. No `--stop` was invoked against the
parent's active build lane. All physical backup, lock, LAN and Store evidence
limitations remain unchanged.
