# Independent Android audit-helper execution-safety review

- Reviewer: `/root/whodunit_cont`, separate from helper author `/root/mafia_cont`.
- Decision: **SAFE TO ATTEMPT** only for the exact runner below and the isolated managed-device path; this is not a Gradle, app-runtime, physical-device, signing, or Store gate PASS.
- Repository: `/Users/abdelrahman/Projects/parlor`, `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Tracked diff was empty at approval; pre-existing untracked material preserved.
- Approved runner: `audit-runs/2026-09-05-source-audit/run_android_managed_cycle.py`,646lines, SHA256 `74cf484d1e629fbe82ca0e91aa0b36691314044fdbe2d25c0af81f380afdcec1`.

## Actual review coverage

The602line revision SHA256 `bd53751fc15da1f5270eb807e93333fc02c042971ab2827eff956479c62472b7` was read completely in numbered, untruncated chunks. The full602→646diff and all44inserted lines of `launch_log_writers`, plus its changed call and surrounding process ownership logic, were then reread. Unchanged ranges were source-compared; the hash was checked again after synthetic validation. This accounts for every line of the approved version, not merely search hits or another agent's summary.

The imported156line `run_gradle_cycle.py` was read completely. Only its `ROOT`, `OUT`, `identity`, and `owned_outputs` definitions are called by this runner; its guarded `main` is not executed on import. This approval does not approve that other runner's standalone finalizer.

The checked-in58line synthetic-signing harness,251line Gradle wrapper,62line Java runtime fixture, and188line Kotlin cold-start fixture were fully reread. A separate coverage ledger records hashes/ranges and marks audit-only helpers as nonshipping, so these reads must not inflate the application's source-coverage percentage.

## Reachability and isolation

`productionAndroidRuntimeCheck` selects `:composeApp:pixel2Api35ReleaseAndroidTest`; the actual Gradle/AGP/UTP/ddmlib path binds an owned AVD and exact managed-device serial. The cold-start fixture deletes/recreates app settings and uses a FIFO: it must never run on a user's device or pre-existing AVD.

The approved runner precreates task-only HOME/Android/emulator/AVD/TMP directories, disables USB/mDNS/general-emulator ADB discovery, and uses a foreground ADB server whose listener PID must match its owned Popen PID. Exact installed SDK binary behavior, library versions, and serial selection were researched separately in `research/android-managed-isolation-whodunit_cont/PREFLIGHT.md`. No actual adb/emulator command was executed by this reviewer.

Its sanitized environment excludes inherited credential/serial/Java injections, blanks real signing project properties, disables implicit SDK downloading, and retains strict dependency verification. The checked-in harness creates only a two-day synthetic test key. The final separate Gradle-user-home registry shares only `caches` and `wrapper` symlinks, never global configuration/init/signing or daemon state. Exact Gradle8.13 source confirms daemon-registry scope.

## Safety corrections independently requested/rechecked

1. **Shared Gradle registry:** a baseline-only absence check cannot protect an unrelated same-version daemon started later. The final isolated registry closes that window without deleting or duplicating global dependency caches.
2. **Launch-log reader adoption:** revision602adopted any new SDK/JVM PID holding an audit log. That could include an unrelated reader. Final646requires writable stdout/stderr (FD1/2) plus matching inode/device. Ordinary readable-log viewers are excluded. This was an audit-helper correction, not a Parlor application defect.

PID/start identity, observed ancestry/process groups, unique JVM temp arguments, and task-root file holders constrain worker ownership. Stop sends bounded TERM/KILL only to verified identities; no broad `pkill`, generic AVD-name kill, default ADB command, or foreign-port kill is used. A foreign/unknown root holder prevents filesystem deletion. Interrupted attempts use the same finalizer, with repeated INT/TERM deferred while it records failures, stops Gradle/workers/ADB, captures compact evidence, and removes task-only outputs.

Existing build directories cause refusal rather than deletion. Cleanup explicitly unlinks its two cache symlinks; installed `lsof +D` semantics do not follow them, and `rmtree` does not delete their targets. Cleanup errors and retained paths cannot be reported as a complete PASS. A SIGKILL/host death cannot be finalized in-process; the prelaunch ownership/input receipts exist for manual targeted recovery.

## Exact evidence requirement

The runner must retain and verify all three actual instrumentation methods, not merely a successful task or aggregate test count:

- `com.parlor.app.ReleaseRuntimeSmokeTest.testReleaseBuildLaunchesCanonicalActivityWithoutDebuggableFlag` (`ReleaseRuntimeSmokeTest.java:18–37`).
- `com.parlor.app.ReleaseRuntimeSmokeTest.testReleaseBuildCanAcquireDeclaredMulticastLock` (same file:39–61).
- `com.parlor.app.MainActivityColdStartTest.testColdStartDisplaysContentWhileSettingsIoIsBlocked` (`MainActivityColdStartTest.kt:21–138`).

PASS also requires exit0, no runner error, no report copy/parse error, counters matching actual cases, no failures/skips, artifact hashes, and completed cleanup. Artifact hashing is not proof of real Store signing or physical-device networking.

## Independent synthetic verification

Command: `/usr/bin/python3 -B audit-runs/2026-09-05-source-audit/evidence/android-runner-independent-whodunit-cont/verify_helper_synthetically.py`.

The helper functions were extracted from the exact approved AST into a namespace with synthetic process snapshots/FD replies. No helper `main`, Popen, lsof, adb, emulator, keytool, Gradle, or process signal ran. Synthetic XML/APK-hash fixtures and symlink-target checks lived only in a fresh audit-owned fixture directory, which was removed on both attempts.

- Final run: exit0;21 assertions PASS (`synthetic-receipt.json`); these are helper assertions, **not21application tests**.
- Covered writable FD ownership, reader/pre-existing PID exclusion, wrong inode/device, malformed writer metadata, unknown holders, exact methods, missing methods, skips, inconsistent JUnit counters, preserved symlink targets, and fixture cleanup.
- Initial run: exit1 from a fixture that expected an exception despite a mismatched inode already rejecting ownership. It was corrected to match the inode before omitting device metadata; the earlier rejection remains a separate assertion. The failed attempt and reason are retained in `synthetic-attempt-01.json`. No helper/application guard was weakened.

## Limits and continuation

This review authorizes one controlled attempt by root, not a claim that ADB registration, emulator boot, release tests, or cleanup will succeed. Runtime format/platform failures must remain explicit, and residual workers/outputs require identity-checked cleanup rather than deleting live state. Root owns the single build lane and will preserve actual run/cleanup receipts. Physical Android/iOS LAN, real signing, Store identity ownership, and external Store gates remain separate.

Research additions contain only compact exact-version source excerpts, URLs/hashes, and local-man excerpts; full temporary source downloads were not retained. This reviewer created no build outputs, daemons, app/test/server processes, source edits, commits, or Store operations.
