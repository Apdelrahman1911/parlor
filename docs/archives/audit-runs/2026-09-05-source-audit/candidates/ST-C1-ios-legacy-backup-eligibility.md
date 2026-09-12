# ST-C1 — Failed iOS legacy migrations retain application-plaintext snapshots in a backup-eligible directory

**Finder:** `/root/session_cont`. **Independent validator:** `/root`. **Current classification (2026-09-05): CONFIRMED DEFECT — Medium**, independently approved in `validations/ST-C1-root.md` with actual Kotlin/Native filesystem evidence. **Historical filing status:** candidate with deterministic source proof, awaiting independent approval; runtime had not yet executed at filing. **Platforms:** iOS; current fresh installs without legacy saves are unaffected. No Android backup leak or peer/network exfiltration is claimed.

**Reviewed source:** branch `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Tracked source unchanged. Existing untracked user material preserved. All source paths below are relative to absolute repository root **`/Users/abdelrahman/Projects/parlor/`**; the hash table supplies exact versions.

## Impact and expectation

A legacy `.snapshot.json` record can contain host-private game payloads. If migration cannot recognize or read it, the application correctly refuses to restore the corrupt game, but retains the original bytes under `Documents/snapshots` without adding a backup exclusion. Much of the private payload may remain intact even if one framing byte is corrupt. Apple documents Documents as backed up by default. Until successful migration or explicit Discard, a subsequent system backup is therefore eligible to copy application-plaintext private state off the device.

The expected boundary is supported by the actual current implementation, not solely by prose: all new records are encrypted and written under an excluded Application Support directory (`IosSnapshotFileSystem.kt:74–105,241–260,347–370`); the recognized protected-record failure branch deliberately deletes leftover legacy plaintext (`174–182`). The `SnapshotStore` contract (`shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/SnapshotStore.kt:10–13`) explicitly includes host-private state. The privacy contract also says host-only state never leaves the host (`docs/PRIVACY_AND_COMPLIANCE.md:22`), but this claim is corroborative rather than executable proof.

**Not claimed:** that any real player's data has been backed up; that an OS backup is unencrypted or publicly readable; a network/peer disclosure; a fresh-install normal-save encryption failure; or that old builds shipped to Store users. Real backup execution and transfer were not performed by the finder.

## Exact reachable source path

1. iOS shipping DI binds `IosSnapshotFileSystem` (`composeApp/src/iosMain/kotlin/com/parlor/app/storage/PlatformStorage.ios.kt:13–19`) into the singleton `FileBackedSnapshotStore` (`composeApp/src/commonMain/kotlin/com/parlor/app/di/StorageModule.kt:16–23`). Home starts inventory at `composeApp/src/commonMain/kotlin/com/parlor/app/App.kt:120–143`.
2. `composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeRecoveryAvailability.kt:48–85,110–145` calls snapshot inventory/metadata. `shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt:118–149` calls the platform list and maps safe filenames to IDs.
3. `IosSnapshotFileSystem.kt:109–123` resolves the old `Documents/snapshots` tree. `150–166` discovers a safe bounded legacy name, attempts migration, then returns that name even when its migration failed. `composeApp/src/commonMain/kotlin/com/parlor/app/storage/PlatformStorage.kt:26–40` intentionally isolates per-record ordinary exceptions while propagating cancellation.
4. **Witness A — legacy-only malformed framing:** create one old regular file named `audit-legacy.snapshot.json`, containing a previously plaintext snapshot whose first `{` changed to `!`, with the remaining secret-containing bytes intact. There is no matching protected record. `IosSnapshotFileSystem.kt:168–170` calls `migrateLegacy`; `201–205` reads it successfully within the byte bound, then throws before encryption, deletion, or backup exclusion. `381–382` recognizes only first-nonwhitespace `{`. This failure happens before any Keychain lookup, so missing signing entitlements are not a prerequisite for the source proof.
5. **Witness B — malformed current header plus leftover legacy copy:** the protected file exists but starts with a damaged `PARSNAP` magic and does not look like JSON; the legacy plaintext exists too. `172–191` selects the final rejection, outside the `174–182` recognized-magic `finally`, so the legacy copy remains. This groups with A because neither path quarantines/protects the legacy tree before migration.
6. The only backup-exclusion calls are `IosSnapshotFileSystem.kt:103` (protected directory) and `358` (new protected file), with helper `361–370`. There is no exclusion applied to `Documents/snapshots` or either retained failed record. Safe filename validation, bounded reads and corrupt-state rejection do not establish backup exclusion.
7. Retry/Discard behavior is sound but does not erase this interval: `composeApp/src/commonMain/kotlin/com/parlor/app/LocalResumeRouter.kt:70–94` invokes explicit deletion; `App.kt:265–291` routes user actions; `IosSnapshotFileSystem.kt:141–147` deletes both current and legacy copies. Until the user discards, retry repeats the same rejection and retention.

## Historical reachability (not release-incidence evidence)

The full 96-line historical first-party iOS implementation was inspected directly with `git show 9d06e0c1c5168351c5d49966d07fd714de19a6b6:composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt`, without checking out that commit. Lines `29–45` create `Documents/snapshots` with no exclusion; `58–60` write raw supplied bytes. Current migration exists specifically to consume that layout. Corrupt framing or an interrupted replacement/delete sequence is sufficient; no hostile peer or public filesystem access is required. An interruption after current ciphertext replacement but before legacy deletion can create the dual-copy precondition, although Witness B additionally requires corruption of the protected header.

Related `d54ab0cec246ed7ed23647b7e084e748c49534cd` changes the recognized-magic read from success-only deletion to `finally`. This fixes decrypt/key-loss failures **inside that branch**, not all malformed-file states. The report does not reopen that fixed case as broken.

## Independent verification completed — 2026-09-05

**Historical filing note:** this section originally requested independent review and made no runtime PASS/FAIL claim because no receipt existed then. The following is the subsequent root-owned result, not self-approval by the finder.

Independent validator `/root` reopened the production implementation, callers, tests, private-payload serialization, historical producer and authoritative Apple backup references, then classified **CONFIRMED DEFECT — Medium** in `validations/ST-C1-root.md` (source ranges/hashes: `validations/ST-C1-root-source-hashes.json`).

The root-owned single build lane executed `:composeApp:iosSimulatorArm64Test --tests '*STC01LegacyBackupEligibilityAuditTest*'` with strict dependency verification, JDK21, isolated audit source and a fresh iOS26.5 simulator. `reproducers/ios_storage/STC01LegacyBackupEligibilityAuditTest.kt:1–115` invokes the actual production filesystem, not a JVM fake. **3 tests executed: 2 witnesses PASS; 1 desired-safety regression FAIL as expected; 0 errors; 0 skips.** Overall task exit1 correctly records the failed desired invariant rather than claiming a green suite.

The witnesses prove that after `list()`, the retained legacy file and its directory return actual Foundation backup-exclusion values **false**; `read()` rejects while the synthetic original bytes remain intact. The damaged-current-header witness also proves the protected Application Support directory's exclusion is **true**. The desired assertion fails with “A retained legacy record must be quarantined from backup even when migration fails.” Both failed-migration paths precede Keychain access.

Evidence: `evidence/storage-native-01/receipt.json`, `test-receipts.json`, and `reports/composeApp/build/test-results/iosSimulatorArm64Test/TEST-com.parlor.app.storage.STC01LegacyBackupEligibilityAuditTest.xml`; simulator lifecycle/cleanup `evidence/storage-native-01-simulator/receipt.json`. Execution ended2026-09-05T11:39:56.732Z; Gradle stop exit0 at11:39:57.058Z; precise generated-output cleanup complete11:39:57.158Z; task-owned simulator shutdown/deletion completed11:40:00.837Z with no owned device/process left. Source unchanged and no real data/signing material accessed.

The finder independently reopened the115-line reproducer, complete raw XML and build/test/cleanup receipts before incorporating these results. **No actual OS backup, physical-device transfer, publicly readable backup, or real-player disclosure was tested or claimed.** Safe retention in excluded quarantine remains a valid remedy; the test does not require destruction of the only recoverable copy.

## Counter-evidence examined

- New saves already use authenticated encryption, Complete Data Protection, atomic write and backup exclusion. Healthy legacy migration succeeds before deletion; this finding does not contradict those paths.
- The current `IosStorageSafetyTest.kt:79–121` proves legacy removal for recognized magic with invalid version and for a missing key. Both preserve `PARSNAP`; neither exercises malformed magic or legacy-only failed framing.
- Corrupt-record isolation and explicit user-controlled Discard are legitimate data-integrity behavior. They justify retaining recoverable bytes, not leaving the old directory backup-eligible. Remediation must preserve that recovery policy.
- Files are app-sandboxed; direct peer access is absent. System backup has different scope. Data Protection does not itself set the backup exclusion flag.
- Android has similar success-only legacy deletion but explicitly excludes root/file domains from both backup rule versions and disables backup in its manifest. Do not copy this finding to Android.
- Ordinary NSError/format rejection reaches a recovery warning instead of a crash. This candidate is independent of the unconfirmed post-open NSFileHandle exception risk and the rejected directory-opener ROOT-C1 lead.

## Suggested remediation and regression coverage (not implemented)

Apply and verify backup exclusion/protection to the legacy directory **before** any migration/read attempt, or move retained opaque records into safely excluded protected quarantine while preserving Retry/Discard semantics. Handle exclusion failure explicitly. Do not normalize corrupt snapshots, silently fall back to stale legacy state, or delete the last usable copy merely to satisfy a test.

Tests should cover: legacy-only damaged first byte; protected bad magic plus legacy; recognized-magic missing key; bounded oversized/read failure; exclusion failure; healthy and corrupt records together; interruption between write and delete; and explicit discard. Verify flags on relevant directories/files, not only an in-memory fake. Real-device backup inclusion/exclusion and existing historical backups remain separate external validation/retention concerns.

## Authoritative platform basis

Access date **2026-09-05**; compact raw receipts and extracted relevant content in `research/storage-residual-session_cont/`:

- Apple File System Programming Guide, iOS section: <https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/FileSystemOverview/FileSystemOverview.html>. Reviewed extract `apple-filesystem.html.txt:18–56`, especially `33–35,54`: Documents and Application Support backed up by default; use NSURLIsExcludedFromBackupKey to exclude. Original HTML SHA-256 `3632c19044906714af5bfbc1029d882f35d1bd70208224b2836acc26d0e29d72`.
- Current Foundation resource-key documentation: <https://developer.apple.com/documentation/foundation/urlresourcekey/isexcludedfrombackupkey>. Official JSON <https://developer.apple.com/tutorials/data/documentation/foundation/urlresourcekey/isexcludedfrombackupkey.json>. Full main content read: flag excludes resource from all app-data backups; some filesystem operations can reset it to false, so set when saving. JSON SHA-256 `95e1cc545863b8bcc11c10571f469ee51f9a52f8f5c810b2ecb8c765fc5404ba`.

The archived guide establishes general iOS directory defaults, not simulator/device backup execution. The current API confirms the supported exclusion control. No source excerpts or private material were sent to external services.

## Current source hashes

- `composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt` — `340c62d93104e015bc9dc80f92b04e46e92c77d52947b2623f0fd5c472900216`
- `composeApp/src/commonMain/kotlin/com/parlor/app/storage/PlatformStorage.kt` — `1f56d8b7adca7d5148445ec9e8f69d2f9a74f253d4c425cc2abd00a84594b690`
- `composeApp/src/iosMain/kotlin/com/parlor/app/storage/PlatformStorage.ios.kt` — `cb69b6de029bf7d9f84dbc5bf19139150906a29d64cf29a60994259ca657a0c0`
- `composeApp/src/commonMain/kotlin/com/parlor/app/di/StorageModule.kt` — `9734dde8f751d0e5792577692c322051aa0adc7f45ed0fa6ac5961cf9295e87c`
- `composeApp/src/commonMain/kotlin/com/parlor/app/App.kt` — `622b679a2ef023fab17bf6c8ed8eb38c1fbc2b76a391f5f9a8298d59da9dda59`
- `shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt` — `e2ca0be28fd2f0eafee1a55022653190cf30a8c6da620dc3dfe56f361e0da27f`
- `composeApp/src/iosTest/kotlin/com/parlor/app/storage/IosStorageSafetyTest.kt` — `2ecae719e0cbbdaefa412ede46656f59b63777087c8b653e215053359b42218b`

## Additional executable private-payload trace

`game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt:810–839` writes the canonical state, `snapshot/WhodunitSnapshotCodec.kt:40–51` serializes the full state, and `domain/state/WhodunitState.kt:20–25,101–119` contains all private player roles and the host killer/seed/seat mapping. `shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt:170–176` serializes the envelope. This confirms that a framing-damaged legacy snapshot may still contain private payload bytes; it does not assert any real user's legacy file was inspected. Hashes/ranges for these supplementary reads are in `reviews/storage-residual-session_cont.sources.json`.
