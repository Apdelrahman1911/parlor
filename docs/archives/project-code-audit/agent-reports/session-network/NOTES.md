# Session / network workstream — working notes

Code-only reconstruction. No `*.md` outside `project-code-audit/` was read.

## Protocol

- Wire version is **4.2** (`PARLOR_PROTOCOL_MAJOR` / `PARLOR_PROTOCOL_MINOR`).
- Compatibility is **exact** `ProtocolVersion` equality, not major-only.
  `ProtocolVersion.isCompatibleWith` is `this == other`. Comment: strict schema,
  a newer optional field would break an older decoder.
- Game identity is a second exact gate: `gameId` + `gameVersion` must match
  (`WhodunitHostRoomBridge.GAME_VERSION = 6`, `MafiaHostRoomBridge.GAME_VERSION = 2`).
- Codec: CBOR, `ignoreUnknownKeys = false`, `alwaysUseByteString = true`.
  Frame cap `MAX_ROOM_FRAME_BYTES = 272 KiB`.
- Payload caps: command 32 KiB, snapshot 256 KiB, control 8 KiB.
- `connectionEpoch` is documented as stable `1L`; stale sockets are rejected by
  room/session ownership, not by rotating this field.

## Authority

- Host is the only reducer owner. `HostAuthoritativeSessionCoordinator` is a
  single-writer mailbox. Peers use `PeerAuthoritativeSessionCoordinator` +
  `ShadowSessionController` and **never** reduce.
- Transport overwrites every peer-authored `actor` with the authenticated
  P2pKit peer id before the host sees the frame (`HostP2pRoom` inbound
  collector). `InMemoryPeerRoom` mirrors that stamp.
- Payload impersonation of another seat is not possible on the production
  path if the overwrite stays on every `PeerMessage` with an `actor` field.
  `LeaveNotice` has no actor.

## Start barrier (v4)

- Offer (`SessionStarting`) → peer prepare + `SessionStartReady` →
  `SessionStartCommitted` → best-effort `SessionStartCommitAck`.
- Commit is irreversible. Lost acks do not roll back. Host retries the same
  `startId`. Peer lobby has **no** pre-offer timeout.
- `sessionNonce` is public (room-code `hashCode`) and is **not** the role seed.
  Host seed stays in `ProcessMultiplayerSession.hostSeed` / reducer context.

## Transport

- Production `RoomTransport` is always `P2pKitRoomTransport` via
  `p2pBootstrapModules()` → `p2pTransportModule`. Desktop is a harness
  (in-process identity store).
- Discovery advertises `parlor-room|<deviceTag>` only. Room code never goes
  on Bonjour. Join still browses LAN internally; `supportsDiscovery = false`
  (no UI room list). `supportsManualEndpointConnection = false`.
- `JoinConfig.rejoinToken` is rejected (`Unauthorized`). Resume is
  `resumeLastSession()` + rotated credential, not the deprecated token field.
- P2pKit security: `AuthenticatedV2(AcceptAnyAuthenticatedSameApp)`. Same-app
  TCP is not user identity; room code + host approval + credential digest are.

## P2pKitRoomTransport.kt coverage

File is 4589 lines. Read in full, in order:

| Range | Contents |
|---|---|
| 1–400 | class header, lifecycle channel, `host()`, `join()` start |
| 401–800 | join dial loop, `resumableSession()`, `resumeLastSession()` |
| 801–1200 | credential invalidate, `awaitAdmission`, `resumeConnectionDetailed`, `awaitResume` start |
| 1201–1629 | resume commit, credential mapping, constants, helpers |
| 1630–2028 | `HostP2pRoom` maps, inbound collector, actor stamp |
| 2029–2428 | LeaveNotice, session state, admission/resume request |
| 2429–2828 | `completeResume`, ready barriers, approve/reject/`closeAdmissions`/`retireDisconnectedMember` |
| 2829–3228 | `admit()`, rollback, traffic, background |
| 3229–3557 | foreground, expiry, send/leave, `PeerP2pRoom` start |
| 3558–3957 | peer lifecycle, adopt/replace, admission/resume ready |
| 3958–4589 | incoming collector, terminal invalidation, send/leave/finalLeave/closeForRetry |

No skim-by-filename.

## Tests / Ignore

- Session + networking common tests: no `@Ignore`.
- Transport: three `@Ignore` physical-LAN tests in
  `P2pKitRoomTransportLoopbackTest` (join membership, message round-trip,
  broadcast). Host advertise-only test is live.
- Coordinator/start/outbox/owner/handshake suites are large and in-memory.

## Threats checked

| Threat | Code verdict |
|---|---|
| Actor impersonation | Overwritten on host + InMemory peer send |
| Replay of applied command | `commandId` ledger (256/peer) + client sequence |
| Non-idempotent retry | Peer never replays; queries `CommandOutcomeRequest` |
| Split-brain / host migration | None. Host death / 120s lifecycle expiry ends the room |
| Unbounded retry | All loops have absolute deadlines (join 30s, start 20s, rejoin 120s, outcome 20s) |
| CancellationException | Rethrown across session/transport `catch`es inspected |
| Queue bounds | Present, but see SN-001 / SN-004 / SN-007 |
| Cleanup leaks | Idempotent `leave()`, kit `stopAfterFailure`, owner orphan close |
