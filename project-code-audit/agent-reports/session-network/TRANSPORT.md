# Transport reconstruction (from code)

## Binding

`P2pBootstrap.p2pBootstrapModules()` = `[p2pTransportModule]`. Always in
`allModules`. Platform factories:

- Android: `lan(Context)`, `P2pKitAndroid.initialize` once on IO.
- iOS: `lan()`.
- Desktop: `lan()` + in-memory `DevelopmentIdentityStore` (not a store target).

All: `SecurityMode.AuthenticatedV2(AcceptAnyAuthenticatedSameApp)`,
`BackgroundPolicy.CloseActiveSessions`, `ReconnectPolicy.Enabled(10, 3s)`.

AppId `"com.parlor.app"`. Advertised name `"parlor-room|" + parlor-<tag>`.

`capability.supportsDiscovery = false` even though join browses internally.
No raw IP / fingerprint join API.

## Host create

1. Validate display name.
2. CSPRNG room code.
3. `kit.start()`, require `localFingerprint`.
4. Construct `HostP2pRoom` **before** `startAdvertising` so
   `incomingSessions` is collected (replay-zero race).
5. Failure → `kit.stopAfterFailure` or `room.leave()`.
6. Register with `AppLifecycleRoomCoordinator`.

## Join (room code)

`JoinConfig.rejoinToken != null` → `Unauthorized`.

Normalize code/name. Start kit + discovery. `DiscoveryCandidateScheduler`
(30s total, 5s/dial, 16 attempts/candidate, 64 tracked, 16 new/update).

Candidate must be `isFreshParlorHost`: name prefix `parlor-room|`; if
`lastSeen` is null, **accept** (Android adapter); else age ≤ 5s.

Per candidate: `kit.connect` → `awaitAdmission`:

1. `AdmissionRequest` (local `ProtocolVersion()`, no rejoinToken) retried
   every 400ms until first response (5s).
2. `AdmissionPending` → wait host tap (60s).
3. `AdmissionOffered` → validate playerId, host name, fingerprint pin,
   generation == 1 → `credentialStore.stage` → `AdmissionConfirmed`.
4. `AdmissionCommitted` matching offer → `commit` store.
5. `AdmissionAccepted` (v3) → `IncompatibleProtocol`.
6. `WrongCode` / `IncompatibleProtocol` stay in scheduler; other rejections
   fail join immediately.

Then `PeerP2pRoom.finishInitialAdmissionHandoff` sends `AdmissionReady` then
best-effort `AdmissionCommitAck`. Ready failure abandons the room.

## Host admission

Inbound: traffic guard → decode → **overwrite `actor`** → handshake or
game queue.

First application frame must arrive in 5s or the session is closed
(rate-limit diagnostic).

`AdmissionRequest` checks: protocol exact, room code, no rejoinToken,
displayName matches transport peer name, distinct names, not closed,
pending cap (`maxRemotePlayers + 4` ≤ 17). Then `AdmissionPending` +
`PeerEvent.AdmissionRequested`. Host UI calls `approveAdmission`.

`admit()`: reserve seat, offer credential (gen 1, 24h TTL, host
fingerprint + secret), wait `AdmissionConfirmed` (60s), commit maps,
wait `AdmissionReady` (60s) **before** `PeerJoined`. Lost commit frame
after confirm is **not** rolled back (resume recovers). Ready timeout
closes the socket (credential remains; peer can resume).

`closeAdmissions()` fails `CommandInFlight` if any reservation / ready
barrier is live. Drops disconnected lobby members and wipes their
credentials so they cannot rejoin a game they were never frozen into.

## Resume / credentials

Store: one device record, `active` + `pending`, schema v1, 8 KiB max,
key `p2p-resumable-session-v1`. Resume candidate prefers pending only
when it is a **different** membership (avoid ghosting a just-committed
new seat). Same-membership pending is not preferred (try last commit).

`resumeLastSession`: load, expire → invalidate + `RejoinExpired`. Discover
pinned `hostPeerId` + `kit.connect(peer, expectedFingerprint)` within
`REJOIN_GRACE_MS` (120s). Identity triple checked after connect.

`awaitResume`: `ResumeRequested(secret, generation)` → `ResumeOffered`
(gen+1) → stage → `ResumeConfirmed` → `ResumeCommitted` → store commit.
Host accepts current **or previous** digest (rotation window) via
constant-time compare. `AlreadyConnected` if live session not
lifecycle-retired.

`ResumeReady` is the inbox-attached barrier; host emits
`PeerReconnected` only after it. Lost `ResumeCommitAck` keeps previous
generation valid until next rotation.

`invalidateOwned` = exact generation. `invalidateMembershipOwned` =
all rotations of that membership (terminal / finalLeave / expiry).

## Disconnect / host death / leave

| Event | Host | Peer |
|---|---|---|
| TCP Reconnecting | member `connected=false` | `HostLost`, info Lost |
| TCP Connected after reconnect | `PeerReconnected` if still owner | `HostRestored` if same session |
| TCP Closed/Failed | `PeerLeft`, 120s rejoin deadline | `HostLost` + start `Resuming(now+120s)` + credential resume |
| `LeaveNotice` | remove member **and** wipe credential; no rejoin | — |
| `finalLeave` | terminate coordinator + `leave()` | `leave(notice)` then membership invalidate |
| `closeForRetry` | n/a | close **without** notice, keep credential |
| App background | Suspended, stop advertising, close all sessions, 120s expiry | same; kit CloseActiveSessions + explicit close |
| App foreground | Resuming + advertise; Active when **all** members connected | resume loop until deadline |
| Expiry | `Expired` + `leave()` | `Expired` + membership invalidate |

No host migration. Host process death: peers see HostLost, resume until
120s, then terminal. Host has no successor.

`retireDisconnectedMember` (gameplay only, after `closeAdmissions`):
atomic map wipe + session close. Idempotent for previously seen seats.

## Queues / traffic

| Queue | Cap |
|---|---|
| Host `incoming` Channel | 16 frames (suspends sender) |
| Peer `incoming` Channel | 8 frames |
| Host/peer `peerEvents` SharedFlow | extraBuffer 64, no replay |
| App lifecycle Channel | 8, DROP_OLDEST (close is **not** on this channel) |
| Admission limiter | 3/peer/10s + 32 global then 1/s; 128 identities / 5 min |
| Inbound frames | burst 32, 16/s; 3 violations / 10s → disconnect |
| Tracked sessions | min(maxRemote+4, 21) |

`incomingPeerMessages.send` / `incomingHostMessages.send` are
**suspending**. A stalled coordinator can stall the P2pKit collect
loop for that session (backpressure, not drop). See SN-001.

## InMemoryRoomBus vs production

| | InMemoryRoomBus | P2pKitRoomTransport |
|---|---|---|
| Actor stamp | `withAuthenticatedActor` on send | overwrite on host receive |
| Admission / resume / credentials | none | full transactional |
| Discovery / mDNS / freshness | none | scheduler + lastSeen |
| Lifecycle Suspended/Expired | default Active | 120s background/resume |
| `closeAdmissions` / retire / finalLeave | interface defaults | implemented |
| Incoming | Channel 64 host / 32 peer | 16 / 8 + traffic guard |
| peerEvents | extraBuffer 32, **must be emitted by test** | from connection state |
| Broadcast | all registered inboxes | connected sessions only; 0 delivered → NotConnected |
| Encryption / pin | none | P2pKit + fingerprint |
| LeaveNotice | not modeled | explicit vs TCP path |

Coordinator tests that use the bus prove protocol/authority, not LAN
races, Bonjour ghosts, or credential rotation.

## Physical-LAN tests left unproven (`@Ignore`)

`P2pKitRoomTransportLoopbackTest`:

- `peer_can_join_a_hosted_room_and_membership_appears_on_host`
- `peer_to_host_message_round_trips_and_host_to_peer_message_arrives_back`
- `host_broadcast_reaches_every_peer`

Live: host advertise + 6-char code only. Lifecycle tests use a fake kit,
not two NICs.
