# ST-C1 — independent validation

## Verdict and exact scope

**CONFIRMED DEFECT — Medium.** Finder: `/root/session_cont`; independent
validator: `/root`. This is a legacy iOS local-save privacy defect, not a peer
projection leak, cryptographic bypass, general fresh-install save failure, or
proof that any real backup disclosed player data.

Source: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`. Current source was not changed.
Absolute repository root: `/Users/abdelrahman/Projects/parlor/`.
Exact hashes and directly reopened ranges are in
`validations/ST-C1-root-source-hashes.json` and
`coverage/reviews-root-closeout.jsonl`.

## Expectation, reachability, and root cause

New snapshots are deliberately encrypted, protected and excluded from backup.
Retaining the only corrupt legacy copy for Retry/Discard is reasonable, but
retention should not preserve its historical backup exposure. The expectation
is supported by executable protection of the current destination and the
existing recognized-magic failure cleanup, not just a privacy-document claim.

1. Production iOS DI selects `IosSnapshotFileSystem` in
   `composeApp/src/iosMain/kotlin/com/parlor/app/storage/PlatformStorage.ios.kt:13–19`.
   `composeApp/src/commonMain/kotlin/com/parlor/app/di/StorageModule.kt:16–23`
   injects it into `FileBackedSnapshotStore`.
2. `composeApp/src/commonMain/kotlin/com/parlor/app/App.kt:120–143` invokes Home
   recovery discovery. `shell/home/HomeRecoveryAvailability.kt:48–85` calls
   `listUnfinished` and `loadMetadata`; the common store's lines118–139 and89–105
   route those operations through the platform filesystem.
3. In
   `composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt`,
   lines74–105 protect and exclude **Application Support/Parlor/snapshots**.
   Lines109–123 merely resolve **Documents/snapshots**, without excluding it.
   `list()` at150–166 tries migration and retains failed names; the common
   `PlatformStorage.kt:26–40` isolates ordinary failures rather than deleting
   their data.
4. **Legacy-only damaged framing:** lines201–205 read a bounded old record then
   reject a non-JSON first byte. Encryption, deletion and backup exclusion never
   occur. A one-byte corruption need not destroy the remaining private payload.
5. **Damaged current header with an old copy:** lines168–191 select the current
   record, but a header matching neither protected magic nor legacy JSON throws
   outside the recognized-magic `finally` at174–182. The old plaintext is again
   retained. These are manifestations of one missing legacy quarantine boundary.
6. Only lines103 and358 call backup exclusion. Later Retry repeats the rejection;
   explicit Discard can delete both copies (`LocalResumeRouter.kt:70–94` and
   `IosSnapshotFileSystem.kt:141–147`). Neither guarantees protection during the
   intervening retention period.

Private-payload applicability was reopened independently:
`WhodunitGameFlow.kt:810–839` builds a canonical snapshot;
`snapshot/WhodunitSnapshotCodec.kt:40–51` serializes the full state;
`domain/state/WhodunitState.kt:20–25,101–119` includes roles, killer, seed and seat
mapping. `FileBackedSnapshotStore.kt:170–176` encodes the envelope. The test's
readable synthetic marker is illustrative, not a claim about the real
envelope's textual representation (the payload is a byte array).

The validator also directly read the complete96-line historical iOS producer
at commit `9d06e0c1c5168351c5d49966d07fd714de19a6b6` using `git show`, without
checkout. It created the Documents directory at29–45 and wrote supplied bytes
without application encryption at58–60. This establishes a real legacy format,
not whether any particular old revision was released to Store users.

## Independent native execution

Audit-only source:
`reproducers/ios_storage/STC01LegacyBackupEligibilityAuditTest.kt:1–115`.
The root-owned lane invoked the **actual production Kotlin/Native filesystem**
using `:composeApp:iosSimulatorArm64Test --tests '*STC01LegacyBackupEligibilityAuditTest*'`,
with strict dependency verification, JDK21, one worker and an isolated fresh
iOS26.5 simulator. Inputs and complete flags are preserved in
`evidence/storage-native-inputs-01.json` and
`evidence/storage-native-01/receipt.json`.

Result: **3executed, 2passing witnesses, 1expected failing safety assertion,
0errors, 0skips**. Raw XML:
`evidence/storage-native-01/reports/composeApp/build/test-results/iosSimulatorArm64Test/TEST-com.parlor.app.storage.STC01LegacyBackupEligibilityAuditTest.xml`.

Both witnesses establish that `list()` preserves the record, `read()` rejects,
the synthetic original bytes remain, and actual Foundation backup-exclusion
values are **false for the old file and directory**. The dual-copy witness
also proves the current Application Support directory is excluded. The desired
regression fails with: “A retained legacy record must be quarantined from
backup even when migration fails.” These paths reject before Keychain access;
missing signing entitlements do not explain their result.

Execution ended2026-09-05T11:39:56.732Z with exit1. Gradle stop completed at
11:39:57.058Z, exit0; all task-created module/root/build-logic outputs were
removed by11:39:57.158Z. The owned simulator was shut down/deleted by11:40:00.837Z,
and its absence/no owned processes were checked. See
`evidence/storage-native-01-simulator/receipt.json`. Production source and global
caches were preserved; no real user save, credential, or signing file was inspected.

## Counter-evidence and limitations

- Healthy migration encrypts before deleting the old copy. Recognized-magic
  invalid-version/missing-key cleanup already works; checked-in
  `IosStorageSafetyTest.kt:79–121` covers those different paths. They do not
  cover damaged magic or legacy-only framing failure.
- Sandbox access and Complete Data Protection are not the backup-exclusion
  bit. Apple documents Documents backup defaults and the exclusion key's scope:
  [directory guide](https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/FileSystemOverview/FileSystemOverview.html)
  and [current key](https://developer.apple.com/documentation/foundation/urlresourcekey/isexcludedfrombackupkey),
  accessed2026-09-05. Relevant official material was reopened from
  `research/storage-residual-session_cont/apple-filesystem.html.txt:18–56` and
  `apple-backup-exclusion.md`; fetch/hashes are in that research directory.
- No OS backup was performed. Backup eligibility is not proof of transfer,
  public accessibility, unencrypted system backups, or a real disclosure.
- This does not establish an Android backup leak: Android's backup-rule
  exclusions are different. It is independent of IOS-R1 and the unresolved
  post-open native-exception hypothesis.

## Recommended remediation and regression tests — not implemented

Apply and verify backup exclusion to the legacy directory before attempts to
read/migrate, or safely quarantine retained opaque data under protected,
excluded storage. Handle protection failures explicitly. Preserve Retry/Discard,
current-record precedence, corrupt-state rejection and last-copy recovery;
do not silently restore stale data or delete everything to satisfy a test.

Cover both native witnesses, exclusion failure, oversized/read-failing records,
mixed healthy/corrupt inventory, interrupted replacement/deletion, recognized
magic/key-loss cleanup and explicit Discard. Keep real-device backup behavior,
historical backup retention and actual release incidence as separate validation
and owner-policy questions.
