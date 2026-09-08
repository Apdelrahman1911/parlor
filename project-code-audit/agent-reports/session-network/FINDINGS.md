# Findings — session / network (SN-001+)

Severity: P0 ship-blocker · P1 likely user-visible / security · P2 latent / untested gap · P3 hygiene.

---

## SN-001 — Host application inbox is a capacity-8 mailbox; burst can stall or drop work

**Severity:** P1  
**Symbols:** `HostAuthoritativeSessionCoordinator.mailbox` (`HOST_MAILBOX_CAPACITY = 8`);
`HostP2pRoom` inbound `incomingPeerMessages.send`; `jobs` collector `mailbox.send(Work.Incoming)`.

Host inbound from P2pKit is a Channel(16) that **suspends** the per-session
collector when full. The coordinator collector then `mailbox.send`s into an
**unbounded-wait capacity-8** mailbox. Heartbeats, start retries, and peer
commands share that mailbox.

Effects:

- A malicious or bursty admitted peer can delay start-deadline / abort /
  mutation work (absolute timers still fire, but they must also `mailbox.send`).
- `startSession` cancel path uses `mailbox.trySend(AbortStart)` and comments
  that a full mailbox must not commit; worker also checks
  `completion.isCancelled` before commit. That specific race is handled.
- `RetryStart` / `Heartbeat` / `Incoming` use suspending `send`. Under
  sustained fill, start retries and heartbeats stall. Game traffic is
  human-paced, but the bound is small relative to 17 peers × command+heartbeat.

Not a classic unbounded queue; it is a **small rendezvous that couples
liveness of all host work to the slowest/noisiest peer**.

**Evidence:** `AuthoritativeSessionCoordinator.kt:173,212-216,1268`;
`P2pKitRoomTransport.kt:1677-1680,2041`.

---

## SN-002 — `peerEvents` has no replay; production hosts ignore it and use `members`

**Severity:** P2 (mitigated in production bridges)  
**Symbols:** `HostP2pRoom._peerEvents` (`replay = 0`, extraBuffer 64);
`WhodunitHostRoomBridge` / `MafiaHostRoomBridge` `reconcileRoomTopology`.

Production runtimes set `reconcileRoomTopology = true` and collect
`room.members` (StateFlow, current value). That closes the
freeze-roster → subscribe race.

`PeerEvent` itself is still an edge stream. Any future caller that
subscribes only to `peerEvents` can miss `PeerJoined` /
`PeerReconnected` / `AdmissionRequested`. `tryEmit` on LeaveNotice /
HostLost can also drop when the 64-slot buffer is full (no
`onBufferOverflow` configured → `tryEmit` returns false).

`InMemoryRoomBus` same shape (replay 0). Tests emit after collectors
attach.

**Evidence:** `P2pKitRoomTransport.kt:1664,3364,4374`;
`WhodunitHostRoomBridge.kt:103-111,305-318`;
`MafiaHostRoomBridge.kt:100-105,263-276`.

---

## SN-003 — Physical LAN join, actor-stamp on the wire, and broadcast are `@Ignore`

**Severity:** P1 (coverage)  
**Symbols:** `P2pKitRoomTransportLoopbackTest` three `@Ignore` tests.

The only real-P2pKit test that runs is “host advertises a 6-char code”.
Join, authenticated actor overwrite on a real session, and broadcast to
N peers are documented as needing two/three physical devices. Fake-kit
lifecycle tests do not exercise mDNS, `lastSeen == null` Android path,
fingerprint pin, or multi-peer TCP.

This is the largest untested production surface in the workstream.

**Evidence:** `P2pKitRoomTransportLoopbackTest.kt:126-128,158-160,208-210`.

---

## SN-004 — Peer inbound Channel(8) of 272 KiB snapshots can stall host sends

**Severity:** P2  
**Symbols:** `PeerP2pRoom.incomingHostMessages` capacity
`P2pTrafficLimits.PEER_APPLICATION_QUEUE_CAPACITY = 8`;
`launchIncomingCollector` → `incomingHostMessages.send`.

If the peer coordinator is busy installing a snapshot (JSON decode +
validator), the collector suspends. Host `session.send` then waits on
P2pKit backpressure. `BoundedPeerOutbox` send timeout is 2s → host
treats that peer as `Timeout` and continues. Snapshots are conflated,
so a later revision still ships.

Not data loss of authority (host state remains). Can extend
start-commit / command-result latency on a slow peer device.

**Evidence:** `P2pKitRoomTransport.kt:3605-3608,4164`;
`BoundedPeerOutbox.kt:235-246`.

---

## SN-005 — `connectionEpoch` never rotates; replay protection is message-id only

**Severity:** P2  
**Symbols:** `SessionEnvelopeHeader.connectionEpoch` default `1L`;
`PeerAuthoritativeSessionCoordinator.rememberHostMessage` (2048 ids).

Reconnect uses a new physical session but the same epoch. A delayed
frame from a **replaced** socket that still delivers into a new
collector would be accepted if its `messageId` is unseen and revision
is newer.

Host replaces collectors on `replaceSession` / session close, so a
dead session’s collect job is cancelled. Residual risk is a
use-after-replace race if P2pKit delivered into the old collector
after `activeSession` flipped. `replaceSession` cancels the old
collector **after** swapping `activeSession`; the old collector can
still `send` one frame into the **shared** `incomingHostMessages`
channel.

That channel is process-local to one `PeerP2pRoom`, so the new
coordinator may install a snapshot encoded for the previous generation
if the host also sent one on the dying socket. Snapshots are
monotonic by revision, so an older revision is ignored; a **same or
newer** revision from a half-closed session is unlikely but not
epoch-fenced.

**Evidence:** `Protocol.kt:63`; `P2pKitRoomTransport.kt:3842-3871,4085-4164`;
`AuthoritativeSessionCoordinator.kt:1800-1806`.

---

## SN-006 — Same-app LAN peer can consume host admission budget before room-code check

**Severity:** P2  
**Symbols:** `AdmissionAttemptLimiter.tryAcquire`;
`handleAdmissionRequest` acquires **before** protocol/code checks.

Any authenticated same-`AppId` device can open TCP and send a first
frame. The limiter (3/peer + 32 global) runs before `WrongCode`. A
LAN neighbor running Parlor can burn global tokens with garbage
codes, delaying a legitimate joiner until refill (1/s after burst).

Not impersonation (code still required). Room-full / started still
apply after the limiter.

**Evidence:** `P2pKitRoomTransport.kt:2163-2177`; `P2pTrafficPolicy.kt:130-159`.

---

## SN-007 — Host mailbox `send` from the incoming collector has no timeout

**Severity:** P1  
**Symbols:** `HostAuthoritativeSessionCoordinator` init
`room.incoming.collect { mailbox.send(Work.Incoming) }`.

If the mailbox worker is inside a long `applyCommand` / `snapshotFor`
(JSON encode of a large Whodunit case), the collector suspends on
`mailbox.send`. Combined with SN-001, the P2pKit host collect loop
for **every** peer can stall (each session collect independently
blocks on `incomingPeerMessages.send` once the 16-slot channel fills).

Start-deadline jobs also `mailbox.send`. If the worker is stuck in
user `applyCommand` that ignores cancellation, the 20s start deadline
cannot be processed until apply returns.

Bridges’ `applyRemoteCommand` is reducer-bound and should be fast;
there is no host-side timeout around `applyCommand`.

**Evidence:** `AuthoritativeSessionCoordinator.kt:212-216,1098-1164`.

---

## SN-008 — Mafia host auto-progression can submit while seats are reconnecting only via disconnected set

**Severity:** P3  
**Symbols:** `driveMafiaHostProgression` / `nextHostAdvance`;
`MafiaHostRoomBridge.submitHostAction`.

Progression skips when `disconnectedPlayers` is non-empty. Transport
`offlinePlayers` includes eliminated audience; those are marked
disconnected too. Lifecycle `Suspended` is **not** observed by Mafia
progression except that `submitHostAction` fails `IllegalForPhase` when
disconnected is non-empty, and `applyHostMutation` returns `Suspended`
when room lifecycle ≠ Active — `PublishingMafiaSessionController`
maps that to `SessionClosed`/`SessionSuspended` and the collectLatest
cancels on leaving Active. Re-entry replays current state. Appears
correct; residual: no equivalent of Whodunit’s explicit Pause.

**Evidence:** `MafiaHostProgression.kt:25-40`; `MafiaHostRoomBridge.kt:125-147`;
`MafiaRetainedMultiplayerRuntime.kt:86-93`.

---

## SN-009 — `InMemoryRoomBus` cannot prove resume, LeaveNotice, or `closeAdmissions` atomicity

**Severity:** P2 (test gap)  
**Symbols:** `InMemoryPeerRoom.leave = Unit`; no `closeAdmissions` /
`retireDisconnectedMember` / credential overrides.

Game multiplayer desktop tests use the bus. They prove actor stamp,
command authority, and snapshot privacy. They do **not** prove:

- resume generation rotation vs `continueWithout`
- LeaveNotice vs TCP teardown
- `closeAdmissions` CommandInFlight during ready barrier
- host background closing sockets before peers resume

Those live in `P2pKitRoomTransportLifecycleTest` (fake kit), not in
game-bridge tests.

**Evidence:** `InMemoryRoomBus.kt:84-117`; game-mode `*MultiDevice*` tests.

---

## SN-010 — Broadcast reports success if ≥1 peer received the frame

**Severity:** P2  
**Symbols:** `HostP2pRoom.broadcast`.

If one of N connected peers throws, the whole broadcast is
`TransportFailure`. If some are not `Connected`, they are skipped; if
at least one send succeeds and none throw, result is Success. A
partially connected roster can therefore ack “sent” while another
seat got nothing. Snapshots go **Direct** via outboxes (good). Start
frames also go Direct. Broadcast is unused by coordinators for
gameplay; still a contract footgun if a future caller uses
`SendTarget.Broadcast` for a start/snapshot.

**Evidence:** `P2pKitRoomTransport.kt:3410-3435`;
`AuthoritativeSessionCoordinator.kt:1023-1061,1172-1185`.

---

## SN-011 — Peer `rejoinToken` property is always null

**Severity:** P3  
**Symbols:** `PeerP2pRoom.rejoinToken` getter returns `null`.

Deprecated `AdmissionAccepted.rejoinToken` is rejected. The
`LocalRoom.rejoinToken` API is a lie on the production peer room; the
secret lives only in `ResumableCredentialStore`. Callers that read
`room.rejoinToken` get nothing. No shipping caller in this scope
uses it. Dead API surface.

**Evidence:** `P2pKitRoomTransport.kt:3612-3613`; `LocalRoom.kt:56-57`.

---

## Positive controls (not findings)

- Exact 4.2 compatibility; unknown CBOR keys rejected.
- Actor overwrite on every typed peer message with `actor`.
- Peers never reduce; no optimistic apply.
- Non-idempotent commands are never auto-retried.
- CancellationException preserved on inspected paths.
- Credential stage/commit/rollback; membership-scoped revoke on
  terminal leave.
- `closeAdmissions` refuses in-flight credential handoff.
- Host seed / role assignment never on the wire (`sessionNonce` ≠ seed).
- Display names are labels; `PlayerId` is transport identity.
