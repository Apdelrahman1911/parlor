# Independent validation — DOC-C5: stale current snapshot-directory guidance

## Classification and identity

**DOCUMENTATION MISMATCH — Low.** This is an independently verified source-comment discrepancy, **not an application, privacy, migration, or data-loss defect**.

- Finder: `/root`; independent validator: `/root/mafia_cont`.
- Checkout: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.
- Per-file hashes and actual read ranges: `validations/DOC-C5-C6-mafia_cont-source-hashes.json` and `coverage/reviews-mafia_cont.jsonl`.
- Audit-only source inspection; no builds, device tests, real snapshot reads, source edits, or Store operations. No external API assumption is required to establish the named-directory mismatch.

## Exact location, expectation, and effect

`/Users/abdelrahman/Projects/parlor/shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt:204–208` identifies the platform implementations as Android `Context.filesDir` and iOS `NSDocumentDirectory`, implementing reads/writes in those directories.

The expectation is narrowly that current interface guidance describe the actual production bindings, or explicitly label historical examples as legacy. Current writes target **Android `noBackupFilesDir/snapshots`** and **iOS Application Support `Parlor/snapshots`**. The named directories are legacy sources for one-way migration and cleanup, not current write destinations.

The concrete consequence is inaccurate maintainer guidance when locating current saves or reasoning about backup/recovery handling; the text is not executable and does not itself put snapshots in backup-visible storage. Low severity is warranted because the platform implementations have accurate local comments and enforce the actual directory selection regardless of this KDoc.

## Reopened reachable implementation and guards

1. Production composition loads the binding: `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/di/AppModule.kt:67–74` includes both `storageModule` and `platformStorageModule()`. Android starts that list from `/Users/abdelrahman/Projects/parlor/composeApp/src/androidMain/kotlin/com/parlor/app/ParlorApplication.kt:13–18`; iOS uses it from `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/MainViewController.kt:18–32`.
2. `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/di/StorageModule.kt:16–22` injects `SnapshotFileSystem` into the common store. `/Users/abdelrahman/Projects/parlor/composeApp/src/androidMain/kotlin/com/parlor/app/storage/PlatformStorage.android.kt:14–15` selects `AndroidSnapshotFileSystem`; `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/storage/PlatformStorage.ios.kt:13–14` selects `IosSnapshotFileSystem`. These are not unused test implementations.
3. `/Users/abdelrahman/Projects/parlor/composeApp/src/androidMain/kotlin/com/parlor/app/storage/AndroidSnapshotFileSystem.kt:46–57,64–72,113–137,145–160,254–274` makes `noBackupFilesDir/snapshots` the checked current base, encrypts before writing a temporary file there, then replaces the current record. `filesDir/snapshots` is separately named `legacyDir`; migration installs protected bytes in the current base before deleting the legacy source. Current-record precedence and explicit legacy deletion show that an existing old path does not make it the current destination.
4. `/Users/abdelrahman/Projects/parlor/composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt:74–124,131–139,168–208,347–373` resolves `NSApplicationSupportDirectory`, creates `Parlor/snapshots` with complete-protection attributes and explicit backup exclusion, and writes through `filePath()` under that base. `NSDocumentDirectory/snapshots` is separately resolved as `legacyBasePath`. Migration writes protected current bytes before deleting the legacy source. Atomic/protected write and backup-exclusion failures throw rather than selecting the legacy directory as a fallback.
5. `/Users/abdelrahman/Projects/parlor/shared/storage/src/commonMain/kotlin/com/parlor/storage/snapshot/FileBackedSnapshotStore.kt:59–86,144–150,209–214` delegates writes/reads to the injected filesystem and maps errors without serializing platform exception details. The disputed KDoc cannot select or override a directory. `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/storage/PlatformStorage.kt:6–16` also correctly explains that the Koin composition function, not the filesystem interface, is the expect/actual boundary.

## Counter-evidence and rejected escalation

- The KDoc says “chosen directory,” so it could be read as general illustration. However, it explicitly associates named platforms and directories with their `actual`s, without “legacy” or “for example.” In the actual application those associations describe historical sources, not current reads/writes. This is a definite current-documentation mismatch, not a missing implementation requirement.
- Both legacy paths still appear in executable migration code. That is compatible with migration and does not validate the KDoc's current-destination claim.
- Correct class-level comments describe the protected destinations (`AndroidSnapshotFileSystem.kt:32–39`; `IosSnapshotFileSystem.kt:58–65`). These reduce impact but do not make the interface wording correct.
- This validation does **not** certify every encryption, durability, migration, or native runtime behavior. No insecure-write or failed-recovery behavior is attributed to this comment.

## Suggested remediation and checks

Update only the KDoc: describe platform-provided protected storage, name current destinations if useful, and clearly distinguish legacy sources. Refer to the composition-bound implementations rather than claiming the interface itself is expect/actual. Do not move saves, change migration behavior, alter crypto, or change save formats to match the old text.

A source-level comparison of the updated wording and both production bindings is sufficient for the documentation fix. Existing storage checks remain relevant if code is changed separately; no new runtime feature or mandatory migration is implied by this finding.
