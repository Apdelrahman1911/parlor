# Independent native verification — native-alltests-01

Reviewer: `/root/native_fix_review`; reviewed 2026-09-05T23:09:58.788744+00:00.

**APPROVED for the configured executable test scope.** This is not physical-device, app-wrapper, complete native-game coverage or Store-readiness proof. DS-C01 remains **PARTIALLY VERIFIED**.

## Exact identity

- Checkout: `main` at `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; authorized dirty remediation checkout, no commit/integration.
- Source manifest (658 tracked/untracked scoped files): `e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7`.
- Tracked diff: `60b555c0dd323a086e959ffbc5aa3fd7c6629ae1d0ad016021572e7ae272e4dd`. Diff alone omits untracked tests; before/after/current manifest equality independently verified.
- Build-lane controls manifest: `922d365bfff5f2101a8c4cb4bb1a64985d7bc2f19816edadade37605b46f97e2`; before/after/current equality verified. Six native correction/test hashes remain identical to the original independent approval.

## Executed command and result

```text
./gradlew allTests productionIosSimulatorRuntimeTests --no-build-cache --continue --no-daemon --no-parallel --max-workers=1 --no-configuration-cache --dependency-verification=strict '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseParallelGC -Dparlor.remediation.cycle=native-alltests-01' -Pkotlin.compiler.execution.strategy=in-process -Pparlor.android.signing.storeFile= -Pparlor.android.signing.storePassword= -Pparlor.android.signing.keyAlias= -Pparlor.android.signing.keyPassword= -I /Users/abdelrahman/Projects/parlor/remediation-runs/2026-09-05-confirmed-fixes/owned_ios_simulator.init.gradle
```

Exit0, 2026-09-05T22:30:54.986075+00:00 → 2026-09-05T22:39:02.052990+00:00. Strict verification, one worker, no build cache. BUILD SUCCESSFUL (861 actionable tasks;857 executed,4 up-to-date). Every XML-backed test task actually executed; no cached test pass is counted.

Parsed every actual testcase in all392 preserved XML suites, reconciled suite attributes and test-receipts, and checked each suite timestamp lies inside this cycle. **2458PASS +3SKIP =2461 platform-instance descriptors**, zero failures/errors; these are not2461unique tests.

| Module | Desktop PASS | iOS simulator PASS | Android debug-unit PASS | Android release-unit PASS |
|---|---:|---:|---:|---:|
| :composeApp | 95 | 85 | 76 | 76 |
| :game-modes:mafia | 267 | 0 (no source) | 0 (no source) | 0 (no source) |
| :game-modes:whodunit | 309 | 9 | 9 | 9 |
| :shared:content | 24 | 24 | 24 | 24 |
| :shared:core | 12 | 12 | 12 | 12 |
| :shared:design-system | 40 | 26 | 15 | 15 |
| :shared:engine | 4 | 0 (no source) | 0 (no source) | 0 (no source) |
| :shared:engine-testing | 6 | 6 | 6 | 6 |
| :shared:networking | 34 | 34 | 34 | 34 |
| :shared:networking-testing | 4 | 4 | 4 | 4 |
| :shared:session | 145 | 142 | 142 | 142 |
| :shared:storage | 31 | 31 | 31 | 31 |
| :shared:transport-p2p | 249 +3skip | 53 | 53 | 53 |
| **Totals** | **1220 +3skip** | **426** | **406** | **406** |

Desktop182suites; iOS70suites; each Android unit variant70suites. The JSON includes all392raw XML paths/hashes and per-suite/task details.

## Runtime, discovery and skipped-target proof

- Root tasks dynamically include all13KMP modules. Native tests truly run via pinned KGP2.4.10 `xcrun simctl spawn <ownedUUID> <test executable>`. The init script sets immutable device ownership, `standalone=false`, `debugMode=false`; it does not change assertions, test filters or enablement.
- Eleven simulator tasks execute. Mafia(32Desktop test-source files) and shared engine(3Desktop files) contain no common/native/Android tests: compile/link report `NO-SOURCE` and their native tasks `SKIPPED`. Their267/4Desktop passes do not become native passes.
- All13`iosX64Test` tasks are skipped for ARM64-host/X64-target mismatch. `iosArm64()` creates a target/binary configuration, not an automatic physical-device test run; no `iosArm64Test` runs.
- Three explicit P2pKit Desktop methods remain `@Ignore`: peer join, round-trip, broadcast require2/3physical LAN devices. The passing host-advertisement method alone is not end-to-end peer proof.
- KGP2.4.10 upstream files were independently reopened in full and seven exact-version official sources freshly re-fetched; bytes match the previously retained dependency source. See `native-final-research-01.json` for URLs/access times/hashes.

## ST-C1 and DS-C01

- **ST-C1 bounded repair approved:** all8new Foundation backup-exclusion/last-copy regressions execute, plus6existing native storage-safety methods. Assertions cover malformed/oversized retained legacy data, direct reads/cold inventory, precedence, exclusion failure propagation, explicit discard and reapplication after retry/recreation. No healthy signed-Keychain migration, real backup/restore or power-loss claim.
- **DS-C01 remains partial:** all11ownership tests execute in synthetic UUID UserDefaults suites. “Restart” tests create a fresh owner object, not a killed/relaunched application. Unmarked legacy overrides remain indistinguishable from legitimate OS values; same-value external writes and asynchronous defaults persistence remain limitations. No actual app resource/direction/active-session-retention journey is proven.
- Native test executables do not launch the SwiftUI/Compose app wrapper. IOS-R1 remains an evidence gap; nothing here hides a storage warning or warrants entitlements.

## Simulator ownership and cleanup

- New simulator `Parlor-Remediation-native-alltests-01-2fe3b9f188bd453496f772189d62ce48`, UUID `7F62342A-458F-43BC-9E8A-A843D0314033`, iOS26.5/iPhone17Pro; not a reused user profile or qualified Store-Xcode gate.
- Gradle `--stop` exit0 at 2026-09-05T22:39:02.389423+00:00; output: “No Gradle daemons are running.” All15created module/root/build-logic `build/` directories and cycle scratch removed by 2026-09-05T22:39:09.247928+00:00. No retained generated outputs, remaining owned workers, deferred signals or cleanup errors.
- Exact owned UUID shutdown/delete exit0; final inventory confirms absence and all22preexisting devices preserved. Unrelated inventory logs intentionally removed after parse; owned operation logs and preservation booleans retained. Reviewer inspected runner/control logic and receipts, did not mutate/query devices.
- Exact-directory cleanup starts no Gradle, so a second stop is unnecessary. This is the terminal native cycle’s cleanup evidence, not an assertion that later root-owned builds cannot have outputs.
- Reviewer started no build/app/simulator/daemon/background workers, performed no Git/application edits and wrote only new required review evidence.

## Remaining limits

Physical LAN, real Android/iOS devices, actual app launch/relaunch and language/accessibility journeys, healthy signed Keychain storage, power-loss/backup behavior, Store/signing/legal operations and native Mafia/engine test coverage are not supplied by this cycle. Separate `productionAppleCheck` linkage/analysis must be reconciled independently; no result is inferred here. Experimental lint pin, future Gradle-deprecation and x64-disabled warnings remain recorded, not suppressed.

Machine-readable dossier: `native-final-verification-01.json` (SHA256`c0235ccee1d68e85ecaa98ed3aee277eb50c22fc82fba3998ea9fdd1a6d4d245`).
