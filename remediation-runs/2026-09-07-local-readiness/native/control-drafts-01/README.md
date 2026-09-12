# iOS local-readiness controls

These non-shipping controls build an explicitly bound **temporary source copy**
on one newly created, task-owned iPhone 17 Pro / iOS 26.5 simulator. The root
coordinator owns the only Gradle/Xcode lane. Nothing here authorizes signing,
Store operations, personal-device use, repository changes, or publication.

## What runs

The copied XCTest target must execute exactly five unskipped tests: the four
existing production `ComposeContainerViewControllerTests` and the expanded
actual-app matrix. The matrix retains the reviewed V12 controls for:

- actual English/Arabic/System Settings selections and before-App restarts;
- both local games' actual UI setup and retained canonical session observations;
- actual Compose/native direction and portrait/landscape containment geometry;
- real foreground/background callbacks and bounded passive observation trains;
- public Settings-app interaction, exact pane identity, one-activation guards,
  and an observed English-primary / Arabic-secondary system-language list.

The additions are:

1. **Eight further explicit process-cold launches** of the same installed build,
   after the Settings fixture. Each has six foreground/no-alert/native-geometry
   samples spanning at least ten measured seconds. These are not eight fresh
   installations, uninstrumented launches, or proof of a past crash's cause.
2. **Actual Koin-bound storage investigation.** The original Home invocation is
   not instrumented retroactively. A separate invocation of its production
   recovery pipeline captures credential SecItem return statuses at the exact
   call sites, tagged with a coroutine-context element. Concurrent unrelated
   calls cannot borrow that attribution. Original queries, errors, dispatchers,
   mutexes, and result mappings remain unchanged.
3. **Synthetic cold-process durability.** A UUID-scoped secure-storage key and
   safe `.native-readiness` file use the real backing/filesystem. Boot 1 writes,
   boot 2 reads/overwrites, boot 3 reads/deletes, and boots 4–8 check absence.
   Home never sees this filename as a saved game. No byte contents, credentials,
   keys, returned CF data, or exception messages enter observation receipts.
4. **Actual OS Arabic per-app selection.** After an attested cleanup of only
   the earlier synthetic app-domain language seed, public Settings chooses
   Arabic while English remains the system's primary language. The probe
   captures actual before-App presence and ordered value; real in-app English,
   System, and restart must preserve it exactly. ABSENT is reported honestly
   and does **not** complete the separate PRESENT-coverage goal.
5. **Full native provenance.** Every bundle file is checked for Mach-O magic;
   built and installed image inventories must be identical. This includes the
   launch stub, `Parlor.debug.dylib`, and `ComposeApp.framework/ComposeApp`.
   Actual app-emitted dyld paths bind each executed image to those hashes.
   `dwarfdump` UUID/architecture results and read-only owned-app `codesign`
   metadata supplement the inventory. `CODE_SIGNING_ALLOWED=NO` describes the
   build configuration; it does not by itself establish the bytes' signature
   form or explain a Keychain result.

## Prerequisites and explicit binding

Use JDK 21, the checked-in wrapper, strict dependency verification, the installed
iOS 26.5 runtime, and `/usr/bin/python3 -B`. This runtime is **not** the pinned
Store-qualified Xcode 26.3 validation environment. No CocoaPods or repository
override is introduced. Stop any earlier task-owned build lane first; these
controls refuse pre-existing Gradle/Xcode workers and original build outputs.

Run lightweight controls before any native execution:

```sh
/usr/bin/python3 -B -m unittest discover \
  -s scripts/verification/ios-readiness -p 'test_*.py' -v
```

These synthetic tests validate parsers, source transformations, exact worker
ownership, stale-output/exit propagation, and V12 secondary-FIFO cleanup. They
do not establish app, iOS, Keychain, LAN, or Store success.

Then freeze all tracked/untracked build inputs and controls:

```sh
CONTROL=scripts/verification/ios-readiness
/usr/bin/python3 -B "$CONTROL/bind_source.py" --describe
# Use the explicitly reviewed source and diff hashes from that output:
/usr/bin/python3 -B "$CONTROL/bind_source.py" \
  remediation-runs/CAMPAIGN/ios-readiness-source-01.json SOURCE_SHA DIFF_SHA
/usr/bin/python3 -B "$CONTROL/run_ios_readiness.py" --control-manifest \
  remediation-runs/CAMPAIGN/ios-readiness-source-01.json
```

A separate reviewer must inspect the complete control/source binding, the
copy-only diff, tests, ownership, and finalization paths. Binding creation is
not approval. After that reviewer records the exact control hash, the root
coordinator may execute:

```sh
/usr/bin/python3 -B "$CONTROL/run_ios_readiness.py" ios-readiness-01 \
  remediation-runs/CAMPAIGN/ios-readiness-source-01.json REVIEWED_CONTROL_SHA
```

Bindings/cycle directories are never overwritten or refreshed implicitly.
Changing any source/control invalidates the execution binding. The runner has
no runtime dependency on archived audit/remediation scripts; their origin
hashes in `inherited-controls.json` are provenance, not proof of correctness.

## Result and cleanup interpretation

- Diagnostic delivery and stable launches are independent from storage health.
  A nonzero native storage result remains unavailable, never an empty success.
  A successful write followed by loss/corruption, successful deletion followed
  by retention, or native success followed by unexplained secure-boundary
  failure makes the local gate **FAIL** and requires investigation. It is not
  automatically an independently confirmed application defect.
- Failed prerequisite writes/deletes do not establish a durability defect;
  dependent verification remains **BLOCKED**. IOS-R1 numeric evidence belongs
  only to the exact observed credential invocation. Snapshot numerical status
  and the original Home invocation remain explicitly unattributed.
- Exit `0` means this bounded matrix passed; `2` means diagnostic verification
  succeeded but storage/OS/PRESENT coverage remains partial; `1` means a gate,
  source binding, execution, or cleanup failed. None is a production-readiness
  or Store verdict. Full games, save-envelope recovery, physical LAN, real
  signing, assistive technology, and past-launch root causes remain outside
  these controls' claims.
- Every nested/outer build path immediately stops the isolated Gradle registry.
  After compact receipts, hashes, UUIDs, and source differences are captured,
  the owned simulator, copy, DerivedData, and task workers are removed/stopped.
  Cache symlinks are unlinked without deleting global caches. External Apple
  FIFOs require PID/start/ancestry/UID/inode/birthtime/holder attestation before
  exact unlink and empty-parent removal; unknown paths are preserved and fail
  cleanup. Interruptions use the same finalization path.

Review first: `run_ios_readiness.py`, `owned_lane.py`, `secondary_fifo.py`,
`copied_sources.py`, `instrument_storage.py`, `NativeReadiness*`,
`artifact_inventory.py`, and the receipt parsers. Original evidence is immutable;
the runner writes only to its new campaign cycle directory and owned temporary
allocation. The final receipt compares original inputs before/after and copied
inputs after workers stop. Cleanup failures must be resolved, never relabeled.
