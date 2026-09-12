# Protocol reconstruction (from code)

## Version and compatibility

```
PARLOR_PROTOCOL_MAJOR = 4
PARLOR_PROTOCOL_MINOR = 2
ProtocolVersion.isCompatibleWith(other) = this == other
```

Exact major.minor. A 4.1 peer cannot decode 4.2 (`nextExpectedClientSequence`
on snapshots). A 4.3 host would be rejected by 4.2 peers. Tests in
`ProtocolValidationTest` assert both newer and older minors fail.

Game version is a third exact field on `SessionEnvelopeHeader`. Mismatch →
`IncompatibleGameVersion` / `CommandStatus.IncompatibleVersion`.

`connectionEpoch` defaults to `1L` and is compared for equality. It is not
rotated per reconnect.

## Wire codec

`RoomMessageCodec`: kotlinx CBOR, `encodeDefaults = true`,
`ignoreUnknownKeys = false`, `alwaysUseByteString = true`.

Limits (`Protocol.kt`):

| Constant | Value |
|---|---|
| `MAX_COMMAND_PAYLOAD_BYTES` | 32 KiB |
| `MAX_SNAPSHOT_PAYLOAD_BYTES` | 256 KiB (public+private) |
| `MAX_CONTROL_PAYLOAD_BYTES` | 8 KiB |
| `MAX_ROOM_FRAME_BYTES` | 272 KiB |

Transport additionally applies directional caps
(`P2pTrafficLimits.MAX_PEER_TO_HOST_FRAME_BYTES` = 40 KiB,
`MAX_HOST_TO_PEER_FRAME_BYTES` = 272 KiB).

## Message catalog

### Host → peer (`HostMessage`)

| Type | Role |
|---|---|
| `AdmissionAccepted` | **Deprecated.** Peer treats as `IncompatibleProtocol`. |
| `AdmissionOffered` | Credential offer + `hostDisplayName`. |
| `AdmissionPending` | Waiting for host tap. |
| `AdmissionCommitted` | Peer may promote staged secret. |
| `AdmissionRejected` | `AdmissionRejection` enum. |
| `ResumeOffered` / `ResumeCommitted` | Rotated generation. |
| `PlayerSnapshot` | Atomic public+own-private + `revision` + `nextExpectedClientSequence`. |
| `CommandResult` | Idempotent outcome + next sequence. |
| `Heartbeat` | `authoritativeRevision` hint. |
| `SessionEnded` | Terminal; `SessionEndReason`. |
| `SessionStarting` | Immutable start offer (`startId`, roster, case digest, public nonce). |
| `SessionStartCommitted` | Irreversible enter-gameplay. |

### Peer → host (`PeerMessage`)

| Type | Role |
|---|---|
| `AdmissionRequest` | First encrypted frame; `actor` overwritten. |
| `AdmissionConfirmed` / `AdmissionCommitAck` / `AdmissionReady` | Stage → promote → inbox attached. |
| `ResumeRequested` / `ResumeConfirmed` / `ResumeCommitAck` / `ResumeReady` | Same for reconnect. |
| `ClientCommand` | `commandId`, `clientSequence`, `expectedRevision`, payload. |
| `SnapshotRequest` | `lastAppliedRevision` (`-1` = none). |
| `SessionHeartbeat` | Peer liveness + last revision. |
| `CommandOutcomeRequest` | Query ledger; never replay. |
| `SessionStartReady` / `SessionStartCommitAck` | Start barrier. |
| `LeaveNotice` | Permanent lobby leave (no actor). |

## Validation (`ProtocolValidation.kt`)

Header: protocol equality, ASCII entity ids (`[A-Za-z0-9_-]{1,128}`),
opaque ids 16–128 same alphabet, `sequence >= 0`, `connectionEpoch > 0` and
equal to expected.

Commands: `commandId == header.messageId`, `clientSequence > 0`,
`expectedRevision >= 0`, payload ≤ 32 KiB.

Snapshots: `revision >= 0`, `nextExpectedClientSequence > 0`, combined
payload ≤ 256 KiB.

`SessionStarting`: `startId == messageId`, `sequence == 0`, 2–16 distinct
players, seats `0..n-1`, distinct display names, caseVersion/digest both
present or both absent, SemVer + lowercase SHA-256.

Start acks: `sequence == 0`.

## Admission rejections

`WrongCode`, `HostDeclined`, `RoomFull`, `SessionStarted`,
`IncompatibleProtocol`, `InvalidRequest`, `RateLimited`, `InvalidCredential`,
`ExpiredCredential`, `AlreadyConnected`, `DisplayNameInUse`.

## Command statuses

`Applied`, `Duplicate`, `InvalidAction`, `Unauthorized`, `StaleRevision`,
`SequenceGap`, `IncompatibleVersion`, `PayloadTooLarge`, `SessionEnded`,
`UnknownCommand`, `SessionSuspended`.

## Identity / secrets

- Room code: 6 chars, alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no I/O/0/1).
- Display name: trim, 1–32, no ISO control / FORMAT, well-formed surrogates.
  Equality is exact and case-sensitive.
- Rejoin secret: 256-bit hex (`SecureIds.rejoinToken256`). Host stores SHA-256
  only (`HostCredential.digest`). Compared with `SecureHashes.constantTimeEquals`.
- Host fingerprint pin: `p2f1-` + 52 hex (length 57) on stored credentials.
- Session/command/start ids: 128-bit hex CSPRNG.
- Role seed: `SecureIds.randomLong()` on host only. Never on the wire.

## What is never serialized to a peer

Coordinator snapshots are `snapshotFor(playerId)` → public projection + that
player’s private slice. `ShadowSessionController.hostState` / `canonicalState`
are `null`. Bridges re-check `toPublic(publicState).state == publicState`
before install (fail closed on host-only fields).
