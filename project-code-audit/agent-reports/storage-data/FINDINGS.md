# Storage / content findings

IDs ST-001+. Evidence is file:line. Severity is about persistence /
integrity / recoverability risk, not whether a happy-path game currently
saves.

---

## ST-001 SnapshotStore contract claims AEAD; common layer stores plaintext JSON

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `shared/storage/.../SnapshotStore.kt:10-13` requires “authenticated
    encryption at rest”
  - `FileBackedSnapshotStore.kt:143-152` encodes `GameSnapshot` as UTF-8 JSON
  - Protection exists only in platform `SnapshotFileSystem` actuals
- **Why it matters:** A mistaken bind of a raw-directory FS (or a future
  desktop “just write files” shortcut) would persist host-private state
  (role maps, killer id) as readable JSON while still satisfying the
  interface. The invariant is convention, not the type.

---

## ST-002 Public Ktor remote adapter on the shipping content classpath

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `shared/content/build.gradle.kts:13` `ktor-client-core` on commonMain
  - `KtorRemoteCaseDataSource.kt:40` public class
  - Production bind `ContentModule.kt:59` `OfflineRemoteCaseDataSource()`
  - `MockEngine` only in tests (`OfflineRemoteCaseDataSourceTest.kt`)
- **Why it matters:** Offline-only release still ships an HTTPS client
  type. One Koin edit enables an unreviewed remote path. Same fact as
  AR-003; filed here because it is the content-pipeline defect.

---

## ST-003 iOS settings writes are not durable / error-checked

- **Severity:** Medium
- **Confidence:** High
- **Evidence:**
  - `IosSettingsKeyValueBacking.kt:22-31` `setBool` / `setObject` /
    `removeObjectForKey` with no `synchronize` and no `NSError`
  - Contrast `AndroidSettingsKeyValueBacking.kt:56-58` `commit()` or
    `SettingsPersistenceException`
  - Contrast `DesktopSettingsKeyValueBacking.kt:44` `preferences.flush()`
  - `PersistentSettingsStore.kt:50-51` publishes only after `write*`
    returns — iOS write always “returns”
- **Why it matters:** Reduced-motion / language / theme can look saved
  and vanish after kill. `NSUserDefaults` is usually durable, but the
  store cannot observe a failed write. Accessibility prefs are the
  surface.

---

## ST-004 iOS user defaults are backup-eligible; snapshots/credentials are not

- **Severity:** Low
- **Confidence:** High
- **Evidence:**
  - `IosSettingsKeyValueBacking.kt:9` `NSUserDefaults.standardUserDefaults`
  - Snapshots: `IosSnapshotFileSystem.kt:102` `excludeFromBackup`
  - Credentials: `IosSecureKeyValueBacking.kt:61-62` ThisDeviceOnly,
    `synchronizable=false`
  - Settings keys are non-secret (`PersistentSettingsStore.kt:71-75`)
- **Why it matters:** Theme/language can restore onto another device
  while snapshots and resume secrets cannot. Intentional for secrets;
  undocumented split if a future setting grows an identifier.

---

## ST-005 Desktop snapshot key is a same-user file, not an OS vault

- **Severity:** Low
- **Confidence:** High
- **Evidence:**
  - `DesktopSnapshotFileSystem.kt:31-38, 40-43` `~/.parlor/snapshot-key-v1.bin`
  - `PlatformStorage.desktop.kt:18-21` in-memory `SecureKeyValueBacking`
    (credentials not durable at all)
- **Why it matters:** Documented harness limitation. Host-private
  snapshots on a shared login are readable by any same-uid process.
  Not a Store defect unless Desktop is promoted.

---

## ST-006 FileBackedSnapshotStore mutex does not cover platform FS internals

- **Severity:** Low
- **Confidence:** High
- **Evidence:**
  - `FileBackedSnapshotStore.kt:32,58` one process mutex
  - Android/iOS FS methods have **no** additional mutex; concurrent
    callers of the same `SnapshotFileSystem` from outside the store
    could interleave `list` migration with `write`
  - Production has a single `SnapshotStore` singleton (`StorageModule.kt:17`)
- **Why it matters:** Safe under current DI. A second writer (debug
  tool, future module) could race atomic replace vs legacy migrate.
  Residual, not a current production bug.

---

## ST-007 Android snapshot `readBytes` after length check still materializes 8 MiB

- **Severity:** Low
- **Confidence:** High
- **Evidence:**
  - `AndroidSnapshotFileSystem.kt:113-116` `file.length()` then `readBytes()`
  - iOS uses `readBoundedSnapshotBytes` (`IosSnapshotFileSystem.kt:400-410`)
  - Desktop: `Files.readAllBytes` after size check (`DesktopSnapshotFileSystem.kt:54-57`)
- **Why it matters:** TOCTOU on a writable filesDir is app-private, so
  not a classic traversal. Peak RAM is the full 8 MiB cap plus copies
  during decrypt. Bound exists; streaming does not. Acceptable for
  current 256 KiB game payloads.

---

## ST-008 Mafia codec imports the networking payload cap

- **Severity:** Low
- **Confidence:** High
- **Evidence:**
  - `MafiaSnapshotCodec.kt:6,50-52` `import com.parlor.networking.protocol.MAX_SNAPSHOT_PAYLOAD_BYTES`
  - Whodunit duplicates the same `256 * 1024` locally
    (`WhodunitSnapshotCodec.kt:124`)
- **Why it matters:** Local persistence is coupled to the wire protocol
  constant. Changing the LAN snapshot budget silently retunes disk
  recovery. Layering leak, not a functional bug today.

---

## ST-009 Mafia multi-device never writes local snapshots

- **Severity:** Info
- **Confidence:** High
- **Evidence:**
  - `MafiaGameFlow.kt:274-297` writer lives only in PaP `SessionDrivenFlow`
  - Grep `SerializedSnapshotWriter` / `snapshotStore` under
    `game-modes/mafia/src/commonMain` → only that PaP file
  - `MafiaSnapshotRecovery.kt:73-78` rejects any disconnected/dropped
    overlay (multi-device chrome)
- **Why it matters:** By design: room is source of truth; host death
  ends the party. Confirming the question in scope: Mafia **does**
  write local snapshots for pass-and-play; **does not** for LAN play.

---

## ST-010 Production remote/cache path is dead by construction

- **Severity:** Info
- **Confidence:** High
- **Evidence:**
  - `OfflineRemoteCaseDataSource.kt:23-27` always `Unreachable`
  - `InMemoryCachedCaseDataSource.kt:10-13` process RAM
  - `DefaultCaseRepository.kt:81-96` cache.put only after remote success
- **Why it matters:** Shipping catalog is bundled-only. Cache/remote
  branches are tested but never taken in prod. Not a defect; records
  the real content pipeline.

---

## ST-011 Bundled catalog identity matches packaged JSON 1:1

- **Severity:** Info
- **Confidence:** High
- **Evidence:**
  - `BundledWhodunitCatalog.kt:10-18` seven kebab ids
  - Seven files under `composeResources/files/cases/`; each
    `"caseId"` equals filename stem
  - `CasePickerDiscoveryTest.kt:175-188` asserts set equality
  - `BundledWhodunitCases.kt:51-60` missing resource / mismatched
    `caseId` crashes at first access
- **Why it matters:** Positive control. Adding a JSON without a catalog
  entry (or the reverse) fails tests / startup. No orphan files found.
