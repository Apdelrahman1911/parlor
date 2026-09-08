# Android managed release-smoke result — independent verification

Reviewer `/root/whodunit_cont`; execution owner `/root`; 2026-09-05. **PASS for this narrow ARM64/API35 managed-emulator run and its task-owned cleanup.** This is not a physical-device LAN, real-signing, Store, whole-UI, whole-application, or cross-platform verdict.

## Source and executed path

Repository `/Users/abdelrahman/Projects/parlor`, branch `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked status empty before/after and at independent verification. All eight recorded input hashes were recomputed and matched, including the source-reviewed `b34ad466…` audit helper, imported helper, checked-in signing harness/wrapper, Gradle task definitions, and both instrumented test classes. The input manifest hash matches the run receipt. This does not replace root's separate inventory of pre-existing untracked/build-consumed files.

Actual path: checked-in `run_release_managed_device_smoke.sh` → `./gradlew productionAndroidRuntimeCheck --dependency-verification=strict --no-daemon --max-workers=2 …` → `:composeApp:pixel2Api35ReleaseAndroidTest`. JDK21.0.11/Gradle8.13 were observed in owned process/log records. The ADB server used the independently reviewed loopback-only listener and task-owned HOME/AVD/Gradle-registry isolation.

Gradle recorded **BUILD SUCCESSFUL in2m39s**,486actionable tasks (420executed/66up-to-date). Release compilation, app/test R8 shrinking, release packaging, signing validation using the synthetic two-day test key, and lintVital tasks actually ran. The existence of a synthetic signature is not proof of canonical Store signing or identifier ownership.

## What actually ran

I read all693sanitized Gradle-log lines,481receipt lines,11JUnit XML lines, and complete input/artifact/test/ADB/stop receipts. The XML contains exactly three cases, not just an aggregate count:

| Actual method | Recorded time | Assertion scope inspected in source |
| --- | ---: | --- |
| `MainActivityColdStartTest.testColdStartDisplaysContentWhileSettingsIoIsBlocked` |0.763s|Controlled settings FIFO connected/still blocked while the real Activity has a visible, attached, nonzero-sized Compose root and an observed draw; cleanup releases/waits for writer.|
| `ReleaseRuntimeSmokeTest.testReleaseBuildCanAcquireDeclaredMulticastLock` |0.123s|Declared multicast permission is granted; a real platform multicast lock can be acquired/released.|
| `ReleaseRuntimeSmokeTest.testReleaseBuildLaunchesCanonicalActivityWithoutDebuggableFlag` |1.411s|Target package is canonical, debuggable flag absent, Activity launches and is not finishing/destroyed after idle.|

JUnit counters are3tests/0failures/0errors/0skips; class/method identities match the reviewed production instrumented fixtures. Device/project properties are `pixel2Api35`/`:composeApp`. No common/desktop rule tests ran in this cycle (`test-receipts.json=[]`). The view/draw assertion is not a screenshot/pixel/layout/a11y test; acquiring a lock is not actual P2pKit/LAN communication.

Two compact artifact receipts were retained before deletion:

- App release APK:4,784,964bytes, SHA256 `33c2bcad1afefc4ef7fed98e764e2a53fae489613a6f9f5e06e4c5b410d764e5`.
- Test APK:12,048bytes, SHA256 `c456586bbd3ab6b2b5818acd929ce7ada6cb93a56f71a44ac044d9f6eb33ac12`.

The APK bytes were correctly removed after their required use, so this second review verifies retained hash receipts/source binding—not a new certificate or archive inspection.

## Warnings and non-pass areas preserved

The log still reports Gradle8.13future deprecation, host-incompatible iOSX64tests, experimental lint pin, inability to strip `libandroidx.graphics.path.so`, newer installed SDK XML/Android37directory metadata, and R8's synchronous-parser fallback warning. These are retained, not suppressed or invented into application defects. Other `SKIPPED`/`NO-SOURCE`/`UP-TO-DATE` build-task statuses are not counted as executed tests. This run does not close those other platform/dependency qualifications or establish a warning-free repository.

The three ADB connection-terminated messages coincide with the recorded managed-emulator lifecycle. They do not prove a Parlor disconnect defect or networking success. This reviewer did not run adb or inspect any real user's device/session.

## Cleanup independently checked

Harness exit0 was recorded at13:19:10.170793Z. Immediate wrapper stop returned0 at13:19:10.507743Z (0.33695s later); `stop.log` states no Gradle daemons are running in the isolated registry. Worker/ADB shutdown completed before required evidence capture and filesystem removal. The receipt records no live owned identities/unknown holders and no cleanup errors;13generated output directories and the task HOME/AVD/TMP/key/Gradle-registry root were removed after unlinking the two cache pointers.

An independent, read-only `ps` query for all recorded PID/start identities found none remaining. Read-only `lsof` found no listener on the dedicated port53207. All13listed output paths and the exact temporary root were absent; the two shared global cache targets still existed. No process was signaled and no Gradle/SDK-runtime command was launched by this reviewer. These checks concern task-owned state, not unrelated user processes.

Structured proof, commands,16verification checks, source/evidence hashes, and reviewed ranges: `evidence/android-managed-02-independent-whodunit-cont.json`.

## Relationship to the failed first attempt

`android-managed-01` remains a recorded **audit harness listener-argument failure before Gradle/tests**, not an app failure. Its cleanup passed. The independently verified, server-only socket-spec correction is documented under `research/android-managed-listener-whodunit-cont/`. A successful retry does not erase the first receipt or manufacture device evidence. Physical LAN, real signing/Store identity, external release gates, complete navigation/UI journeys, and other-platform runtime coverage remain separate.
