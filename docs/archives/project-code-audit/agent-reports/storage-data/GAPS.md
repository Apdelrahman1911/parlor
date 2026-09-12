# Storage / content workstream — GAPS

## Intentionally unread (out of persistence scope)

- Whodunit / Mafia reducers and most UI
- `P2pKitRoomTransport` body (credential *use* sites, not the store)
- `ResumableCredentialStoreTest.kt` (store itself was read)
- Whodunit case JSON **bodies** (clues/characters) — envelopes only
- Snapshot golden JSON under `desktopTest/resources/snapshot-goldens/`
- `WhodunitPayloadValidator` after the first 40 lines
- `WhodunitSnapshotValidationTest` / `MafiaSnapshotCodecTest` bodies
- composeApp `App.kt` beyond the `SnapshotStore` inject (architecture
  already mapped Home inventory)

## Questions this workstream did not close

- Whether iOS `NSUserDefaults` ever fails in practice without an API
  error (ST-003 is about observability, not a reproduced loss).
- Whether any debug / screenshot build rebinds `SnapshotStore` or
  `RemoteCaseDataSource` (only `allModules` was traced).
- Peak RSS of an 8 MiB Android snapshot read (ST-007) — not profiled.
- Whether a future settings key would be acceptable in iCloud backup
  (ST-004).
- Exact Keystore wipe behavior after PIN change / device transfer for
  `com.parlor.app.snapshot.aes-gcm.v1` (Android Keystore semantics;
  code does not handle key-invalidated-but-file-remains beyond
  `SnapshotProtectionException` → player Discard).

## Coverage vs charter

| Charter item | Status |
|---|---|
| shared/storage/** all Kotlin | Done (9 main + 4 test) |
| composeApp platform storage actuals | Done (Android/iOS/Desktop + backup XML) |
| shared/content/** all | Done (12 main + tests sampled/read) |
| Ktor vs Offline | Done |
| Bundled JSON + catalog identity | Done (7/7 match) |
| Snapshot codec recovery (both games) | Done |
| Settings persistence | Done |
| ResumableCredentialStore | Done |
| Does Mafia write local snapshots? | Yes PaP / no multi-device |
| Atomicity, corruption, concurrent, backup | Mapped in STORAGE.md |
| StorageModule + platformStorageModule | Traced |
| UI / P2P except credentials | Not audited |
