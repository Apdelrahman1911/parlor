# Findings — security / privacy (SP-001+)

Severity: P0 ship-blocker · P1 likely user-visible / security · P2 latent /
defense-in-depth · P3 hygiene.

**Status:** Confirmed = source proves it. Needs runtime = device / TalkBack /
backup / LAN pentest required.

No confirmed host-only or other-player private leak on the multiplayer
snapshot path. The strongest confirmed issue is **seed-in-filename** on
local P&P snapshots.

---

## SP-001 — Pass-and-play snapshot filenames embed the gameplay seed

**Severity:** P2  
**Status:** Confirmed  
**Symbols:** `SessionDrivenFlow` / Mafia P&P `sessionConfig`;
`FileBackedSnapshotStore.fileName`; `AndroidSnapshotFileSystem` /
`IosSnapshotFileSystem`.

Fresh local sessions name themselves from the CSPRNG seed:

- Whodunit: `SessionId("local-${seed.toString(16)}")`
  (`WhodunitGameFlow.kt:764-770`)
- Mafia: `SessionId("mafia-local-${seed.toString(16)}")`
  (`MafiaGameFlow.kt:241`)

`FileBackedSnapshotStore` writes `${sessionId.raw}.snapshot.json`
(`FileBackedSnapshotStore.kt:123`). The same seed is `hostOnly.randomSeed`
and fully determines hidden-role assignment.

Ciphertext is AEAD-protected and (on Android) under `noBackupFilesDir`.
The **filename is not**. A listing of the snapshot directory — adb,
forensics, a backup-policy regression — yields the seed without breaking
Keystore/Keychain.

Home tiles do not display the raw id (`HomeScreen.kt:246-285`).
Multiplayer runtimes do **not** persist snapshots; `mp-host-${seed}`
exists only in RAM.

**Fix shape:** random `SecureIds.id128()` session ids; keep seed only
inside the authenticated payload.

---

## SP-002 — Public Ktor remote content client ships on the production classpath

**Severity:** P2  
**Status:** Confirmed  
**Symbols:** `KtorRemoteCaseDataSource`; `contentModule`;
`:shared:content` `ktor-client-core`.

Production DI binds `OfflineRemoteCaseDataSource()`
(`ContentModule.kt:59`). composeApp has **no** `HttpClient` Koin bind.
`KtorRemoteCaseDataSource` is still a public class on commonMain
(`KtorRemoteCaseDataSource.kt:40`) with path-safe ids, bounded reads
(256/512 KiB), and strict JSON. No engine is pulled into the app.

A one-line Koin change plus an engine would put an unreviewed HTTPS
client on the INTERNET-permissioned app. Overlaps architecture AR-003.

---

## SP-003 — Mafia `toPublic` does not re-null living `revealedRole`

**Severity:** P3  
**Status:** Confirmed (defense-in-depth; not a current leak)  
**Symbols:** `MafiaProjectionPolicy.toPublic`;
`MafiaObservableStateValidator.validateRoleVisibility`.

Whodunit vote targets are redacted **inside** the policy. Mafia living
roles are “null by reducer construction” (`MafiaProjectionPolicy.kt:17-20`).
`toPublic` / `toPlayer` do not force `revealedRole = null` for living
slots.

Peer install still fail-closes: `MafiaPeerSnapshotValidator` →
`MafiaObservableStateValidator` rejects `slot.alive && revealedRole != null`
(`MafiaObservableStateValidator.kt:350-353`). A reducer regression would
be dropped by the peer, not painted. A future caller that uses `toPublic`
without the validator would not get that belt.

---

## SP-004 — Desktop snapshot key is same-user plaintext; credentials are RAM-only

**Severity:** P3  
**Status:** Confirmed (desktop is a non-shipping harness)  
**Symbols:** `DesktopSnapshotFileSystem`; `PlatformStorage.desktop.kt`.

AES-256-GCM with `~/.parlor/snapshot-key-v1.bin` (POSIX 0600). Any other
process as the same OS user can read the key and every snapshot. Comments
state this is not equivalent to Keystore/Keychain
(`DesktopSnapshotFileSystem.kt:31-38`).

`InMemorySecureKeyValueBacking` is the desktop credential bind
(`PlatformStorage.desktop.kt:18-21`). Rejoin secrets die with the process.

Android/iOS binds are Keystore + `noBackupFilesDir` and Keychain
`AfterFirstUnlockThisDeviceOnly` / non-sync.

---

## SP-005 — Production P2pDiagnostics always emit to logcat / NSLog

**Severity:** P3  
**Status:** Confirmed  
**Symbols:** `BoundedP2pDiagnostics`; `platformP2pDiagnosticWriter`.

Closed vocabulary: event / role / result / reason / count bucket. Export
line has no room code, secret, fingerprint, display name, or payload
(`P2pDiagnostics.kt:100-109`). `LocalRoom.rejoinToken` is hard-null
(`P2pKitRoomTransport.kt:3612-3613`).

Writers are unconditional: Android `Log.i("ParlorP2p")`, iOS `NSLog`,
Desktop `println`. A nearby `adb logcat` / Console.app observer gets
join/command cadence, not secrets.

---

## SP-006 — Room-code `WrongCode` is a distinct oracle; compare is not constant-time

**Severity:** P3  
**Status:** Confirmed (mitigated)  
**Symbols:** `handleAdmissionRequest`; `RoomInputPolicy`;
`AdmissionAttemptLimiter`.

Code is 6 chars × 32-symbol alphabet (`RoomInputPolicy.kt:5-6`) ≈ 30 bits,
CSPRNG, **not** in mDNS (`P2pKitRoomTransport.kt:266-267`). Compare is
`request.roomCode != roomCode` (`P2pKitRoomTransport.kt:2175-2176`).
Mismatch → `AdmissionRejection.WrongCode`, mapped to diagnostics
`WRONG_ROOM`.

Limiter: 3/peer then 1/10s; global 32 then 1/s; 128 identities
(`P2pTrafficPolicy.kt:48-56`). Host tap is still required after a correct
guess. Residual: a same-AppId LAN client can distinguish “wrong code”
from “declined / full” and spend the limiter probing advertised
`parlor-room|*` hosts.

---

## SP-007 — Dependency verification hashes artifacts but not signatures

**Severity:** P3  
**Status:** Confirmed  
**Evidence:** `gradle/verification-metadata.xml:4-5`
`verify-metadata=true`, `verify-signatures=false`.

File exists and is populated (not missing). A compromised mirror that
serves a new hash is caught; a hash that was recorded from a bad first
fetch is not. Out of scope to regenerate.

---

## SP-008 — Physical LAN privacy (actor stamp, isolation, 17 seats) is unproven on radio

**Severity:** P1 (coverage)  
**Status:** Needs runtime  
**Symbols:** `P2pKitRoomTransport` actor overwrite (`:1890-1910`);
`P2pKitRoomTransportLoopbackTest` `@Ignore` (session-network SN-003).

Source **does** overwrite every `PeerMessage.actor` with the authenticated
peer id, encode `toPublic` + own private only, and reject non-fixed-point
public payloads. The only real-P2pKit test that runs is “host advertises
a 6-char code”. Join, actor overwrite on a live session, and N-peer
broadcast are ignored.

Needs two/three physical devices before calling the LAN privacy story
proven.

---

## SP-009 — TalkBack / leftover pass-and-play private screens

**Severity:** P2  
**Status:** Needs runtime  

Source spot-check is clean for **hidden** roles:

- WaxSeal a11y is `reveal_gate_a11y` (no role) (`WaxSealReveal.kt:67,148-149`)
- Mafia handoff `contentDescription` is player name only
  (`MafiaHandoffScreens.kt:109`)
- P&P reveal uses `when (stage)` so the role card is not composed under
  hide (`MafiaPassAndPlayPhaseRouter.kt:188-214`)
- Host-lost overlays `clearAndSetSemantics { }`
- Home resume a11y is game title + position, not session id

`PrivateRoleCardScreen` paints role as ordinary `Text` **after** the
player confirms they are alone (`PrivateRoleCardScreen.kt:62-73`).
TalkBack will announce it then (intended). Unproven: VoiceOver focus
order during the gate, and a phone handed over while the reveal stage
is still up.

---

## SP-010 — Backup / device-transfer / unlocked-device extraction

**Severity:** P2  
**Status:** Needs runtime  

Android: `allowBackup=false` + exclude-all `backup_rules.xml` and
`data_extraction_rules.xml` (cloud **and** device-transfer) + ciphertext
in `noBackupFilesDir`. iOS: `NSURLIsExcludedFromBackupKey` on snapshot
dir/files; Keychain `ThisDeviceOnly` + `kSecAttrSynchronizable=false`.
Keys are **not** user-authentication bound (no biometric); they work
after first unlock so the 120s background resume can run.

Needs: Android 11- and 12+ backup/restore onto a second device; iOS
Quick Start / unencrypted iTunes backup; confirmation OEM images honor
`allowBackup=false`. Rooted/jailbroken listing of snapshot **filenames**
is SP-001 even if AEAD holds.
