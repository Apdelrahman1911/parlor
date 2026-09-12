# Verification and cleanup reconciliation — supplement 01

## Scope and preservation

Independent reviewer: `/root/mafia_cont`. This is a **supplement**, not a rewrite of the original reconciliation. The original report/index retain their original26-receipt/15-Gradle-cycle scope. Their current hashes and a fresh comparison of all26 original receipt hashes are in `evidence/verification-cleanup-reconciliation-mafia-cont-supplement-01.json`: **no original receipt changed**.

Reviewed completely: both new `receipt.json` files, the native Gradle log, raw JUnit XML, JSON test/artifact receipts, simulator lifecycle logs, isolated115-line test,8-line test source-set injection,68-line simulator task selector,118-line native runner and156-line shared build runner. This reviewer ran no build, test, app, or device boot; read-only simulator metadata/process/output queries independently checked completed cleanup.

Source remained `main` / `3625d0663ba6eb51338cbd5f9dc45f859ec18846` / tree `db7f3d2afe73a13628296daee2cce71165eebc8d`, with no tracked modifications before/after. The known untracked paths remain preserved; `git diff --check` exits0.

## New native cycle — honest result

Evidence: `evidence/storage-native-01/` and `evidence/storage-native-01-simulator/`.

- Command: checked-in `./gradlew :composeApp:iosSimulatorArm64Test --tests '*STC01LegacyBackupEligibilityAuditTest*'` with the retained audit-only source-injection and owned-simulator init scripts. The receipt includes every flag: JDK21, strict dependency verification, no build cache, no parallel execution, one worker, no configuration cache, in-process Kotlin compiler and6g JVM heap; Android signing inputs were explicitly empty.
- Actual runtime target: freshly created **iPhone17Pro / iOS26.5 ARM64 simulator**, bound to UUID `7EB15DA9-B8EC-4CC1-8D70-01FD06BC25F7`; Gradle logs confirm `standalone=false` and the exact selected test task. This is not a device or Store-qualified Xcode26.3 run.
- Cycle: 2026-09-05T11:38:46.659637Z–11:39:56.732710Z. Gradle **exit1 / FAIL**, because the intended safety assertion fails. Compilation and native test execution occurred; this is not a selector, linker, launch, or initialization failure.
- Raw XML and JSON agree exactly: **3 discovered/executed,2 passed,1 failed,0 errors,0 skipped**. Failure message: `A retained legacy record must be quarantined from backup even when migration fails`.

| Exact native test (all `[iosSimulatorArm64]`) | Result and interpretation |
| --- | --- |
| `witnessDamagedProtectedHeaderRetainsBackupEligibleLegacyPlaintext` | PASS as a **witness of current unsafe retention**, not safety success. The actual default `IosSnapshotFileSystem().list/read` leaves the synthetic legacy bytes when a protected record has an invalid header. The legacy file and its directory report no backup exclusion; the protected directory reports exclusion. |
| `witnessDamagedLegacyPrefixSurvivesColdListWithoutBackupExclusion` | PASS as a **witness**. The actual filesystem retains/discovers a synthetic legacy file with an invalid JSON prefix; reading rejects it, while native file and directory backup-exclusion values remain false. |
| `regressionRetainedDamagedLegacyMustNotRemainBackupEligible` | **FAIL, expected reproducer failure**. After the production cold-list path, neither removal nor file/directory backup exclusion satisfies the safety assertion. Do not count this as a passing regression gate or a fix. |

The fixture directly instantiates the production Kotlin/Native `IosSnapshotFileSystem`; its reads and backup-attribute checks use Foundation, not a mirrored JVM filesystem or implementation mock. Synthetic filenames contain a fresh UUID and the known snapshot suffix. Each test cleans its own synthetic files in `finally`; the entire fresh simulator is subsequently deleted. No real player data is involved.

Limits: malformed-prefix/header paths stop before encryption/Keychain work, so these tests do **not** validate cryptographic round trips, Keychain entitlements, Home/Compose rendering, or valid migration. Querying backup-exclusion attributes is not a real iCloud backup/upload/restore observation. Broader finding classification/expectation remains in the finding's independent source validation; this supplement reconciles the execution evidence only. The separate `evidence/storage-native-inputs-01.json`, recorded11:36:52.188487Z before the11:38 native cycle, fingerprints all five runner/fixture/init files; independently recomputed hashes match all five. This resolves the original supplement's review-time-only input caveat. No linked test-binary hash was preserved, so this remains input identity plus task execution evidence, not a binary-to-source attestation.

## Stop, output cleanup, and device cleanup

- Primary `./gradlew --stop` finished with exit0 at11:39:57.058651Z: **0.325941s after the task finished**. `stop.log` says no Gradle daemons are running.
- Evidence copied before deleting exactly **15 task-created generated build directories**, including module, root and `build-logic/convention/build` outputs. No global dependency cache/source/configuration/verification metadata was deleted. Cleanup completed11:39:57.158011Z, with no cleanup errors or remaining generated outputs. No cleanup Gradle command started a replacement daemon.
- The outer simulator finalizer issued a redundant `./gradlew --stop` (exit0, same no-daemon result), then **shutdown exit0** and **delete exit0** for its recorded UUID. The companion receipt completed11:40:00.958030Z with `owned_device_absent=true` and no matching processes.
- Independent metadata check at11:48:53Z: `simctl list devices -j` exit0, target UUID absent; its exact CoreSimulator device directory absent; `ps` query exit0 with no target-UUID process, Gradle8.13 daemon, or repository build/test process matching the inspected predicates. Every recorded removed build path remains absent. This check neither booted nor altered another simulator or user process.
- Small logs/XML/test sources are retained as evidence. Artifact receipts are empty; no linked test binary or generated output remains required. No separate task-owned Xcode DerivedData is recorded for this Gradle-only cycle.

## Reconciled totals and unaffected gaps

The combined reconciliation now covers **28 original receipt files,16 primary Gradle cycles,759 retained JUnit XML files** (original26/15/758 plus this cycle's2/1/1). These are receipt/cycle counts, not unique test counts. The ordinary `productionCheck`, `allTests`, Desktop, Android, and previous simulator suite counts do not change: this is an isolated audit reproducer outside normal registered suites. Physical P2pKit tests, signed release/Store gates, real-device backup/lifecycle/UI/accessibility evidence, and prior host/toolchain limitations remain unverified as previously recorded.
