# Persistence map

No SQL. Three independent stores, one process-lifetime cache.

## 1. Game snapshots (`SnapshotStore`)

### Contract

`shared/storage/.../SnapshotStore.kt`: `save` / `load` / `delete` /
`listUnfinished`. Comment requires AEAD + platform file protection. The
interface itself is plaintext `GameSnapshot` JSON; protection is below it.

Envelope (`engine/.../GameSnapshot.kt`): `sessionId`, `gameId`,
`engineVersion`, `createdAt`, `phaseId`, `payload: ByteArray`,
`metadata: Map<String,String>`.

### Common implementation

`FileBackedSnapshotStore`:

- One file per session: `{sessionId.raw}.snapshot.json`
- Process mutex serializes all ops
- Encode/decode on `Dispatchers.Default`; I/O stays on the FS dispatcher
- Name filter: length ≤ 240, no `/` `\` `\0`, no `.` `..`, no `.tmp` suffix
- `listUnfinished` filters suffix, drops blanks, distinct + sorted
- `SerializationException` / `SnapshotProtectionException` → `CorruptedData`
- Other exceptions → `IoError("snapshot_io")` (no path/key text)
- `CancellationException` rethrown
- Public ctor takes `Json`; tests can inject codec + context

`SerializedSnapshotWriter` (one session):

- Mutex: persist / discard / complete-delete cannot interleave
- Dedupes identical last-successful state
- Completed → `store.delete`; else `store.save`
- Rejects `snapshot.sessionId != writer.sessionId` as `CorruptedData`
- Successful `discard` is final (late collector cannot resurrect)
- Status: Idle / Writing / Saved / Deleted / Failed
- Cancel restores previous status

`InMemorySnapshotStore`: public commonMain test/dev fake. **Not** bound in
production DI.

### Android (`AndroidSnapshotFileSystem`)

- Dir: `context.noBackupFilesDir/snapshots`
- AES-256-GCM, Android Keystore alias `com.parlor.app.snapshot.aes-gcm.v1`,
  non-exportable, randomized IV
- AAD = magic `"PARSNAP"` + filename (swap-under-other-id fails)
- Format: `PARSNAP` + version=1 + ivLen=12 + IV + ciphertext+tag
- Limits: 8 MiB plaintext, 8 MiB+1 KiB protected
- Atomic write: tmp + `fd.sync` + `ATOMIC_MOVE` (fallback REPLACE)
- Legacy: `filesDir/snapshots` plaintext JSON (`{` after whitespace)
  one-way migrate on read/`list`. Delete legacy only after protected
  winner is durable. Failed legacy delete throws (does not keep dual copy)
- `list()` migrates independently (`migrateSnapshotRecordsIndependently`);
  one corrupt legacy name stays visible

### iOS (`IosSnapshotFileSystem` + `IosSnapshotKeychain`)

- Dir: Application Support `/Parlor/snapshots`
- `NSFileProtectionComplete` + `NSURLIsExcludedFromBackupKey`
- Encrypt-then-MAC: AES-256-CBC+PKCS7 + HMAC-SHA256
- 64-byte key in Keychain: service `com.parlor.app.snapshot.v1`,
  `AfterFirstUnlockThisDeviceOnly`, `synchronizable=false`
- `loadOrCreate`: duplicate add never replaces an existing key
- Filename is MAC AAD (header + name + iv + ciphertext)
- Bounded read: `readDataOfLength(limit+1)` — never materializes oversized
- Write: `NSDataWritingAtomic | NSDataWritingFileProtectionComplete`
- Legacy: `Documents/snapshots` same one-way migrate as Android

### Desktop (`DesktopSnapshotFileSystem`) — harness only

- `~/.parlor/snapshots` + sibling `snapshot-key-v1.bin` (AES-256-GCM)
- POSIX 0600 on key file; file-channel lock for first create
- Same-user process can still read the key. Documented, not Store-grade
- No legacy-dir migrate (in-place JSON upgrade only)

### Writers / recovery

| Game | Writes local snapshot? | Recoverable? |
|---|---|---|
| Whodunit PaP / Solo | Yes, every state | Yes. Solo metadata auto-deleted as `NotFound` |
| Whodunit MultiDevice | No | Room is source of truth |
| Mafia PaP | Yes, every state | Yes; requires `playMode=PassAndPlay` |
| Mafia MultiDevice | **No** | No local resume |

Whodunit recovery (`loadResumedSession`):

- Envelope `sessionId`/`gameId` must match
- `engineVersion.major == 1` and `engineVersion <= 1.2.0`
- `playMode == Solo` → delete + `NotFound`
- unknown playMode → `CorruptedData`
- `caseVersion`/`caseDigest` both present or both absent
- decode via `WhodunitSnapshotCodec`; `phaseId` must equal state
- `validateResumedSessionForCase` binds digest to loaded case; legacy
  (no identity) accepted only if gameplay refs still match current case

Mafia recovery (`loadMafiaResumedSession`):

- same envelope checks; version `1.0.0`
- **requires** `metadata.playMode == PassAndPlay` (missing → corrupt)
- `MafiaSnapshotCodec.decode` + `isValidRecoveryState()` (reducer-reachable,
  no disconnected/dropped overlay, role-map == private keys, logs bounded)

Home inventory (`readLocalRecoveryInventory`) loads **envelopes only**.
Unreadable records stay addressable. `LocalResumeCoordinator` never
auto-deletes on decode failure.

### Codec persistence rules

Whodunit (`WhodunitSnapshotCodec`):

- Writes `{kind, schemaVersion:1, state}` (`parlor.whodunit.snapshot`)
- Strict Json; encode validates via `WhodunitStateValidator`
- Decode: wrapper keys → versioned (schema 1 only, canonical round-trip);
  else **legacy bare** with three explicit repairs (untimed revote,
  killer deflectionTargets, roleAssignmentGeneration)
- Payload cap 256 KiB (local const, same number as protocol)

Mafia (`MafiaSnapshotCodec`):

- Bare `MafiaState` JSON (no game-owned wrapper)
- Encode **and** decode require `isValidRecoveryState()`
- Canonical byte-equal round-trip
- Payload cap imports `networking.protocol.MAX_SNAPSHOT_PAYLOAD_BYTES`

Neither codec will decode another game's bytes if the envelope `gameId`
check in the recovery function runs first. Home routing uses envelope
`gameId` only (`resolveLocalResumeDestination`).

## 2. Settings (`SettingsStore`)

Keys: `reduced_motion` (default false), `language_override` (`en`/`ar` or
null), `theme_mode` (`system`/`light`/`dark`).

`PersistentSettingsStore`: read-once at construct; write-then-publish;
one mutex; invalid setter throws without mutating last value; corrupt
stored tags fall back (language→null, theme→system).

| Platform | Backing | Durability |
|---|---|---|
| Android | `SharedPreferences` `parlor_settings_v1` `MODE_PRIVATE` + `commit()` | Throws `SettingsPersistenceException` if commit false. Type-mismatch reads → null |
| iOS | `NSUserDefaults.standardUserDefaults` | **No synchronize / error** — write is fire-and-forget |
| Desktop | `Preferences.userRoot()/com/parlor/app/settings-v1` + `flush()` | Maps `BackingStoreException`/`SecurityException` |

Non-sensitive. Not mixed with snapshots or credentials.

## 3. Resumable credentials (`SecureStorage` → `ResumableCredentialStore`)

`PlatformKeyedSecureStorage`: mutex + maps backing exceptions to
`IoError("secure_storage_io")`.

Android backing (`AndroidSecureKeyValueBacking`):

- `noBackupFilesDir/secure-credentials/{key}.bin`
- AES-256-GCM, Keystore alias `com.parlor.app.secure-credentials.aes-gcm.v1`
- AAD = `"PARSEC1"` + logical key
- Key charset `[A-Za-z0-9._-]`, length 1..64
- Value ≤ 16 KiB; atomic tmp+sync+move

iOS backing (`IosSecureKeyValueBacking`):

- Keychain generic password, service `com.parlor.app.resumable-session.v1`
- Account = logical key
- `AfterFirstUnlockThisDeviceOnly`, non-sync
- Update-or-add; concurrent first-writer retries update
- Same key/value bounds

Desktop: `InMemorySecureKeyValueBacking` — credentials die with process.

`ResumableCredentialStore` (internal, transport-p2p):

- Single key `p2p-resumable-session-v1`, max 8 KiB
- Record: `{schemaVersion:1, active?, pending?}` — at least one required
- Secret is 64 hex; host fingerprint `p2f1-` + 52; never logged here
- Stage / commit / discardPending / updateGame / invalidateOwned /
  invalidateMembershipOwned / clear
- Mutex serializes RMW
- Resume candidate: same-membership pending is **not** preferred (last
  committed first); different-membership pending **is** preferred
- Corrupt JSON / invalid record → `CredentialStoreError.Corrupted`
  (not auto-deleted)
- Bytes zeroed after encode/decode
- Strict Json (`ignoreUnknownKeys=false`)

Bound into `P2pKitRoomTransport` on all three platforms via
`secureStorage = get()`.

## Atomicity / concurrency / corruption / backup

| Concern | Snapshots | Settings | Credentials |
|---|---|---|---|
| Atomic replace | Android/Desktop tmp+sync+move; iOS NSData atomic | Android `commit`; iOS none; Desktop `flush` | Android same as snapshots; iOS Keychain update |
| Process mutex | Yes (store + writer) | Yes (writes) | Yes (adapter + store) |
| Cross-process | None | Platform prefs | Keychain / Keystore |
| Crash mid-write | tmp leftover ignored by list; old file remains if move fails | iOS last write may be lost | Android tmp deleted in finally |
| Corrupt record | Visible, fail-closed, explicit discard | Fallback defaults | `Corrupted`; not wiped |
| Backup | Android allowBackup=false + exclude-all XML + noBackupFilesDir; iOS excludeFromBackup + ThisDeviceOnly | Same Android policy; iOS defaults **are** normally backup-eligible (non-secret) | Android noBackup; iOS ThisDeviceOnly non-sync |

Android backup XML excludes root/file/database/sharedpref/external **and**
device-protected counterparts for both cloud-backup and device-transfer.

No application-level backup/export/restore API exists. No schema-version
table for settings. Snapshot format version is in the ciphertext header
(v1) plus game payload schema.

## Size / memory

- Snapshot plaintext 8 MiB at FS; game codecs 256 KiB payload
- Android/iOS FS `readBytes` after length check (Android) / bounded read (iOS)
- Credential 16 KiB value / 8 KiB JSON record
- Case remote adapter (unused in prod): list 256 KiB, case 512 KiB,
  read limit+1 then discard
- No streaming backup; no whole-vault DTO
