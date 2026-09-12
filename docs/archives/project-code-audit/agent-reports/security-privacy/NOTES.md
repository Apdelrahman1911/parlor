# Security / privacy workstream — NOTES

CODE-ONLY. No `*.md` outside `project-code-audit/` was read. Production
untouched. No commit.

## Threat surface (as implemented)

Same-LAN host-authoritative play. P2pKit `AuthenticatedV2` +
`AcceptAnyAuthenticatedSameApp` opens an encrypted session to any other
Parlor build on the LAN. Application admission is then: 6-char room code
(not advertised), explicit host approval, display-name uniqueness, and a
rotating 256-bit rejoin secret (host stores SHA-256 only).

There is no public internet play, no browsable room list, no raw-IP join,
no spectators, no host migration.

## Projection (Whodunit + Mafia)

Both policies implement the engine contract:

- `toPublic`: empty `privatePerPlayer` + sentinel empty `hostOnly`
- `toPlayer(id)`: only `privatePerPlayer[id]` + same host-only sentinel
- `toHost`: full canonical state

Whodunit also redacts in-progress vote *targets* (`castSoFar` values →
`PlayerId("redacted")`); voter keys stay. Resolved/Tied tallies are public
by design.

Mafia day-vote `castSoFar` is **public by spec** (`ActiveVote` kdoc). Night
coordination, detective results, doctor history, suspicions, and the full
role map live only in private / host-only buckets. `withTerminalRoleReveal`
copies `hostOnly.fullRoleMap` onto the public roster **only** in
`MafiaPhase.PostGame`, then still strips `hostOnly`.

Host bridges encode `toPublic(state)` as the public payload and **only**
`state.privatePerPlayer[recipient]` as the private payload. Peers reject
any public payload that is not a fixed point of `toPublic` and then run
game-specific validators (`WhodunitStateValidator.isValidPeerProjectionForCase`,
`MafiaPeerSnapshotValidator`). Accidental canonical placeholders are
re-projected before first paint.

Mafia policy does **not** independently null `PublicPlayerSlot.revealedRole`
during play. Living-role secrecy is a reducer invariant, re-checked by
tests, not re-enforced at the projection boundary.

## Protocol / identity / credentials

- Exact major.minor (`4.2`). Wire entity IDs restricted to `[A-Za-z0-9_-]`.
- Every inbound `PeerMessage` actor is overwritten with the authenticated
  P2pKit peer id before the host sees it (`P2pKitRoomTransport` ~1890–1910).
- Deprecated `AdmissionAccepted` is still decoded, then **rejected** as
  `IncompatibleProtocol`. Live path is Offered → stage → Confirmed →
  Committed. `JoinConfig.rejoinToken` on initial join is rejected.
- Host resume: SHA-256 of presented secret, `constantTimeEquals`, generation
  match (current or previous), display-name + fingerprint pin, expiry.
- Peer `LocalRoom.rejoinToken` is hard-`null`; secret never enters UI state.
- Room code: CSPRNG `SecureIds.randomCharacters`, alphabet 32, length 6.
  Advertisement is `parlor-room|*` plus a random device tag — **code is not
  in mDNS**. Compare is not constant-time; `WrongCode` is a distinct reject.
- Rate limit: 3/peer then 1/10s; global 32 then 1/s; 128 tracked identities.
- `sessionNonce` = `room.code.hashCode()` — documented **public**, not the
  role seed. Host seeds are `SecureIds.randomLong()` /
  `SecureSessionSeedSource`. Peer runtimes force `randomSeed = 0L`.

## Storage

| Binding | Android | iOS | Desktop |
|---|---|---|---|
| Snapshots | AES-256-GCM, Keystore, `noBackupFilesDir`, filename as AAD | AES-256-CBC+HMAC, Keychain ThisDeviceOnly, App Support + exclude-from-backup + complete Data Protection | AES-256-GCM, owner-only key file under `~/.parlor` |
| Credentials | AES-256-GCM, Keystore, `noBackupFilesDir/secure-credentials` | Keychain generic password, `AfterFirstUnlockThisDeviceOnly`, non-sync | **InMemory** (harness) |
| Settings | private SharedPreferences, no secrets | NSUserDefaults | Java Preferences |

P&P persists **canonical** state (includes `hostOnly`) through
`SerializedSnapshotWriter` + game codecs. Multiplayer host/peer runtimes
do **not** attach that writer.

Local session ids are `local-${seed.toString(16)}` (P&P) and
`mp-host-${seed.toString(16)}` (MP host, RAM only). Snapshot files are
`${sessionId.raw}.snapshot.json` — the P&P filename therefore contains the
gameplay seed. Home tiles do not display the raw id.

## Platform policy

- Android: `allowBackup=false`, exclude-all `backup_rules` +
  `data_extraction_rules` (cloud + device-transfer), `usesCleartextTraffic=false`.
  Permissions: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE,
  CHANGE_WIFI_MULTICAST_STATE. No location / Nearby / Bluetooth. Release
  `isDebuggable=false` + R8.
- iOS: no `.entitlements` file, no `CODE_SIGN_ENTITLEMENTS`. Info.plist has
  `NSLocalNetworkUsageDescription` + `_p2pkit2._tcp`. PrivacyInfo:
  tracking=false, empty collected types; UserDefaults CA92.1, FileTimestamp
  C617.1, SystemBootTime 35F9.1.
- `gradle/verification-metadata.xml` exists (`verify-metadata=true`,
  `verify-signatures=false`). Not regenerated.

## Diagnostics / a11y / remote / debug

- `P2pDiagnostics`: closed enums only. Export line is seq/elapsed/event/role/result/reason/count.
  Production writers: Android `Log.i("ParlorP2p")`, iOS `NSLog`, Desktop `println`.
- Spot-check: WaxSeal a11y is `reveal_gate_a11y` (no role). Mafia handoff
  `contentDescription` is player name only. `PrivateRoleCard` paints role as
  `Text` **after** the player confirms they are alone — TalkBack will read it
  then (intended). Home resume a11y uses game title + position, not session id.
- Production DI: `OfflineRemoteCaseDataSource`. `KtorRemoteCaseDataSource` is
  a public class on `:shared:content` commonMain (`ktor-client-core` only; no
  engine). composeApp has **no** `HttpClient` bind.
- No debug screens, `@Preview` game UIs, or test hooks in android/ios/common
  production source sets. `FakeClock` / `InMemorySnapshotStore` /
  `InMemorySettingsStore` / `InMemorySecureKeyValueBacking` live in commonMain.

## Randomness

Platform CSPRNG: Android/Desktop `SecureRandom`, iOS `SecRandomCopyBytes`.
IDs 128-bit hex, rejoin 256-bit hex, room-code rejection sampling (no modulo
bias). Production `SessionSeedSource` = `SecureSessionSeedSource` (pinned by
test).
