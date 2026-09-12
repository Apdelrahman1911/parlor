# Gaps — security / privacy

## Closed by code (do not re-open without a regression)

- Projection: both games strip `privatePerPlayer` / `hostOnly` at
  `toPublic` / `toPlayer`. Host bridges send own-slice private bytes only.
  Peers require `toPublic(decoded) == decoded` plus a game validator.
- Actor identity: transport overwrite of every inbound `PeerMessage.actor`.
- Rejoin: 256-bit secret, host SHA-256 + `constantTimeEquals`, generation
  rotation, fingerprint pin, expiry. Deprecated `AdmissionAccepted`
  rejected as incompatible. `LocalRoom.rejoinToken` never published.
- Room code not in mDNS. Display names drop ISO control / FORMAT chars.
  Wire entity IDs `[A-Za-z0-9_-]`.
- Production seed = `SecureSessionSeedSource` → `SecureIds.randomLong()`
  (CSPRNG). `sessionNonce` is public `room.code.hashCode()` and is **not**
  the role seed. Peer runtimes force `randomSeed = 0L`.
- Content: `OfflineRemoteCaseDataSource` only. No composeApp `HttpClient`.
- No debug screens / `@Preview` game UIs / `src/debug` hooks in shipping
  source sets.
- `gradle/verification-metadata.xml` exists.

## Not proven (runtime / pentest)

- Real-radio join, actor stamp, snapshot isolation, 17 seats (SP-008 / SN-003).
- TalkBack/VoiceOver during handoff and leftover reveal (SP-009).
- Backup / Quick Start / OEM `allowBackup` honor (SP-010).
- Crowded LAN: many `parlor-room|*` advertisers + WrongCode probing (SP-006).
- Fingerprint pin after host reinstall (new P2pKit identity, same typed code).
- `AcceptAnyAuthenticatedSameApp`: any Parlor install can complete the
  P2pKit handshake and then hit admission. App layer is the real gate;
  not exercised against a modified client.

## Overlaps other workstreams (not re-filed as P0 here)

- AR-003 / AR-004: public Ktor adapter + commonMain fakes (`FakeClock`,
  `InMemorySnapshotStore`, `InMemorySettingsStore`).
- SN-001 / SN-004: small host/peer mailboxes (DoS / stall, not data leak).
- Mafia day-vote `castSoFar` is **public by spec**. Whodunit collecting
  targets are redacted; voter keys stay.

## Intentionally residual

- Room code ≈ 30 bits + host approval. Not a password; it is a same-room
  selector.
- iOS/Android keys usable after first unlock (background resume).
- Desktop is not a Store target (SP-004).
- PrivacyInfo `NSPrivacyCollectedDataTypes` is empty. Display names and
  room codes stay on-LAN. Store-review judgment is out of this audit.
- iOS has **no** `.entitlements` / `CODE_SIGN_ENTITLEMENTS`. Local Network
  is Info.plist-only (`NSLocalNetworkUsageDescription`, `_p2pkit2._tcp`).
  No push / associated-domains / iCloud entitlements observed.

## What this workstream did not read

- Full `P2pKitRoomTransport.kt` (4589): admission/resume/diagnostics
  sections only; session-network owns the rest.
- Full bodies of every leak/codec test after the production contracts.
- Engine reducers beyond projection + observable validators.
- Any `*.md` outside `project-code-audit/`.
