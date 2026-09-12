# Session reconstruction (from code)

## Process owner state machine

`ProcessMultiplayerSessionOwner` owns **one** route per process.

```
Idle
  │ acquire(route)
  ▼
Opening(route, Host|Join|Resume)
  ├─ success → Active(session)
  ├─ failure → Failed(route, error, retryMode)
  └─ leave/cancel → orphan room.leave() → Idle

Active
  ├─ peer start fail → preparePeerRetry → closeForRetry → Retryable
  ├─ host start fail → prepareHostRetry → leave + terminate → Retryable
  ├─ finalLeave     → peer: room.finalLeave(); host: terminate+leave → Idle
  ├─ lifecycle Expired → closeRuntime → Failed(RejoinExpired, Resume)
  └─ other route acquire → AlreadyConnected

Retryable / Failed
  ├─ acquire same route → Resume (if retained room or resumeExistingSession)
  ├─ discardRetainedRoute / leaveRoute → discardRejoinCapability → Idle
  └─ abandonFailedRoute → Idle if no retained room

Closing is exclusive (CommandInFlight if another close is running).
```

`ProcessMultiplayerSession`:

- `freezeAdmissions()` is exactly-once (`closeAdmissions()`), survives UI cancel.
- `getOrCreateCheckpoint` / `getOrCreateRuntime` are kind-checked, released-fail-closed.
- Lifecycle watcher is a **sibling** of `sessionJob` so expiry can join children.
- `closeRuntimeAndScope()` joins with 5s timeout.

`MultiplayerSessionRoute` factories: host (no code), peer join (canonical
code+name), peer resume (optional blank name). `toString()` omits code/name.

DI: `AppModule` singleton on `named("multiplayerSession")` SupervisorJob +
Default dispatcher. `App.kt` injects owner and restores shell route from
`state.routeOrNull`.

## Host coordinator

`HostAuthoritativeSessionCoordinator` — one mailbox, capacity **8**
(`HOST_MAILBOX_CAPACITY`). Work: Incoming, Publish, HostMutation, End,
BeginStart / RetryStart / StartDeadline / AbortStart, ResendStart family,
Heartbeat (10s).

### Command sequencing

Per actor:

1. Validate header/payload.
2. Unknown actor → `Unauthorized` (not remembered).
3. Known `commandId` → replay cached `CommandResult`.
4. `clientSequence != next` → `Duplicate` (lower) or `SequenceGap` (higher);
   **sequence not consumed**; snapshot sent.
5. Sequence accepted → `next += 1` **before** apply.
6. `expectedRevision != revision` → `StaleRevision` (remembered), snapshot.
7. `applyCommand` → Applied (revision++ + snapshots) / Invalid / Unauthorized
   (remembered).

Ledger: `LinkedHashMap` per actor, **256** ids (`MAX_REMEMBERED_COMMANDS_PER_PEER`).
Oldest evicted. `CommandOutcomeRequest` returns cached or `UnknownCommand`.

Host mutations share the mailbox so host UI and peer commands are totally
ordered. `applyLifecycleMutation` may run while room is `Suspended`;
`applyHostMutation` requires `Active`.

### Start transaction

`startSession` builds immutable `SessionStarting` (`sequence = 0`,
`messageId = startId`). Worker:

- Empty remote set → commit immediately.
- Else send offer, exponential retry 250ms → 2s, absolute **20s** ready deadline.
- All Ready → set `completedStart`, `startState = Started`, complete caller,
  send commit, wait acks (delivery only). Ack deadline elapsing still
  `finishCommitDelivery`.
- Pre-commit timeout or abort → `SessionEnded(Cancelled)` best-effort, Failed.
- Caller cancel after Ready quorum **cannot** abort (`processStartAbort` no-ops
  if already Started).

`resendStart(playerId)` replays the **same** offer/commit for one seat, isolated
deadlines. Concurrent same-seat resend → `CommandInFlight`.

Game traffic (`canProcessGameTraffic`) only after Started / NotRequired.

### Outbound

One `BoundedPeerOutbox` per remote player:

- 32 FIFO `CommandResult`s; newest snapshot; newest heartbeat.
- Terminal `SessionEnded` clears pending, one shared delivery.
- Send timeout 2s. Control burst of 4 then snapshot.
- Conservative byte ceiling: `32*8KiB + 8KiB + 2*272KiB`.

## Peer coordinator

`PeerAuthoritativeSessionCoordinator` — **does not reduce**.

- One in-flight command (`CommandInFlight` otherwise).
- Sequence assigned locally, then overwritten from snapshot / result
  `nextExpectedClientSequence`.
- Send failure / cancel → `Ambiguous` + `CommandOutcomeRequest` loop
  (500ms → 2s, 20s deadline) → `RecoveryTimedOut`. Fresh host traffic
  restarts lookup. **Never replay.**
- Snapshots: validate, skip seen `messageId` (2048 ring) and
  `revision <= lastInstalled`. Failed install does **not** consume id/revision.
- After start handshake, revision starts at `-1`; initial snapshot recovery
  retries 250ms–2s for 20s.
- Duplicate `SessionStarting` / `SessionStartCommitted` re-acked only if
  identical to `acceptedStartOffer` / `acceptedStartId`.
- `hostState`/`canonicalState` stay null on `ShadowSessionController`.

## Start handshake (peer lobby)

`awaitAuthoritativeSessionStart`: sole collector of `incoming` + `peerEvents`
+ `lifecycle`. No lobby timeout. On valid offer: prepare (20s) then Ready,
then fresh 20s for commit. `HostLost` is non-terminal; `HostRestored`
re-sends Ready. Terminal lifecycle or valid `SessionEnded` → NotConnected.

## Connection tracker

`PeerConnectionTracker`: durable `hostLost` / `selfOffline`. First `HostLost`
starts a **non-extendable** grace (`hostLostTimeoutMs`, 120s in both game
bridges). Restore after expiry is ignored. Close joins child timers.

## Game bridges

Both hosts:

- `PassAndPlaySessionController` is the reducer. Bridges wrap submit through
  the coordinator.
- Production uses `reconcileRoomTopology = true` (members StateFlow), not
  raw `PeerEvent` (tests use events).
- Disconnect → `MarkPlayerDisconnected` + 120s grace →
  `retireDisconnectedMember` then `ContinueWithoutPlayer`. If revoke succeeds
  but reducer fails → `terminate(Cancelled)` (no split roster).
- Reconnect → `resendStart` loop until Ready+CommitAck, then
  `MarkPlayerReconnected`.
- Whodunit also pauses on `Suspended`/`Resuming` and resumes on Active when
  no offline seats remain. Mafia blocks **all** gameplay while any seat is
  disconnected (`submitHostAction` / `applyRemoteCommand`).

Peers: decode snapshot JSON, reject if public ≠ `toPublic(public)`, reject
wrong roster/case, install into shadow. Submit encodes action; never applies
locally.

Runtimes (`WhodunitHostRuntime` / `MafiaHostRuntime`) are retained by the
process owner. Host start: announce barrier then (Whodunit) `AssignRoles(seed)`
or (Mafia) `driveMafiaHostProgression` on `canonicalState` while Active.

## CancellationException

Inspected `catch`es in owner, coordinators, handshake, outbox, LocalRoom
defaults, credential store, and transport all `throw cancelled` (or
`rethrowIfCancellation()`). Fatal `Error` is not converted to `NetError`.
