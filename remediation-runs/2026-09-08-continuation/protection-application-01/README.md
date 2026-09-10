# Actual-filesystem copied-app protection diagnostic

This **unexecuted control at author freeze** is an additive investigation, not an
L08 waiver, normal Debug provenance run, or application qualification. Root must
review and commit controls and the mechanical inventory, generate a fresh source
binding, then approve that exact control manifest before platform execution.

## What executes

One owned simulator, one copied Debug application, and one XCTest call the
unchanged production `IosSnapshotFileSystem` with its real
`IosSnapshotKeychain.loadOrCreate()` write path. The new snapshot directory is not
precreated. The fixture writes and decrypts a small public synthetic JSON payload,
then overwrites and decrypts a different payload. These are **not GameSnapshots**;
strict snapshot/content validation, gameplay, normal startup, Koin, host recovery,
and retained-host routes are not exercised by this entry point.

The wrapper samples before and after the real `excludeSnapshotFromBackup` call:

1. `directory-before-backup`, `directory-after-backup`
2. `write-1-before-backup`, `write-1-after-backup`
3. `write-2-before-backup`, `write-2-after-backup`

The original Kotlin FileManager getter/equality and a native sampler observe each
same inode. The sampler supplies FileManager, fresh NSURL, public descriptor
protection queries and filesystem capability metadata. Every descriptor is closed
before returning from the callback and **before atomic overwrite**. Missing keys,
unavailable SDK symbols, unsupported capabilities, syscall errors, and actual raw
classes are retained, not translated into a protection success. Original equality
is recorded separately; an error-bearing Complete value cannot be a strict PASS.

After XCTest terminates the app, a separately compiled read-only **host macOS**
helper samples the identical final directory/file inodes with the same native
sampler. It binds its executable hash/UUID before and after execution, actual host
SDK public header hashes and preprocessor masks. The preprocessed source is the
sampler; `source_sha256` additionally binds the host main source. Host and iOS
observations come from different processes/platforms/Foundation implementations;
even a difference is **not causal proof of the loaded iOS implementation**.

Six new strict comparisons are reported independently. Diagnostic collection is
`CAPTURED_NOT_PROTECTION_PASS`; existing A37 **0/26** strict results are never
reclassified. Hardware protection is unverified. The normal runtime, provenance,
and notices gates remain **NOT_RUN**. No key, plaintext, ciphertext or full private
path is placed in observation JSON; only ciphertext remains in the owned container
until evidence preservation and owned-simulator removal.

## Copy scope and ownership

`application_probe.py` privately imports the pinned existing normal-iOS lane for
source/control approval, manifest-only copy, worker ownership, isolated build,
fresh artifact/image binding and precise cleanup. It does **not** invoke that
lane's normal runtime sequence or relaunch repetitions. Only the copied Swift
entry, copied XCTest and copied Xcode project are changed. A Kotlin hook, bridge
header and verbatim shared `../protection-diagnostic-01/ProtectionSampler.{h,m}`
are added to the copy. Shipping filesystem and Keychain hashes must remain exact.
No shipping source, application identity, signing, Store or device operation is
changed. The host helper binds the owned device/token/source/controls at compile
time and reads only the driver's exact final container/inodes supplied at runtime,
not arbitrary application paths.

## Commands (coordinator only)

From repository root, first execute the small synthetic control tests:

```bash
python3 -B -m unittest discover \
  -s remediation-runs/2026-09-08-continuation/protection-application-01 \
  -p 'test_application.py'
```

These tests validate synthetic receipts, **not Parlor execution**. Generate the
full approval hash using a fresh source binding and independently reviewed files:

```bash
python3 -B remediation-runs/2026-09-08-continuation/protection-application-01/application_probe.py \
  --control-manifest SOURCE_BINDING
python3 -B remediation-runs/2026-09-08-continuation/protection-application-01/application_probe.py \
  ios-readiness-40 SOURCE_BINDING REVIEWED_HASH \
  --simulator-signing=adhoc --toolchain=qualified-xcode-26.3 \
  --simulator-lifecycle=direct-owned-v1
```

GitHub Actions selection: `protection-application-only`; approval environment:
`PARLOR_APPROVED_PROBE_CONTROL_SHA256`. Run only after the focused synthetic native
diagnostic has been inspected and root approves this additional actual-app build.
Evidence is under `SOURCE_BINDING.parent/evidence/ios-readiness-40`, including
partial/failure receipts where capture fails. Preserve diagnostics separately from
strict and normal gate results; a successful XCTest means collection only.

After any exit, the inherited owner guards stop Gradle, preserve available
evidence, remove only the owned copied source/DerivedData/workers/simulator, and
record cleanup outcomes. Root retains the single build lane and checks stop and
cleanup receipts; failed/partial evidence is not disposable build output.
