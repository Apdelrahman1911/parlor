# Storage residual source review — legacy cleanup and native post-open errors

Reviewer `/root/session_cont`; source `main` at `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Audit only. Root owns the one build/device/cleanup lane; this reviewer ran **no** builds, tests, device operations or cleanup commands. Paths below use `/Users/abdelrahman/Projects/parlor/` as absolute prefix. Exact hashes and read ranges are in `reviews/storage-residual-session_cont.sources.json` and the appended coverage receipts.

## Dispositions

| Lead | Result and count handling |
|---|---|
| iOS failed legacy migration leaves backup-eligible plaintext | **ST-C1 CONFIRMED DEFECT — Medium**, independently approved by `/root` on2026-09-05 after actual native execution (2 witnesses PASS, desired-safety regression FAIL). At original filing this was pending; updated result below and `validations/ST-C1-root.md`. |
| Android protected-read cleanup only on successful decrypt | Conditional retention is real, but the proposed iOS-style backup leak is **FALSE POSITIVE** against current Android exclusions. Stricter purge/quarantine policy is an unresolved legacy-at-rest hardening question, not a separately proven application vulnerability. |
| iOS read/close NSException after successful open | **UNCONFIRMED — BLOCKED** as an application crash; **test/evidence-gap annotation**, not a confirmed defect. Native error semantics are known, but a concrete production post-open fault timing has not been established. |
| Directory passed to iOS bounded-reader crashes | Keep **ROOT-C1 rejection narrow**. Existing macOS probe failed to open the directory and never executed read/close. Neither a production crash nor general post-open safety was proven by that probe. |

## ST-C1: the narrow `finally` is not universal cleanup

`composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt:168–191` removes the old Documents record on failed decrypt only after `hasMagic()` succeeds. The whole protected read at172 is earlier, as is the absent-current branch at170. A damaged magic reaches191 without the deletion. Legacy-only framing corruption reaches205 before any encryption, exclusion or delete.

`list():150–166` intentionally retains names for failed migration. The common helper `composeApp/src/commonMain/kotlin/com/parlor/app/storage/PlatformStorage.kt:26–40` preserves cancellation and isolates ordinary per-record errors; `FileBackedSnapshotStore.kt:89–105,118–149` safely reports corruption and `HomeRecoveryAvailability.kt:48–85,110–145` exposes a warning. None of those guards excludes the retained bytes from backup. Exclusions at103/358 apply only to the new Application Support tree/files. Apple Documents defaults and exclusion API were read directly; see the research ledger.

Private-state applicability was additionally followed through executable serialization: `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt:810–839` writes canonical state via the codec; `snapshot/WhodunitSnapshotCodec.kt:40–51` serializes the full `WhodunitState`; `domain/state/WhodunitState.kt:20–25,101–119` includes per-player role maps and host-only killer/seed/seat mapping. `FileBackedSnapshotStore.kt:170–176` serializes that envelope. Historical first-party iOS code at commit `9d06e0c1c5168351c5d49966d07fd714de19a6b6`, read completely, writes bytes directly to Documents/snapshots at29–60. No real legacy data or shipping-release incidence was inspected or assumed.

Retaining an unreadable save for explicit Retry/Discard is intentional and valid. Retention without excluding/protecting the old location is the separate privacy problem; the suggested remedy is protective quarantine/exclusion, not forced deletion or permissive decoding. Actual OS backup inclusion and historical cloud retention remain external evidence requirements. Neither reading a synthetic flag nor Simulator success proves a real backup occurred.

## Android cleanup asymmetry: source-proven retention, backup consequence rejected

Full `composeApp/src/androidMain/kotlin/com/parlor/app/storage/AndroidSnapshotFileSystem.kt` inspected. New files are in `context.noBackupFilesDir/snapshots:46–53`; legacy files in `context.filesDir/snapshots:55–57`. At121–123, deletion is `.also` after `decrypt`; failures skip it. A bounded protected file `"PARSNAP" + byteArrayOf(0,12)` plus an existing legacy copy deterministically reaches version rejection at197–213 before KeyStore, so the old bytes remain. Successful migration installs ciphertext with sync/atomic replace before deletion at145–160; interruption between installation and deletion explains a possible dual-copy state.

Important guards/counter-evidence:

- No stale legacy fallback occurs when a current file exists but fails; protected failure becomes a sanitized data error. The record remains addressable for user-controlled recovery. Explicit delete at75–83 removes both paths.
- `composeApp/src/androidMain/AndroidManifest.xml:9–12` disables backup and references both rule formats.
- `composeApp/src/androidMain/res/xml/backup_rules.xml:7–17` excludes root/file and other data domains.
- `composeApp/src/androidMain/res/xml/data_extraction_rules.xml:6–29` excludes root/file separately for **cloud backup and device transfer**. Official docs distinguish allowBackup's OEM caveat from these explicit rules; the `file` domain corresponds to getFilesDir and directory exclusion is recursive.
- Existing `AndroidSnapshotDirectoryListingTest.kt:9–23` verifies null-list handling only; it is not a Keystore, migration, or physical backup test. `PlatformStorageMigrationTest.kt:8–31` deliberately retains failed records and preserves cancellation.

Thus the backup-leak hypothesis has concrete counter-evidence. Retained old application-plaintext is not converted into newly protected data; a stronger uniform fail-closed quarantine policy could improve the legacy-at-rest guarantee, but this review did not demonstrate cross-app, peer, cloud or replacement-device access bypassing current rules. No Android runtime test of this state was executed by this reviewer. Do not silently change recovery/deletion policy as a stylistic symmetry fix.

The current Android guide also mentions Android16QPR2/API36.1 cross-platform transfer. Its generic missing-mode warning is not alone proof of exposure: the specific configuration requires `<cross-platform-transfer platform="ios">` and bundleId/teamId/contentVersion mapping. Parlor has none and declares no BackupAgent. Do not invent identifiers or assert this mode is active. OEM/custom transfer behavior and exact API36.1 runtime implementation were not tested.

## iOS post-open FileHandle exceptions: known API behavior, missing reachable repro

`IosSnapshotFileSystem.kt:403–414` opens a private handle, executes synchronous `readDataOfLength(max+1)`, closes in `finally`, then enforces the byte limit. Nil from opener is already a Kotlin IllegalStateException. `FileBackedSnapshotStore.kt:73–105` catches ordinary Kotlin errors; it does **not** establish interception of an Objective-C NSException.

Authoritative sources agree:

- Foundation `readData(ofLength:)` raises `fileHandleOperationException` when type determination/read fails.
- Installed Foundation SDK header `NSFileHandle.h:112–161` marks legacy read/close operations as potentially throwing. NSError alternatives `readDataUpToLength:error:` at27–28 and `closeAndReturnError:` at48–49 exist since iOS13, below Parlor's iOS16 minimum.
- Kotlin Native docs say unwrapped Objective-C exceptions at the boundary crash by default. Full `v2.4.10` platform `Foundation.def` was inspected and has no `foreignExceptionMode = objc-wrap`; current source imports platform.Foundation. No project override/wrapper appeared in the search.
- Foundation Complete protection blocks reads/writes while locked. This is not by itself proof that Parlor successfully opens then crosses a lock access-loss boundary while reading.

Counter-evidence checked: handles are local to this synchronous helper, no suspension exists between open/read/close, the production store is a singleton with its own mutex, and no sibling call closes this private handle. Malformed regular bytes are not a native I/O error. Swift `ContentView.swift`, `MainViewController.kt` and common `AppLifecycleCoordinator.kt` manage privacy cover/transport visibility, not this file descriptor. `Info.plist` declares no file sharing/import path that introduces foreign handles/FIFOs. This does not prove storage errors impossible; it limits what has actually been established.

The root's existing `reproducers/filehandle_directory.m` was read in full and its receipt reopened. It compiled on macOS but returned `opened_directory=false`, exit3, before any read. Therefore keep ROOT-C1 rejected for that witness only. It is not an iOS protected-file test, not post-open proof, and not evidence of an app crash.

Remaining exact validation: an isolated KMP/native app-host test that establishes a successful open of a synthetic regular protected file and then a real read/close error, plus a physical-device lock/unlock timing/lifecycle probe. Fault injection can prove bridge behavior but must not be presented as production reachability. Never use real snapshots. If later remediation is authorized, evaluate the NSError methods with bound/error/cancellation-preserving tests; a generic `catch Throwable` is not a justified solution to a foreign exception boundary.

## Evidence and cleanup

- Source/research manifests are audit evidence, not source changes. Search results were used to locate callers, not counted as line review.
- No Gradle/Xcode/test processes were created by this reviewer. No generated application outputs were created, so no reviewer-owned build cleanup was required. Root handles its own active shared build lane and reports its results separately.
- This report makes no new runtime PASS claims. Parent-produced receipts must be independently opened before incorporating any later runtime conclusion.
- Tracked working-tree/index remain unchanged at this report's closeout; pre-existing untracked guides/design/audit material were neither removed nor rewritten. Full source identity and comparison are in the source manifest.

## Dated post-filing update — independent ST-C1 evidence

Root independently approved ST-C1 on2026-09-05 in `validations/ST-C1-root.md`. The finder subsequently reopened the entire115-line audit reproducer, raw XML, build/test and simulator cleanup receipts. Actual production Kotlin/Native filesystem ran3 tests on an isolated fresh iOS26.5 simulator:2 witness passes,1 expected failed desired-safety invariant,0errors/skips. Legacy file+directory exclusionfalse, retained synthetic bytes, failed read and protected-directory exclusiontrue were asserted through Foundation APIs. No actual backup/device transfer or real disclosure was performed. Root-owned cleanup completed with stop0, no generated outputs and owned simulator removed. This replaces only ST-C1's original pending status; Android and native-postopen dispositions are unchanged. No builds were executed by this source-reviewer lane.
