# Storage and data review

## Map

| Store | What | Where | Atomicity |
|---|---|---|---|
| Settings | language, theme, reduced motion | Android SharedPreferences commit; iOS NSUserDefaults (no synchronize); Desktop Preferences.flush | typed persistence exception on Android/Desktop |
| Snapshots | full `GameSnapshot` JSON then platform AEAD | Android noBackup + Keystore; iOS App Support + Keychain; Desktop `~/.parlor` file key | store mutex + replace; common layer is plaintext JSON (ST-001) |
| Rejoin credentials | secret + metadata | Android/iOS SecureStorage; Desktop RAM-only | stage/commit/rollback in transport |
| Content | 7 Whodunit JSON in composeResources | packaged | catalog identity test |

No Room/SQLite/SQLDelight in the shipping graph. No schema migrations —
snapshot codecs fail-closed on version/shape mismatch.

## Confirmed

- **ST-001:** AEAD is a platform-FS convention, not enforced by `SnapshotStore`.
- **ST-003:** iOS settings writes are unchecked — prefs can look saved and vanish.
- **ST-005 / SP-004:** Desktop key is same-user plaintext file.
- **ST-009:** Mafia MP never writes snapshots (by design).
- **ST-011:** catalog ↔ JSON 1:1 (positive).
- **F-005:** filename contains seed (privacy + integrity of secrecy).

## Recovery

- Whodunit/Mafia codecs refuse reducer-impossible current-schema states.
- Do not normalize into another game (verified: no cross-game decode).
- Home distinguishes loading / ready / unavailable / unreadable (discard UI).
- Corrupt file → typed error, not silent empty.

## Backup

Android deny-all backup + data-extraction rules. iOS exclude snapshots;
settings *are* backup-eligible (non-secret). Needs OEM/device confirmation
(SP-010).
