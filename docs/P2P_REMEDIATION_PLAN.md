# Parlor + P2pKit 0.7.0-rc2 production remediation plan

Document status: approved historical implementation blueprint.

The findings/verdicts below describe the pre-remediation baseline and are not
current operational truth. Implementation began after owner approval and is
recorded in Git as separate remediation checkpoints. Current behavior lives in
`PRODUCTION_ARCHITECTURE.md`; current release evidence requirements live in
`RELEASE_GATES.md` and `P2P_MANUAL_TEST.md`. ADR-0002 resolved the plan's
manual/direct-connect question as unsupported for the first release.

Review basis: the current Parlor repository, the local P2pKit repository,
P2pKit tag v0.7.0-rc2, the Maven-resolved 0.7.0-rc2 artifacts, existing tests,
platform launchers, Gradle configuration, and current multiplayer
documentation.

## 1. Executive verdict

### 1.1 Current production-readiness verdict

Parlor multiplayer is not production-ready.

The P2pKit integration is PARTIALLY VERIFIED:

| Area | Verdict | Reason |
|---|---|---|
| Maven dependency resolution | PASS with provenance gate remaining | Parlor resolves io.github.apdelrahman1911:p2p-core:0.7.0-rc2 and p2p-transport-lan:0.7.0-rc2 from Maven Central rather than the local source tree. |
| Encrypted LAN transport | VERIFIED at library/API level | P2pKit uses authenticated Noise XX sessions over LAN transports. |
| Parlor actor authorization | PARTIALLY VERIFIED | The host is intended to be authoritative, but application command, admission, retry, sequence, and resume contracts are incomplete. |
| Android lifecycle runtime | FAIL | The app does not own a deterministic background/foreground session policy. |
| iOS lifecycle runtime | FAIL | scenePhase is not integrated with logical multiplayer state. |
| Persistent rejoin | FAIL | Peer capability is memory-only and explicit Leave destroys it. |
| Simultaneous command correctness | FAIL | Stale sequence and retry behavior can reject legitimate actions or risk duplicate execution. |
| Admission/capacity under races | FAIL | Capacity and membership commit are not one rollback-safe transaction. |
| Bounded application memory | FAIL | Parlor adds unbounded channels above P2pKit's bounded internal queues. |
| Discovery under multiple rooms/failures | FAIL | Candidates are effectively attempted once and WrongCode may terminate too early. |
| iOS Local Network UX | FAIL | The current gate can claim Granted without operational evidence. |
| Hotspot/cross-platform support | UNVERIFIED as a product promise | Individual manual success is useful evidence but is not the full signed physical matrix. |

P2pKit provides encrypted and authenticated LAN discovery/transport. It does
not provide room authorization, application acknowledgements, exactly-once
game commands, rejoin, host migration, Internet/NAT traversal, application
protocol negotiation, or game-state synchronization. Parlor must own those
guarantees explicitly.

### 1.2 Workspace and dependency facts

* Parlor was inspected on branch mafia/module-and-platform-updates.
* The Parlor worktree contained extensive pre-existing modified and untracked
  user work. Those changes must remain untouched unless the owner explicitly
  assigns overlapping files.
* The local P2pKit worktree was clean during the review.
* Parlor consumes Maven Central artifacts, not a composite build, included
  build, local Maven artifact, or the current local P2pKit source directory.
* Inspected P2pKit source: tag v0.7.0-rc2, commit beginning 90acb295.
* Maven Central provenance still needs resolved checksums, POM/module metadata,
  license inventory, SBOM, and a recorded match between the published artifact
  and the reviewed tag.

### 1.3 Release blockers

The release blockers are:

1. no explicit logical-session lifecycle;
2. no secure persistent resumable-session design;
3. unsafe simultaneous-command and send-failure semantics;
4. unbounded application buffering and missing abuse controls;
5. incomplete discovery candidate scheduling;
6. possible P2pKit identity/file work on Android's main thread;
7. CancellationException conversion risks;
8. non-atomic room-capacity enforcement;
9. non-transactional admission rollback;
10. unresolved manual/direct-connect product scope;
11. untruthful iOS Local Network permission state;
12. non-atomic domain state/revision/projection publication;
13. protocol-version claims inconsistent with strict decoding;
14. incomplete semantic validation;
15. encoded payload-size accounting mismatch;
16. release provenance and documentation inconsistency.

No Blocker, Critical, or High item may remain at release without explicit,
documented owner risk acceptance.

## 2. Current implementation and evidence

### 2.1 Relevant application boundaries

The multiplayer path currently spans:

* composeApp for application startup, navigation, UI, Android and iOS platform
  wiring;
* shared/session for authoritative coordination and session behavior;
* shared/transport-p2p for the P2pKit room adapter, discovery, admission,
  message exchange, and cleanup;
* shared transport/protocol/domain modules used by the adapter;
* game-modes/whodunit for the shipping Whodunit engine, host bridge and UI;
* game-modes/mafia for Mafia multiplayer integration and game state;
* iosApp for the SwiftUI entry point and Apple lifecycle;
* documentation and verification scripts describing runtime behavior.

The effective call path is:

Player UI action
-> game/session controller
-> AuthoritativeSessionCoordinator or P2pKitRoomTransport
-> P2pKit session send
-> LAN TCP transport
-> remote P2pKit incoming flow
-> Parlor wire decode
-> authenticated session/coordinator
-> game bridge/reducer
-> immutable UI projection.

This path currently has authority, ordering, lifecycle, and state ownership
split across several layers. The target design consolidates logical session
ownership without coupling game modules to P2pKit.

### 2.2 Exact evidence from Parlor

| Evidence | Location | Consequence |
|---|---|---|
| Logging callback is a no-op | shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt:57 | Physical-test docs request logs that cannot exist. |
| Actual join timeout is 120 seconds | P2pKitRoomTransport.kt:351 and :358 | Documentation says 10 seconds; user expectations and test timing differ. |
| attemptedPeerIds permits one effective attempt | P2pKitRoomTransport.kt:190 | Transient failure or a late correct room can fail incorrectly. |
| Unbounded channel | P2pKitRoomTransport.kt:429 | A remote flood can grow application memory without a hard bound. |
| Second unbounded channel | P2pKitRoomTransport.kt:938 | A slow consumer can accumulate unbounded work. |
| Admission is committed before delivery is known | P2pKitRoomTransport.kt:726 | Disconnect/send failure can leave a ghost member or consume capacity. |
| Explicit Leave deletes token capability | P2pKitRoomTransport.kt:789 | Documentation claiming Leave then rejoin is false. |
| Peer token is memory-only | P2pKitRoomTransport.kt:941 | Process death cannot resume. |
| Coordinator mailbox is unbounded | shared/session/src/commonMain/kotlin/com/parlor/session/AuthoritativeSessionCoordinator.kt:69 | Flood/backpressure is not contained at the authoritative owner. |
| Sequence is consumed before stale validation | AuthoritativeSessionCoordinator.kt:179 | Legitimate concurrent commands can be rejected without a coherent retry contract. |
| Failed send can cause command re-execution | AuthoritativeSessionCoordinator.kt:354 | A non-idempotent action can execute twice. |
| Existing test expects retry behavior | shared/session/src/commonTest/kotlin/com/parlor/session/AuthoritativeSessionCoordinatorTest.kt:324 | The unsafe behavior is encoded as expected behavior and must be replaced with a root-cause regression test. |
| Whodunit host bridge publishes state through separate holders | game-modes/whodunit/.../WhodunitHostRoomBridge.kt:139 | UI/network observers may see revision and state from different transitions. |
| Pass-and-play controller uses stateIn around mutable session state | game-modes/whodunit/.../PassAndPlaySessionController.kt:57 | The same domain engine has inconsistent ownership paths. |
| Mafia uses the analogous host bridge pattern | game-modes/mafia host bridge | The atomicity issue affects every shipping multiplayer game, not one game. |
| Android application has no lifecycle bridge | composeApp/src/androidMain/.../ParlorApplication.kt:11 | P2pKit lifecycle and Parlor logical state diverge. |
| iOS entry has no scenePhase handling | iosApp/iosApp/iOSApp.swift:3 | Background/foreground behavior is implicit. |
| iOS permission gate returns Granted without evidence | composeApp/src/iosMain/.../P2pPermissionGate.ios.kt:9 | The UI can misdiagnose denial or unrelated transport failure. |

Line numbers are evidence anchors from the reviewed tree. They must be
reconfirmed immediately before implementation because the worktree is active.

### 2.3 P2pKit 0.7.0-rc2 behavior relied upon

The inspected rc2 source establishes these contracts:

* P2pKit creation performs identity/file work on the caller path:
  p2p-core/.../P2pKit.kt:253-266.
* P2pSession.send means a local transport write, not application acceptance.
* Incoming messages use replay 0 with bounded buffering; cancellation is
  documented to propagate: P2pSession.kt:19-79.
* Reconnect applies to outgoing failures only. A clean close does not retry:
  Config.kt:31-53.
* Default background policy closes active sessions. KeepRunning requires
  platform support: Config.kt:63-72.
* Foreground notification does not automatically restart discovery or
  advertising: P2pKitImpl.kt:947-969.
* P2pKit's receive backlog is bounded at 64 messages and 8 MiB:
  P2pSessionImpl.kt:688-715 and :1075-1076.
* LAN discovery uses JmDNS/Bonjour and TCP; sessions use authenticated Noise XX.

P2pKit does not promise Parlor-level command acknowledgement, room membership,
actor authorization, exactly-once application semantics, host migration,
Internet multiplayer, NAT traversal, rendezvous, relay, or authenticated user
accounts.

## 3. Findings inventory

| ID | Severity | Confidence | Category | Confirmed impact |
|---|---|---:|---|---|
| P2P-01 | Critical | High | Lifecycle | Background/foreground can silently terminate physical sessions without deterministic room state. |
| P2P-02 | Critical | High | Rejoin/security | Process death cannot rejoin; Leave documentation is false; a naive token patch would be replayable. |
| P2P-03 | Critical | High | Command correctness | Simultaneous/stale commands and send failure can lose intent or execute an action twice. |
| P2P-04 | Critical | High | Availability/security | Unbounded application queues permit memory exhaustion and peer starvation. |
| P2P-05 | High | High | Discovery | Multiple rooms, late candidates, and transient failures can produce wrong final results. |
| P2P-06 | High | High | Android performance | Identity/file work can occur on the main thread and cause startup jank/StrictMode violations. |
| P2P-07 | High | High | Coroutines | Cancellation may be converted into transport errors and leave child jobs/resources alive. |
| P2P-08 | Critical | High | Capacity | Concurrent approval can exceed room capacity. |
| P2P-09 | Critical | High | Admission | Approval failure/disconnect can leave ghost membership or credentials. |
| P2P-10 | Medium pending decision | High | Product architecture | Manual/direct connect is neither correctly supported nor explicitly unsupported. |
| P2P-11 | High | High | iOS UX | Permission state can be falsely reported and recovery is unreliable. |
| P2P-12 | High | Medium-high | State atomicity | Domain state, revision and projection are not published as one transaction. |
| P2P-13 | High | High | Compatibility | Strict JSON decoding conflicts with stated minor-version compatibility. |
| P2P-14 | Medium | Medium-high | Input validation | Several control/result/terminal messages lack complete semantic validation. |
| P2P-15 | High | High | Payload bounds | JSON ByteArray expansion can exceed effective wire limits before transport checks. |
| REL-01 | Medium | High | Supply chain | Published artifact provenance is not recorded strongly enough for release evidence. |

Recommendations such as a richer game plug-in API or better diagnostic export
are not themselves confirmed defects. They are included only where needed to
fix the confirmed ownership, testing, and supportability problems.

## 4. Final proposed architecture and state model

### ADR-001: one logical multiplayer-session owner

Add MultiplayerSessionOrchestrator at the application/session boundary.

Ownership:

* application scope owns exactly one orchestrator;
* each logical session owns a SupervisorJob and bounded child scopes;
* UI sends intents and observes immutable MultiplayerUiState;
* game modules provide a descriptor and pure authoritative engine;
* the P2pKit adapter owns physical discovery/session handles only;
* a physical connection may be replaced without replacing the logical room;
* terminal cleanup is idempotent and owned by the orchestrator.

Logical state:

| State | Allowed entry | Allowed exit | Invariant |
|---|---|---|---|
| Idle | app start or terminal cleanup | Hosting, Discovering | no room resources |
| Hosting | create-room intent | Lobby, Ending | advertising owned and capacity initialized |
| Discovering | join intent | Connecting, Ending | one candidate scheduler and deadline |
| Connecting | candidate selected | AwaitingAdmission, Discovering, Ending | one bounded connection attempt |
| AwaitingAdmission | secure transport established | Lobby, Discovering, Ending | no game authority before commit |
| Lobby | admission committed | Playing, Suspended, Ending | membership/revision coherent |
| Playing | host starts valid game | Suspended, Ending | host-authoritative engine is sole writer |
| Suspended | lifecycle/network interruption | Resuming, Ending | commands and game timers frozen |
| Resuming | foreground/network recovery | Lobby, Playing, Ending | credential transaction in progress |
| Ending | explicit/timeout/fatal terminal cause | Ended | no new work accepted |
| Ended | cleanup completed | Idle | all jobs/handles closed |

### ADR-002: host-authoritative transaction actor

One actor or mutex-protected owner performs:

1. bounded decode and semantic validation;
2. binding transport peer to host-assigned player identity;
3. connection-epoch and sequence validation;
4. command-id idempotency lookup;
5. phase/context/revision validation;
6. game reducer execution;
7. authoritative revision increment;
8. immutable full-state and per-player projection creation;
9. command-result ledger commit;
10. outbound result/snapshot scheduling.

No client-provided actor/player id is trusted. Game bridges do not mutate
authoritative state. State and revision are one immutable value.

### ADR-003: protocol v2 with honest compatibility

Use a compact, length-accounted binary/CBOR envelope or an equivalent codec
that can prove encoded byte size before allocation and send.

Envelope fields:

* protocol major/minor and required feature bits;
* game id and game protocol version;
* logical session id and physical connection epoch;
* message id, per-connection sender sequence and optional command id;
* message kind and declared payload length;
* bounded payload.

Rules:

* unsupported major or required feature returns UpdateRequired and closes;
* minor compatibility is claimed only when unknown optional fields are ignored
  and golden compatibility tests prove it;
* protocol v1 is either rejected explicitly or supported by a deliberate
  compatibility adapter approved by the owner;
* old-epoch frames never update current state;
* per-recipient snapshots are monotonically revised and conflated;
* terminal messages are idempotent.

Initial encoded bounds:

| Item | Limit |
|---|---:|
| Entire Parlor wire frame | 272 KiB |
| Combined authoritative snapshot | 256 KiB |
| Game command payload | 32 KiB |
| Control/admission/result payload | 8 KiB |

These values are initial engineering limits, not folklore. Phase 1 benchmarks
and stress tests may reduce them. Any increase requires a memory-budget ADR and
P2pKit transport-limit verification.

### ADR-004: resumable-session credential

The credential identifies a resumable logical membership, not merely a socket.

Credential fields:

* logical room/session id;
* game id and protocol version;
* host-assigned player id/slot;
* authenticated host PeerId/fingerprint;
* 256-bit random secret;
* token generation;
* issued-at, expires-at and last authoritative revision;
* negotiated feature set.

Storage:

* Android: Keystore-protected encrypted record in no-backup app storage;
* iOS: Keychain item with ThisDeviceOnly accessibility appropriate to the
  lifecycle policy;
* desktop/development: memory-only by default unless an approved secure store
  is implemented;
* host stores only a token hash and membership metadata.

Semantics:

* created only after transactional admission reaches Confirmed;
* survives transient disconnect, background, network switch, and peer process
  death during the 120-second grace period;
* rotates only after resume Confirm is received and commit succeeds;
* explicit final Leave invalidates host record and deletes the client record;
* room end, game end policy, expiry, host final exit, or security violation
  permanently invalidates it;
* wrong host fingerprint, wrong player identity, old generation, replay,
  expired room, ended game, or already-active duplicate is rejected;
* simultaneous resume attempts use one winner; the other is rejected and
  cannot evict a confirmed current connection without policy authorization.

### ADR-005: bounded memory and per-peer isolation

Initial limits:

| Resource | Limit and behavior |
|---|---|
| P2pKit receive configuration | 8 messages / 512 KiB per active session where configurable |
| Host active sessions | maxRemotePlayers + 3, absolute cap 18 |
| Pre-handshake connections | 4 |
| Authoritative command queue | 8 commands / 256 KiB per peer |
| Control queue | 16 messages / 128 KiB per peer |
| Snapshot queue | one conflated latest snapshot per recipient |
| Command rate | 4/s, burst 8 per authenticated peer |
| All application messages | 8/s, burst 16 per peer |
| Admission attempts | 3 per 10 seconds per fingerprint |
| Global admission | 2 times maxRemotePlayers per minute, burst maxRemotePlayers |
| Cooldown | 30 seconds after threshold violation |
| Rate/admission cache | bounded LRU, maximum 256 entries |
| Application payload budget | below 20 MiB for a full supported room |

Oversize, sustained flood, repeated malformed messages, or queue overflow
produces a classified protocol violation and closes the offending peer. Other
peers and host UI remain responsive.

### ADR-006: explicit lifecycle policy

| Event | Host behavior | Peer behavior | Player-visible result |
|---|---|---|---|
| Background or screen lock | Enter Suspended, freeze timers, notify P2pKit | Enter Suspended, freeze local intents, notify P2pKit | Reconnecting/suspended banner |
| Foreground within 120 s | Restart advertising and accept pinned resume | Restart discovery/connection to pinned host and resume | Resume only after required membership is restored |
| Foreground after 120 s | End room | Credential expires; return to terminal result | Session expired message |
| Short network switch | Same as short suspension | Same as short suspension | Reconnecting with deadline |
| Peer process death | Reserve its seat until grace expires | Relaunch offers encrypted credential | Resume if host and game still valid |
| Host process death/OS termination | Room is terminal; no migration in release one | Detect timeout and end | Host left; room ended |
| Explicit peer Leave | Remove member and invalidate credential | Delete credential | Cannot rejoin using old capability |
| Explicit host exit | Send terminal best effort, invalidate all, close | End immediately or on timeout | Room ended |

Android and iOS implement the same product semantics where platform capability
permits. Platform limitations are surfaced honestly rather than hidden behind
different behavior.

### ADR-007: command result and stale-revision contract

Client behavior:

* one mutating command in flight per local player;
* sending is not success;
* UI displays Pending until an authoritative result arrives;
* timeout exposes Retry status query or Reconnect, not silent action replay;
* duplicate commandId asks for the existing outcome.

Host behavior:

* key idempotency ledger by authenticated actor and commandId;
* exact duplicate returns the recorded result and never reruns the reducer;
* sequence numbers protect a physical connection but do not replace command
  idempotency;
* accepted result includes the resulting revision;
* rejected result includes classification and current revision;
* authoritative revision increments once for an accepted state transition.

Stale policy:

| Class | Examples | Rule |
|---|---|---|
| RevalidateWithinContext | readiness, lobby selection, vote/night selection if phase and prerequisites remain valid | Host revalidates against current state; may accept once |
| ExactRevision | phase transition, start/end round, rematch commit | Reject stale and require a new user-visible intent |
| NeverAutomaticRetry | random draw, irreversible reveal, terminal action, any action with external side effects | Never replay silently |
| Idempotent query/control | status query, heartbeat, outcome query | Safe bounded retry |

### ADR-008: discovery candidate scheduler

The join attempt owns:

* total discovery/connect deadline: 30 seconds;
* per dial plus secure handshake: 5 seconds;
* first admission-state response: 5 seconds;
* manual host approval: up to 60 seconds after a valid request;
* candidate backoff: 0.5, 1, 2, 4, then 5 seconds cap;
* maximum four connection attempts per candidate inside the total deadline;
* deduplication by stable advertised candidate identity;
* removal/update when advertisements disappear or endpoints change;
* cancellation on user abort or lifecycle terminal event.

WrongCode is candidate-local. It is not the final error while another candidate
can appear. Final error precedence should prefer incompatible version,
permission/network policy, room full, all matching rooms rejected, then
deadline/no room, with a useful redacted diagnostic summary.

### ADR-009: direct/manual-connect capability

Recommendation: manual/direct connect is unsupported in the first production
release unless the owner confirms it as a product requirement.

If required, implement a transport-independent provisioning capability based
on an explicit P2pKit host endpoint and expected authenticated fingerprint.
Manual and discovery paths converge before secure handshake and use the same
version negotiation, identity, admission, capacity, abuse, and rejoin rules.
Do not scan random IPs, weaken authentication, or bypass room admission.

### ADR-010: platform permission truthfulness

Android declares only permissions actually needed by the selected LAN transport
and target SDK. Obsolete Nearby guidance is removed.

iOS state is:

* Unknown;
* Requesting;
* GrantedOperational, only after discovery/advertising is operational;
* DeniedActionable, only when observable platform evidence supports it;
* DeniedUnclassified;
* TransportFailure.

Use Local Network and Bonjour wording, NSLocalNetworkUsageDescription, Bonjour
service declarations, foreground retry, and UIApplication.openSettingsURLString.
Do not invent a preflight API that iOS does not expose.

### ADR-011: safe diagnostics

Add typed, allowlisted events such as:

* session.create.started/succeeded/failed;
* discovery.started/candidate/attempt/finished;
* connection.started/secure/closed;
* admission.requested/reserved/committed/rolled_back/rejected;
* protocol.rejected;
* command.received/accepted/rejected/duplicate/outcome_queried;
* snapshot.sent/applied/ignored_stale;
* lifecycle.suspended/resume_started/resumed/expired;
* cleanup.started/completed/timeout.

Allowed fields: monotonic timestamp, build id, role, state, game id, protocol
version, redacted peer ordinal, result classification, queue depth bucket,
encoded-size bucket, and duration.

Never log room codes/secrets, rejoin tokens or hashes, encryption/session keys,
raw payloads, private game state, stable player/device identities, IP addresses,
or unnecessary peer identifiers. Use a bounded in-memory ring buffer and a
redacted export for physical-device evidence.

### ADR-012: extensible game boundary

Each game provides a GameDescriptor with:

* stable game id and game protocol version;
* player limits and supported roles;
* pure authoritative engine/reducer;
* state, command, event and projection codecs;
* semantic validators and size estimates;
* UI route/factory and assets;
* rule/state-machine/property tests.

The app shell owns catalog and navigation. The session layer owns lobby,
membership, authority, protocol and lifecycle. The P2pKit adapter implements a
transport interface. Game modules never import P2pKit.

A non-shipping test fixture must register a minimal second game and exercise a
host/peer flow without modifying networking core or adding a central growing
when statement.

## 5. Detailed issue plans

Each issue below contains the required root cause, exact target behavior,
architecture, affected areas, compatibility, failure cases, tests, regression
risk, measurable acceptance criteria, and evidence.

### P2P-01 — Android/iOS lifecycle

1. Root cause

Android ParlorApplication and the iOS SwiftUI entry do not forward lifecycle
events into a logical multiplayer owner. P2pKit rc2 defaults to closing active
sessions on background and does not restart advertising/discovery on foreground.
Room state, game timers, UI and transport therefore evolve independently.

2. Correct target behavior

Use the exact ADR-006 policy. Background/lock suspends and freezes. Foreground
within 120 seconds performs pinned resume. Longer interruption expires. Peer
process death may resume from secure storage. Host process death ends the room.
Explicit Leave is terminal. No command runs while suspended.

3. Architecture/design change

Platform lifecycle adapters send typed events to MultiplayerSessionOrchestrator.
The orchestrator performs one state transition, owns the deadline, calls the
transport lifecycle adapter, restarts discovery/advertising explicitly, and
unfreezes the game only after membership restoration.

4. Exact code areas affected

* composeApp/src/androidMain/.../ParlorApplication.kt;
* Android activity/process lifecycle wiring;
* iosApp/iosApp/iOSApp.swift and scenePhase;
* common lifecycle adapter and MultiplayerSessionOrchestrator;
* P2pKitRoomTransport lifecycle/start/stop paths;
* host game timers and Whodunit/Mafia UI state;
* callers that currently create/close transport directly.

5. Compatibility impact

No P2pKit public API break is required. Parlor logical state and UI states
change. Protocol v2 needs suspension/resume messages and connection epochs.
Persistent resume metadata changes. Android and iOS behavior becomes aligned;
desktop remains explicitly foreground-only unless lifecycle support is added.

6. Failure and edge cases

Background during admission/send/terminal cleanup; rapid foreground oscillation;
screen lock; network switch; host and peer background together; foreground
after expiry; OS termination; process recreation with stale UI; resume while
room ended; duplicate lifecycle callbacks.

7. Tests required

Pure virtual-time transition tests, timer freeze/resume tests, cancellation at
every state, Android instrumentation and StrictMode, Apple-native scenePhase,
short/long interruption, lock/unlock, network switching, process kill/relaunch,
and repeated session cleanup. Do not use arbitrary sleeps.

8. Regression risks and proof

Could break timers, navigation, foreground single-player play, or cleanup.
Prove single-player tests unchanged, timers advance exactly once, terminal
cleanup is idempotent, and coroutine/session leak counters return to zero.

9. Acceptance criteria

Every lifecycle event maps to one documented state; no command/timer advances
while suspended; resume succeeds or terminates by policy within its deadline;
all handles/jobs close after terminal state on Android and iOS tests.

10. Verification evidence

Automated reports, lifecycle transition traces, Android/iOS signed-device logs,
screen recordings for physical cases, and resource/job cleanup counters.

### P2P-02 — persistent resumable rejoin

1. Root cause

Peer rejoin material is memory-only and explicit Leave destroys it. There is no
durable capability schema, secure storage, expiry, rotation, host hash binding,
replay policy, process-death restoration, or atomic resume transaction.

2. Correct target behavior

Transient disconnect/background/network switch/process death can resume within
120 seconds when room/game/identity remain valid. Explicit final Leave, host
exit, room end, expiry, security violation, or game policy invalidates the
credential permanently. A resumed member keeps its host-assigned player slot.

3. Architecture/design change

Implement ADR-004 plus ResumeRequested -> ResumeOffered -> ResumeConfirmed ->
ResumeCommitted. Store only encrypted client material and host hash. Bind to
authenticated host/peer identity and token generation. Rotate after confirmation
and commit, not before.

4. Exact code areas affected

P2pKitRoomTransport token/join/leave paths, common session models, protocol
envelopes, orchestrator, host membership repository, Android Keystore adapter,
iOS Keychain adapter, desktop policy, UI resume states, room/game end cleanup.

5. Compatibility impact

Protocol and persistent storage version increase. v1 peers cannot participate
unless an explicit adapter is retained. No weakening of P2pKit encryption.
Public P2pKit API should remain unchanged. Android/iOS add platform secure-store
implementations.

6. Failure and edge cases

Process death during offer/confirm; token stolen/replayed; old generation;
wrong host fingerprint; room gone/full/ended; game ended; another connection
already active; host crash after rotation but before client storage; corrupted
secure storage; clock skew; app backup/restore to another device.

7. Tests required

Secure-store unit/adapter tests, no-backup/ThisDeviceOnly checks, process-death
simulation, replay/generation/fingerprint tests, concurrent resume, disconnect
at every transaction boundary, room/game end, explicit Leave, corrupt record,
expiry with virtual time, and physical relaunch tests.

8. Regression risks and proof

Could admit duplicate members, lock a valid user out after partial rotation, or
persist stale secrets. Prove single-winner resume, two-generation recovery
rules, rollback at each boundary, encrypted-at-rest inspection, and complete
terminal deletion.

9. Acceptance criteria

Valid peer resumes within grace without duplicate membership; old/replayed/
wrong-host credentials always fail; explicit Leave never resumes; no plaintext
secret appears in files, logs, backups, or crash diagnostics.

10. Verification evidence

Golden storage migration tests, fault-injection transaction traces, Android
Keystore/iOS Keychain device evidence, and redacted rejoin lifecycle events.

### P2P-03 — simultaneous commands, revisions and acknowledgements

1. Root cause

Client sequence advances before stale-revision classification. Sending is
treated too close to success, and failure may trigger command re-execution.
There is no complete authoritative result, outcome query, duplicate ledger, or
per-command retry classification.

2. Correct target behavior

Every mutating command is pending until an accepted/rejected authoritative
result. Multiple legitimate players may act against the same revision; the
host serializes and revalidates each. An action executes at most once. Stale
rejection is explicit and visible.

3. Architecture/design change

Implement ADR-002 and ADR-007. One client mutation in flight, commandId ledger,
connection sequence, base revision, result/outcome query, and command-specific
stale policy. Never silently retry non-idempotent actions.

4. Exact code areas affected

AuthoritativeSessionCoordinator command mailbox/process/send paths and tests;
protocol command/result models; P2pKitRoomTransport send/result routing;
Whodunit and Mafia command adapters/reducers; UI pending/error feedback;
snapshot application logic.

5. Compatibility impact

Protocol v2 and UI state change. Game rules should not change except where the
current behavior is incorrect. v1 command semantics must be rejected or adapted
explicitly. P2pKit API is unchanged.

6. Failure and edge cases

Two same-revision commands; duplicate frame; ACK lost after execution; command
lost before execution; delayed result after reconnect; stale old epoch; same
commandId with different payload; cancellation during send; terminal transition
between validation and commit; malicious sequence jumps.

7. Tests required

Deterministic concurrent-player tests, duplicate/reorder/delay/drop tests, lost
ACK and outcome query, reconnect replay, commandId collision, sequence wrap/
jump policy, stale-class table for every shipping command, terminal behavior,
and serialization compatibility. Replace the current test that expects unsafe
re-execution.

8. Regression risks and proof

Could make UI feel stuck, change valid simultaneous game actions, or suppress
legitimate retry. Prove all existing rule tests, latency bounds, one-result-per-
intent UI tests, and full Whodunit/Mafia playthroughs under injected faults.

9. Acceptance criteria

No reducer executes a command twice; every accepted mutation advances exactly
one revision; duplicates return identical recorded outcome; stale behavior for
every command is enumerated and tested; UI reports pending/accepted/rejected.

10. Verification evidence

Ledger assertions, transition histories, two-peer fault-harness reports,
shipping-game state-machine tests, and signed-device simultaneous-action runs.

### P2P-04 — bounded queues, backpressure and abuse

1. Root cause

Parlor uses Channel.UNLIMITED in transport/coordinator paths and lacks encoded
byte accounting, rate limits, pending-admission limits, per-peer isolation, and
malicious-client thresholds. P2pKit's internal bound does not bound the queues
Parlor adds above it.

2. Correct target behavior

Memory is bounded by documented message and byte budgets. A malicious or
modified peer cannot grow host memory effectively without bound or starve
other peers. Overflow is classified, visible in redacted diagnostics, and
isolated to the offender.

3. Architecture/design change

Implement ADR-005: bounded byte-counted queues, separated control/command/
snapshot lanes, token-bucket rates, admission budgets, LRU cooldown state,
pre-allocation frame validation, and per-peer cancellation.

4. Exact code areas affected

P2pKitRoomTransport channels/collectors/session maps, coordinator mailbox,
protocol decoder, admission registry, diagnostics, P2pKit session configuration,
game command size validators and stress harness.

5. Compatibility impact

Oversize or abusive clients now receive a protocol violation and disconnect.
Protocol v2 publishes limits/features. Normal public game APIs remain stable.
P2pKit changes only if configuration/error visibility is missing.

6. Failure and edge cases

Many tiny frames; few maximal frames; compressed/encoded expansion; slow UI;
slow recipient; reconnect flood; rotating identities; admission spam; control
starvation; snapshot storm; close while queues full.

7. Tests required

Ten-minute sustained flood, burst boundaries, byte versus count saturation,
malformed lengths, fuzz/property decoder tests, many-peer admission load,
snapshot conflation, control priority, unaffected-peer fairness, heap sampling,
and repeated session teardown.

8. Regression risks and proof

Limits could disconnect legitimate slow devices or lose essential terminal
control. Prove worst-case legal game payload sizes, low-end device tests,
control-lane priority, snapshot convergence, and normal three-device play.

9. Acceptance criteria

All queues remain within count/byte limits; app payload memory stays below 20
MiB at supported maximum; offender closes within threshold; unaffected peers
continue and converge; no OOM/ANR in sustained test.

10. Verification evidence

Queue telemetry, heap profiles, stress-test reports, redacted violation events,
and low-end signed-device results.

### P2P-05 — discovery candidate scheduler

1. Root cause

attemptedPeerIds prevents robust retry. Discovery lacks per-candidate state,
backoff, total/per-attempt deadlines, disappearing-candidate updates and final
error precedence. WrongCode can win before a valid room appears.

2. Correct target behavior

Late correct rooms, transient failures and multiple rooms are handled within a
bounded 30-second search. Wrong-room candidates are skipped. User cancellation
is immediate. The final error explains the most actionable reason.

3. Architecture/design change

Implement ADR-008 as a pure candidate scheduler driven by events and a monotonic
clock. Transport dialing is an injected operation. Candidate state includes
endpoint generation, last result, next eligible time and attempt count.

4. Exact code areas affected

P2pKitRoomTransport join/discovery loop and attemptedPeerIds; join UI/error
models; discovery adapter; time/deadline abstraction; test fake discovery;
documentation timeout procedures.

5. Compatibility impact

No wire change. User-visible join duration changes from the current effective
120 seconds to a 30-second discovery/connect deadline plus an explicit
60-second valid host-approval state. Error copy changes.

6. Failure and edge cases

Candidate appears late/disappears/reappears with endpoint change; wrong room
first; correct room temporarily unreachable; version mismatch; room becomes
full; network switch; duplicate advertisements; cancellation during dial;
permission failure; host approval timeout.

7. Tests required

Virtual-time scheduler tests for all above cases, no-sleep cancellation,
multiple-room integration, transient fault injection, deadline boundaries,
error precedence, resource cleanup, and physical LAN discovery.

8. Regression risks and proof

Retry may cause duplicate sessions, excessive battery use, or slower actionable
errors. Prove one live dial per candidate, bounded attempts, handle cleanup,
power/network counters in soak, and immediate terminal classification where
appropriate.

9. Acceptance criteria

Correct late candidate connects within deadline; wrong candidate never ends
search prematurely; no infinite retry; abort stops discovery/dials promptly;
all handles close; final error is deterministic.

10. Verification evidence

Virtual scheduler traces, fault-harness output, multi-room device logs and
timeout documentation matching constants.

### P2P-06 — initialization and identity I/O

1. Root cause

P2pKit.create performs identity/file work on its caller. Parlor can invoke kit
creation from Android application/UI startup without an explicit I/O dispatcher
or single initialization owner.

2. Correct target behavior

Android UI/main thread performs no disk/network identity work. One application
owner initializes exactly one kit instance; cancellation/failure cleans partial
resources and exposes a recoverable state.

3. Architecture/design change

Make createKit suspending and inject an initialization dispatcher. Android uses
Dispatchers.IO.limitedParallelism(1); Apple uses an appropriate background
dispatcher compatible with KMP/native assumptions. Application scope owns
initialization and shutdown.

4. Exact code areas affected

ParlorApplication.kt, dependency creation/composition root, P2pKitRoomTransport
factory/callers, platform dispatcher abstractions, initialization state UI, and
startup tests.

5. Compatibility impact

No protocol or persistent format change. Factory API may become suspending
inside Parlor; external shared callers require migration. P2pKit public API
remains stable.

6. Failure and edge cases

Concurrent host/join starts; corrupt identity file; permission/storage failure;
cancellation mid-create; process recreation; initialization after shutdown;
iOS threading constraints; slow storage.

7. Tests required

Dispatcher/thread assertions, concurrent single-flight initialization, cancel/
failure cleanup, corrupt identity, repeated create/destroy, Android StrictMode
startup and Apple-native initialization.

8. Regression risks and proof

Could delay first room UI, create two kit instances, or access native state on
the wrong thread. Prove single-flight semantics, loading/error UX, startup
timing and platform-native tests.

9. Acceptance criteria

Zero Android StrictMode disk/network violation on main; exactly one live kit;
all partial resources close on cancel/failure; UI remains responsive.

10. Verification evidence

StrictMode logs, startup trace, unit/native tests, job/resource counters and
signed Android smoke evidence.

### P2P-07 — structured cancellation

1. Root cause

Broad exception catches in discovery, connection, send, admission, game bridge
and cleanup paths can convert CancellationException into normal errors. Some
cleanup depends on arbitrary waits rather than child ownership.

2. Correct target behavior

Cancellation is control flow: it propagates to the owner, does not emit a normal
transport/game error, and terminates all owned children/resources within a
bounded cleanup deadline.

3. Architecture/design change

Audit every catch in the P2P call path. Rethrow CancellationException before
classification. Use coroutineScope/supervisorScope intentionally, cancelAndJoin
children, NonCancellable only for minimal required finalization, and withTimeout
for close completion. No sleep-based race masking.

4. Exact code areas affected

P2pKitRoomTransport wrappers/collectors/close, coordinator actor/send loop,
orchestrator, discovery scheduler, admission/rejoin transactions, game bridges,
platform lifecycle shutdown and all related tests.

5. Compatibility impact

No wire/storage/API change except more accurate error reporting. Callers that
relied on cancellation becoming a Result failure must be corrected.

6. Failure and edge cases

Cancel during discovery, handshake, approval, send, receive, secure-store write,
resume rotation, game reducer, terminal broadcast, close, and nested timeout.

7. Tests required

Deterministic cancellation injection at every suspending boundary; assert parent
receives CancellationException, no error UI/event is emitted, child jobs end,
and handles close. Enable coroutine-debug/leak checks in test builds.

8. Regression risks and proof

Could suppress real failures or skip essential cleanup. Prove typed non-
cancellation failures still surface, minimal finalization completes, and every
resource is released.

9. Acceptance criteria

No audited suspending catch swallows cancellation; cancellation tests pass at
every boundary; cleanup completes within deadline with zero leaked jobs/handles.

10. Verification evidence

Catch-audit checklist, cancellation test report, coroutine job dump before/
after repeated sessions and classified error tests.

### P2P-08 — atomic room capacity

1. Root cause

Capacity is checked separately from final membership mutation. Concurrent host
approvals can each observe a free slot and both commit.

2. Correct target behavior

Committed plus reserved membership never exceeds capacity, even with
simultaneous approvals, resumes, disconnects and host UI actions.

3. Architecture/design change

Capacity reservation is an atomic transition owned by the admission actor.
Every pending admission has one reservation id. Resume of an existing reserved
seat does not consume a new seat.

4. Exact code areas affected

P2pKitRoomTransport admission/member collections, room state model, host
approval UI intents, rejoin transaction, coordinator membership projection and
capacity tests.

5. Compatibility impact

No P2pKit change. Admission protocol v2 carries reservation/offer identifiers.
User-visible RoomFull timing becomes deterministic.

6. Failure and edge cases

Two approvals for last slot; approval plus resume; duplicate approval click;
disconnect after reserve; capacity/config change; kicked/left member during
approval; stale UI approval.

7. Tests required

Barrier-controlled concurrency tests, property tests over arbitrary admission/
leave/resume interleavings, duplicate approval, boundary sizes and fault
injection.

8. Regression risks and proof

Could strand capacity or reject a legitimate resume. Prove reservations always
have expiry/rollback and existing-seat resume is distinguished from new join.

9. Acceptance criteria

The capacity invariant holds after every recorded transition; exactly the
allowed number commits; all unused reservations release deterministically.

10. Verification evidence

Property-test traces, race-test iterations, invariant assertions in debug/test,
and three-device boundary runs.

### P2P-09 — transactional admission rollback

1. Root cause

Membership/token state is committed before approval delivery/confirmation is
known. Disconnect or send failure mid-approval can leave ghost members, indexes
or usable credential material.

2. Correct target behavior

A player is visible as a committed member only after authenticated confirmation.
Failure at any earlier boundary restores the exact pre-admission state.

3. Architecture/design change

Use Pending -> Reserved -> Offered -> Confirmed -> Committed with a transaction
record and one rollback function. Publish lobby projection only at commit.
Disconnect handler consults transaction state and invokes idempotent rollback.

4. Exact code areas affected

P2pKitRoomTransport approval/send/disconnect paths, membership/token maps,
protocol offer/confirm messages, host approval UI, diagnostics and rejoin code.

5. Compatibility impact

Protocol v2 admission handshake changes. No public P2pKit change. Host UI gains
Pending/Approving/Joined/Failed states.

6. Failure and edge cases

Disconnect before/after offer; acceptance send fails after local write; confirm
duplicates/delays; cancellation during secure-store creation; host shutdown;
malformed confirm; credential rotation failure; timeout races with confirm.

7. Tests required

Inject a fault at every linearly ordered boundary; disconnect at each state;
duplicate/delayed confirm; host shutdown; timeout/confirm race; property test
no orphan membership/index/credential.

8. Regression risks and proof

Could make approval slower, publish member too late, or roll back a committed
resume. Prove UI states, idempotent commit marker, result ordering and complete
playthrough after admission.

9. Acceptance criteria

Every failed transaction leaves zero member/index/credential residue; every
committed member has exactly one authenticated peer and reservation; rollback
is safe when invoked repeatedly.

10. Verification evidence

Fault matrix, state transition logs, post-test registry assertions and device
disconnect-during-approval evidence.

### P2P-10 — direct/manual connection decision

1. Root cause

Documentation/testing suggests a fallback without a defined product requirement
or transport-independent authenticated provisioning design. A random direct-IP
workaround would bypass discovery assumptions and risk security inconsistency.

2. Correct target behavior

Either the product explicitly says manual connect is unsupported, or it offers
an authenticated endpoint/fingerprint flow with complete parity to discovery.

3. Architecture/design change

Use ADR-009. Capability is exposed above P2pKit, while the P2pKit adapter maps
it to a supported manual endpoint API. Both paths converge before handshake.

4. Exact code areas affected

Product requirements, transport capability interface, P2pKit adapter, join UI,
endpoint/fingerprint validation, QR/deep-link provisioning if approved, tests
and all manual-test docs.

5. Compatibility impact

Unsupported decision changes documentation only. Supported decision adds an
application API/UI and provisioning format; it must not alter discovery or
weaken encryption/admission.

6. Failure and edge cases

Wrong/stale endpoint, wrong fingerprint, NAT/non-LAN address, port change,
malicious QR, unreachable host, duplicate room, network switch, version
mismatch and manual resume.

7. Tests required

If supported: codec/input validation, wrong fingerprint, timeout/unreachable,
same admission/security tests as discovery, full game and resume parity. If
unsupported: UI/docs tests must contain no manual fallback claim.

8. Regression risks and proof

Could create a weaker trust path or confuse LAN versus Internet support. Prove
identical secure handshake/admission code path and explicit topology copy.

9. Acceptance criteria

Binary: owner records Unsupported for release and all claims are removed; or
every authenticated manual-connect parity test and physical row passes.

10. Verification evidence

Signed owner decision, architecture test proving convergence, security test
results and physical evidence if supported.

### P2P-11 — iOS Local Network permission/recovery

1. Root cause

The iOS permission gate treats permission as Granted without a reliable
preflight. Bonjour/local-network denial, missing plist configuration and generic
transport failure are not modeled truthfully.

2. Correct target behavior

UI reports operational state only from evidence, gives Settings guidance for
actionable denial, retries after foreground, and does not mislabel unrelated
network failure.

3. Architecture/design change

Implement ADR-010 and, if necessary, an additive P2pKit operational health/error
sink. Permission UI consumes typed operational state, not a fabricated Boolean.

4. Exact code areas affected

P2pPermissionGate.ios.kt, common permission UI/model, iOSApp.swift foreground
recheck, Info.plist validation, P2pKit adapter error mapping, Settings opener,
docs and Apple-native tests.

5. Compatibility impact

No protocol/storage change. Shared permission API changes from binary to typed
state; Android maps its explicit runtime/platform states honestly.

6. Failure and edge cases

First prompt, deny, allow, Settings change, app returns foreground, missing/
wrong Bonjour service, airplane mode, no Wi-Fi, hotspot, network switch,
transport bind failure and OS variants.

7. Tests required

State reducer tests, Settings URL adapter, plist validation, mocked operational
errors, physical first-prompt/deny/recover, reinstall/reset permission and
unrelated network failure.

8. Regression risks and proof

Could block users while state is unknown or send them to Settings unnecessarily.
Prove Unknown/Requesting UX remains usable, denial classification requires
evidence, and generic errors retain troubleshooting actions.

9. Acceptance criteria

GrantedOperational never appears before successful local operation; actionable
denial offers a working Settings path; returning from Settings rechecks and can
recover without process restart.

10. Verification evidence

Apple-native tests, plist report, signed physical-device screen recording and
redacted operational events.

## 6. Newly discovered related issue plans

### P2P-12 — atomic state/revision/projection

Root cause: game bridges and StateFlow publication can expose state and
revision from different transitions. Target: one immutable AuthoritativeState
contains game state, revision, phase, membership and result ledger. The
transaction actor publishes it once. Update Whodunit/Mafia bridges and
pass-and-play adapters to observe/project rather than mutate.

Compatibility: internal shared APIs change; game rules and wire projection
remain versioned. Edge cases include observer reentrancy, slow collectors and
terminal transition during snapshot creation. Add reducer/bridge concurrency,
snapshot-revision and slow-collector tests. Risk is breaking UI rendering or
pass-and-play; prove all game UI/state tests and full playthroughs. Acceptance:
no observer can see mismatched revision/state, confirmed by invariant tests and
captured transition histories.

### P2P-13 — honest version compatibility

Root cause: major-compatible/minor-tolerant claims conflict with strict JSON
ignoreUnknownKeys=false. Target: protocol v2 feature negotiation plus a codec
whose behavior matches the written compatibility promise. Exact code areas are
wire serializer configuration, envelopes, validators and compatibility docs.

Compatibility requires an explicit v1 decision and golden payload fixtures.
Test old reader/new optional field, new reader/old payload, required-feature
rejection, unknown message kind and unsupported game version. Risk is silently
accepting a message whose semantics changed; prove required features are
rejected. Acceptance: every supported version pair has bidirectional golden
tests; every unsupported pair gets UpdateRequired.

### P2P-14 — semantic message validation

Root cause: syntactically decodable command results, heartbeat, session-end and
control frames are not always checked for legal enum/state/revision/identity
relationships. Target: validate envelope, size, state legality, revision,
connection epoch and sender authorization before mutation.

Add per-kind validators, exhaustive malformed/control-state tests and fuzzing.
Risk is rejecting a legitimate terminal message during cleanup; prove every
state/message matrix. Acceptance: no unvalidated message reaches domain logic;
malformed input closes or rejects only the offending peer with a safe event.

### P2P-15 — encoded payload bounds

Root cause: JSON ByteArray representation expands data so a nominal snapshot
can cross the 600 KiB transport cap after serialization. Target: enforce the
encoded protocol bounds in ADR-003 using compact bytes and a single combined
snapshot budget.

Test worst-case game states, exact boundary plus/minus one, encoded expansion,
malicious declared length and allocation behavior. Risk is breaking large valid
games; prove every supported player/game state fits with at least a documented
margin. Acceptance: oversize is rejected before large allocation/send, and all
shipping maximum states fit beneath 256 KiB.

### REL-01 — Maven Central provenance

Root cause: the app now correctly uses Maven but release evidence does not
record the published artifact's checksum, source/tag relationship, dependency
graph, licenses or SBOM. Target: lock/verify dependencies, record POM/module
metadata and checksums, generate SBOM/license report and document publication.

No version upgrade is implied. Risk is accidentally introducing dependency
locking conflicts; prove clean dependency resolution on CI/macOS and compare
artifact behavior with the inspected tag. Acceptance: reproducible resolution
and reviewed provenance artifact are attached to the release.

## 7. Dependency-aware implementation phases

### Phase 0 — protect baseline and settle decisions

Changes:

* record both Git states and ownership of every dirty Parlor file;
* re-resolve 0.7.0-rc2 and capture dependency provenance;
* inventory exact Gradle tasks and current pass/fail baseline;
* resolve manual-connect, trust model, v1 compatibility, release targets and
  authority to publish P2pKit changes.

Tests/verification:

* read-only Git status/diff;
* Gradle dependencyInsight for p2p-core and p2p-transport-lan;
* existing documented checks without source edits.

Rollback/regression: no behavior change. Definition of done: reproducible
baseline and no overlapping dirty file is edited without owner direction.

### Phase 1 — protocol contract and deterministic fault harness

Changes:

* protocol v2 bounded envelope/codec and version negotiation;
* connection epoch, sequence, message/command ids;
* semantic validators and encoded size limits;
* in-memory two-peer transport with duplicate, reorder, delay, loss, malformed
  input, disconnect and cancellation injection.

Tests:

* golden compatibility, unknown/required features, boundary/fuzz tests;
* deterministic two-peer handshake and frame fault tests.

Verification:

* common/JVM tests and serialization/protocol suites;
* no game behavior changes yet.

Rollback/regression: retain v1 fixtures, and keep protocol changes isolated.
Definition of done: malformed/incompatible/oversize input is deterministic and
the fault harness can reproduce every later network condition.

### Phase 2 — atomic authority and command outcomes

Changes:

* authoritative transaction actor and immutable state/revision;
* authenticated actor binding;
* command ledger, explicit result/outcome query and stale-policy registry;
* remove unsafe send-failure replay;
* migrate Whodunit/Mafia bridges to read projections.

Tests:

* concurrent same-revision actions;
* duplicates/lost ACK/reconnect outcome;
* every shipping command classified;
* game state-machine/property and pass-and-play regression.

Verification:

* shared/session and game module suites;
* two-peer full game under duplicate/reorder/loss.

Rollback/regression: phase boundary preserves a compatibility adapter if
approved. Definition of done: exactly-once state effects and atomic projection
in every shipping game.

### Phase 3 — orchestrator, lifecycle, threading and cancellation

Changes:

* MultiplayerSessionOrchestrator and logical state machine;
* application-owned scopes and deterministic cleanup;
* Android/iOS lifecycle adapters;
* off-main P2pKit initialization;
* complete CancellationException audit.

Tests:

* virtual-time lifecycle; Android StrictMode; Apple scenePhase;
* cancel at every suspending boundary; job/resource leak tests.

Verification:

* common/JVM, Android instrumentation and Apple-native suites;
* repeated host/join/end cycles.

Rollback/regression: UI is migrated behind an adapter so navigation can be
compared. Definition of done: one logical owner, no main-thread I/O, no swallowed
cancellation and deterministic lifecycle state.

### Phase 4 — transactional admission, capacity and secure rejoin

Changes:

* reservation/offer/confirm/commit admission;
* atomic capacity invariant and idempotent rollback;
* encrypted platform credential stores;
* resume generation/rotation/expiry and duplicate-connection policy.

Tests:

* barrier-controlled capacity races;
* fault injection at every admission/resume boundary;
* secure-storage/process-death/replay/expiry tests.

Verification:

* host/peer integration, Android Keystore and iOS Keychain device tests.

Rollback/regression: version persistent records; migration can delete only
obsolete non-functional token state, never unrelated data. Definition of done:
no over-capacity/ghost membership and secure process-death resume works.

### Phase 5 — minimal additive P2pKit hardening

Changes:

* only additive operational health/error/config hooks proven necessary;
* preserve rc2 public API or provide deprecation/migration plan;
* publish a new version only after compatibility and provenance review.

Tests:

* P2pKit common/platform/API compatibility, cancellation, buffer and lifecycle
  tests; Parlor adapter contract tests against the artifact.

Verification:

* all P2pKit targets and samples; Maven publication dry run; resolved Parlor
  artifact behavior.

Rollback/regression: Parlor adapter supports rc2 until approved migration.
Definition of done: no undocumented fork behavior and no unnecessary breaking
change.

### Phase 6 — bounded transport, discovery and platform permission UX

Changes:

* bounded byte-counted queues/rates/admission throttling;
* discovery scheduler and final error model;
* iOS operational permission states/Settings recovery;
* Android least-privilege validation;
* redacted diagnostics.

Tests:

* flood/load/heap, virtual-time discovery, permission reducers, native platform
  tests and multi-room integration.

Verification:

* low-end Android and iOS physical smoke after automated suites.

Rollback/regression: limits are centralized/versioned and can only be tuned
with evidence. Definition of done: bounded behavior, robust discovery and
truthful permission recovery.

### Phase 7 — game extensibility and shipping-game regression

Changes:

* GameDescriptor/engine/codec/UI registration boundary;
* Whodunit and Mafia adapters;
* non-shipping minimal second-game fixture.

Tests:

* registration acceptance test proves no networking-core edit;
* complete Whodunit/Mafia lifecycle, rematch and fault playthroughs.

Verification:

* dependency graph check ensures game modules do not import P2pKit.

Rollback/regression: preserve game rule fixtures and screenshots. Definition of
done: a new game is localized to its module/registration and both shipping games
retain behavior.

### Phase 8 — documentation, CI and supply chain

Changes:

* canonical architecture/protocol/lifecycle/rejoin/manual-test docs;
* mark historical validation documents;
* stale-claim CI checks;
* dependency locking/checksum, SBOM and licenses;
* release diagnostics/privacy copy.

Tests/verification:

* docs commands run on clean CI;
* manifest/plist and stale-string validation;
* SBOM/license review.

Rollback/regression: historical evidence is retained with banners, not erased.
Definition of done: implementation, tests and docs describe identical behavior.

### Phase 9 — full automated release regression

Required baseline commands include:

    ./gradlew productionCheck
    ./gradlew productionAppleCheck

Also run the discovered repository tasks for:

* Android release compilation;
* Android lintRelease;
* R8/minified release;
* signed/unsigned release AAB as credentials permit;
* JVM/desktop/common tests;
* P2P adapter and two-peer fault suites;
* iosArm64, iosSimulatorArm64 and iosX64 linkage;
* Android manifest and iOS plist validation;
* protocol fuzz/stress and repeated-session soak.

Exact task paths must be copied from Gradle tasks/documentation at execution
time rather than invented. Every command, exit result, artifact and limitation
is recorded.

Definition of done: all locally buildable supported targets pass; unavailable
signing/device/store gates are marked UNVERIFIED, never passed by inference.

### Phase 10 — signed physical validation

Build release/signed artifacts, execute section 9, collect section 10 evidence,
fix reproducible defects, and repeat affected plus regression rows.

Definition of done: every required supported row passes consistently. No
Android/iOS/hotspot/cross-platform “fully supported” claim exists before this.

## 8. Automated test strategy

### Unit and state-machine tests

* all logical session transitions and illegal transitions;
* timer freeze/resume/expiry with virtual time;
* admission and resume state machines;
* every Whodunit and Mafia command/phase/win/tie/rematch/reset rule;
* deterministic randomness/seed ownership where games use randomness;
* permission and diagnostic redaction reducers.

### Protocol tests

* golden serialization for every message;
* supported/unsupported version pairs and required features;
* encoded byte limits plus/minus one;
* semantic state/message matrix;
* duplicate, stale, old epoch, sequence jumps and unknown kind;
* malformed/fuzz/property tests without large pre-validation allocation.

### Concurrency and race tests

* two commands against one revision;
* approvals for the final seat;
* approval plus resume;
* disconnect/cancellation at every admission/resume boundary;
* result versus snapshot ordering;
* lifecycle event versus send/close/terminal transition;
* concurrent kit initialization;
* deterministic barriers/channels rather than timing sleeps.

### Integration and fault-injection tests

At least two in-process peers with:

* drop, delay, reorder and duplicate;
* malformed and incompatible messages;
* lost command, lost result and lost snapshot;
* peer/host disappearance;
* network switch and reconnect;
* process-death credential restoration;
* multiple rooms and wrong-code candidate;
* repeated host/join/leave/cleanup;
* simultaneous actions and rematch.

### Stress and resource tests

* ten-minute sustained flood;
* maximum legal frame/snapshot/game state;
* maximum supported players;
* admission spam and rotating connections;
* slow consumer/snapshot storm;
* twenty complete repeated sessions;
* coroutine/job/socket/discovery/session count returns to baseline;
* memory remains within the documented budget.

### Platform tests

* Android StrictMode/startup, lifecycle, permission/manifest and release/R8;
* Apple-native lifecycle, Keychain, Local Network denial/recovery, plist and
  architecture linkage;
* desktop/common compatibility where it is a supported development target.

## 9. Physical-device validation matrix

For every case record:

* device manufacturer/model;
* exact Android/iOS version and build;
* Parlor version, build type and Git SHA;
* P2pKit Maven version/checksum;
* host/peer roles;
* network topology, access point/hotspot owner and settings;
* timestamps, exact steps, result, redacted diagnostic export;
* screenshots/video where useful;
* cleanup/resource observations;
* PASS, FAIL or UNVERIFIED.

Core topology cases must pass five consecutive runs after a cold launch and five
repeated-session runs without reboot. Failure resets the consistency count after
the root cause is fixed.

| ID | Scenario and prerequisites | Exact steps | Expected/pass criteria |
|---|---|---|---|
| PHY-01 | Android to Android, normal Wi-Fi | Install signed build, host on A, discover/join on B, complete game/rematch/leave; reverse roles | Discovery, secure admission, synchronized commands/snapshots, rejoin and cleanup pass both ways |
| PHY-02 | iPhone to iPhone, normal Wi-Fi | Same sequence and role reversal | Same plus truthful Local Network behavior |
| PHY-03 | Android host, iPhone peer | Complete full session and fault cases | Cross-platform protocol/state equality |
| PHY-04 | iPhone host, Android peer | Complete full session and fault cases | Cross-platform protocol/state equality |
| PHY-05 | Three or more devices | Fill supported room, act simultaneously, complete/rematch | Atomic capacity, actor binding, snapshot convergence |
| PHY-06 | Android phone owns hotspot and hosts | Connect peers to hotspot; host from owner; play/rejoin/repeat | Owner reaches clients; clients receive synchronized state; documented manufacturer/OS limitations |
| PHY-07 | Android phone owns hotspot, connected device hosts | Owner joins client host; add another client where possible | Owner-client and client-client reachability/discovery proven |
| PHY-08 | iPhone owns Personal Hotspot and hosts | Connect peers; host on owner; full play/rejoin | Owner reaches clients and Bonjour/discovery behavior recorded |
| PHY-09 | iPhone owns Personal Hotspot, connected device hosts | Owner joins connected host; add another client | Owner-client and client-client behavior recorded |
| PHY-10 | Multiple discoverable rooms | Start wrong-code room first, correct room later | Scheduler ignores wrong room and joins correct room before deadline |
| PHY-11 | Manual/direct path | Run only if owner requires capability | Wrong fingerprint fails; correct endpoint has security/admission parity |
| PHY-12 | Host background/foreground and lock/unlock | Interrupt below and above 120 seconds | Short resumes; long expires; timers/state match policy |
| PHY-13 | Peer background/foreground and lock/unlock | Interrupt below and above 120 seconds | Short resumes same slot; long expires |
| PHY-14 | Peer process death/relaunch | Kill process during lobby/play and relaunch in grace | Encrypted credential resumes once without duplicate member |
| PHY-15 | Host process death/exit | Kill host, then explicit host exit in separate run | Peers end deterministically; no host migration claim; cleanup succeeds |
| PHY-16 | Voluntary final Leave | Peer leaves, then tries resume | Old credential cannot rejoin |
| PHY-17 | Network switching | Switch Wi-Fi/hotspot during lobby/play | Suspend/resume or deterministic expiry; no torn state |
| PHY-18 | Simultaneous commands | Coordinate legitimate same-revision actions | Results match per-command stale policy; no double action |
| PHY-19 | iOS permission denial/recovery | Fresh install/reset, deny, open Settings, allow, foreground | Truthful state, Settings path and recovery without reinstall |
| PHY-20 | Android network/permission conditions | Test target-SDK relevant denied/settings/no-Wi-Fi states | Least privilege, actionable error, recovery |
| PHY-21 | Repeated sessions | Twenty host/join/play/end cycles without device restart | No stale discovery/session, leak or progressive failure |
| PHY-22 | Sustained session | Long representative play and background cycles | Stable battery/memory/network behavior |
| PHY-23 | Signed release artifacts | Use Play internal/TestFlight/ad-hoc release-like builds | Runtime matches debug-tested behavior and release logs/privacy policy |

A case is UNVERIFIED when a required device, signing identity, store account,
network topology or OS version is unavailable. Simulator/build/unit evidence
cannot convert it to PASS.

## 10. Documentation remediation

Documentation is part of the behavior contract and must change in the same
phase as implementation.

| Document | Required correction |
|---|---|
| docs/P2P_MANUAL_TEST.md | Replace obsolete examples/envelopes/timeouts; define exact discovery/manual policy and evidence capture |
| docs/PARLOR_P2P_SMOKE_TEST.md | Use current protocol/session states and redacted diagnostic event names |
| docs/MULTIPLAYER_PLAYTEST.md | Add lifecycle, rejoin, concurrent commands, capacity, multiple rooms and cleanup |
| docs/IOS_SETUP.md | Use Local Network/Bonjour wording, plist keys and truthful Settings recovery; remove Multipeer/Bluetooth wording |
| docs/ARCHITECTURE.md | Replace Nearby Connections, MultipeerConnectivity and Desktop WebSocket claims with actual P2pKit LAN architecture |
| docs/APP_PLAN.md | Align target lifecycle, authority, topology and release scope |
| docs/PROGRESS.md | Separate historical progress from current verified status |
| docs/PHASE_8_VALIDATION.md | Mark historical results; do not present them as current rc2 runtime evidence |
| docs/PRODUCTION_ARCHITECTURE.md | Document orchestrator, protocol v2, authority, admission, rejoin and bounds |
| docs/PRIVACY_AND_COMPLIANCE.md | Document diagnostics redaction, identity/token storage, local-network disclosure and least privilege |
| PROBLEMS_PARLOR.md | Reconcile resolved/open findings with evidence and risk acceptance |

Mandatory consistency corrections:

* do not request ParlorP2p logs while logging is a no-op;
* final Leave does not mean resumable disconnect;
* remove the obsolete documented 10-second join timeout and the current
  undifferentiated 120-second join timeout claim; document 30-second scheduler,
  5-second attempts and 60-second approval;
* replace obsolete P2P_MANUAL_TEST protocol examples;
* remove obsolete Android Nearby permissions;
* remove current-architecture references to Nearby Connections,
  MultipeerConnectivity and Desktop WebSockets;
* replace iOS Multipeer/Bluetooth wording with Local Network/Bonjour;
* add prominent Historical, not current verification banners to old reports;
* state LAN/hotspot/manual/cross-platform support only to the level physically
  verified;
* make every command in docs executable against the final task graph.

Add a CI stale-document check for old timeout values, old transport names,
Nearby permission claims, tokenless rejoin claims and unsafe log instructions.

## 11. How to add a new game after remediation

1. Create a game module that depends on shared game/session contracts, never
   P2pKit.
2. Define stable game id/version, player bounds and GameDescriptor.
3. Implement immutable state, commands/events, a pure validator/reducer and
   deterministic randomness policy.
4. Implement bounded state/command/projection codecs and semantic validators.
5. Define stale policy and idempotency class for every command.
6. Add UI route/factory, assets, accessibility/localization and error states.
7. Register the descriptor in the catalog through the registration contract.
8. Add rule/state-machine/property/serialization tests and run the shared
   two-peer protocol fixture.
9. Prove the new game can host/join/play/end/rematch without modifying session,
   protocol, discovery, admission or P2pKit adapter code.
10. Add shipping physical scenarios only after automated acceptance passes.

The non-shipping test game proves this workflow without adding a fake production
game.

## 12. Regression and safety verification

For every major change, the following answers “What could this break elsewhere,
and how will we prove it did not?”

| Change | Potential regression | Proof |
|---|---|---|
| Orchestrator/lifecycle | single-player navigation/timers, duplicate cleanup | existing UI/game tests, timer invariants, repeated-session leak test |
| Protocol v2 | rc2/v1 interoperability, snapshot decode | owner compatibility decision, golden fixtures, update-required tests |
| Atomic command actor | game rules, simultaneous valid actions, UI latency | all rule/property tests, two-peer concurrent play, result UI tests |
| Secure rejoin | duplicate member, stale secret, restore failure | fault matrix, encrypted-store inspection, process-death physical run |
| Admission reservation | approval UI, capacity release | property/race tests and three-device boundary run |
| Queue/rate bounds | legitimate slow devices, terminal message loss | maximum legal payload tests, low-end devices, prioritized control lane |
| Discovery scheduler | battery, duplicate dials, slower errors | virtual time, one-dial invariant, soak/network counters |
| Permission UX | false blocking or false Settings guidance | native operational-state and deny/recover physical tests |
| Game descriptors | existing game navigation/rules | full Whodunit/Mafia regression and dependency graph check |
| Diagnostics | secret/privacy leakage or insufficient evidence | allowlist/redaction tests and manual export review |
| Maven hardening | resolution/build reproducibility | clean CI resolution, checksum and SBOM evidence |

Before and after every phase:

* inspect Git diff and status;
* verify no unrelated user file changed;
* run phase tests and impacted regression suites;
* record exact commands/results;
* inspect logs for cancellation/resource leaks;
* keep a rollback note and protocol/storage migration note;
* never weaken/delete tests or hide failures.

## 13. Master production-readiness checklist

| Gate | Planned fix | Required evidence | Final acceptance |
|---|---|---|---|
| P2P-01 | ADR-001/006, Phase 3 | lifecycle unit/native/physical | deterministic Android/iOS policy and cleanup |
| P2P-02 | ADR-004, Phase 4 | secure-store/replay/process-death | bounded secure rejoin; final Leave invalid |
| P2P-03 | ADR-002/007, Phase 2 | concurrency/ACK/fault tests | exactly-once effects and explicit result |
| P2P-04 | ADR-005, Phase 6 | flood/heap/rate/fairness | hard memory bounds and peer isolation |
| P2P-05 | ADR-008, Phase 6 | virtual scheduler/multi-room physical | correct retries/deadlines/errors |
| P2P-06 | Phase 3 | StrictMode/startup/native | no main-thread identity I/O |
| P2P-07 | Phase 3 | cancellation boundary audit/tests | CancellationException propagates; no leaks |
| P2P-08 | admission actor, Phase 4 | race/property/three-device | capacity invariant always holds |
| P2P-09 | admission transaction, Phase 4 | boundary fault matrix | no ghost member/credential |
| P2P-10 | ADR-009 | owner decision plus parity tests if supported | explicitly unsupported or fully authenticated |
| P2P-11 | ADR-010, Phase 6 | plist/native/physical recovery | truthful permission UX |
| P2P-12 | ADR-002 | atomic state/projection tests | no torn state/revision |
| P2P-13 | ADR-003 | version golden matrix | compatibility claims equal codec behavior |
| P2P-14 | protocol validators | semantic/fuzz matrix | no invalid message reaches domain |
| P2P-15 | encoded bounds | boundary/worst-game tests | reject before allocation; valid state fits |
| Maven Central provenance | REL-01, Phase 0/8 | graph/checksum/SBOM/licenses | artifact tied to reviewed source/release |
| Android compilation | Phase 9 | release compile result/artifact | PASS |
| iOS compilation | Phase 9 | arm64/simulator/x64 linkage | PASS where supported |
| JVM/desktop compilation | Phase 9 | common/JVM/desktop results | PASS where supported |
| Android manifest | Phase 8/9 | merged manifest validation | least privilege and target behavior |
| iOS plist | Phase 8/9 | plist/Bonjour validation | exact Local Network declarations |
| Encryption/identity | P2pKit contract plus actor binding | Noise/config and impersonation tests | no weakened encryption or client authority |
| Duplicate/order/version/snapshot | Protocol v2 | fault/golden suites | deterministic convergence |
| Simultaneous commands | P2P-03 | game-specific concurrent tests | no double execution/lost unexplained intent |
| Lifecycle/rejoin | P2P-01/02 | automated plus physical | exact documented semantics |
| Admission/backpressure/capacity | P2P-04/08/09 | flood/race/fault | bounded and transactional |
| Direct-connect decision | P2P-10 | signed owner decision | product/docs/tests aligned |
| Android to Android | Phase 10 PHY-01 | exact signed-device record | consistent PASS |
| iOS to iOS | Phase 10 PHY-02 | exact signed-device record | consistent PASS |
| Android to iOS | Phase 10 PHY-03/04 | both host directions | consistent PASS |
| Three-device/hotspot/repeated | PHY-05 through PHY-09, PHY-21 | topology/device evidence | consistent PASS for claimed scope |
| Signed store artifacts | PHY-23 | release build/SHA/store-like install | behavior matches release claims |
| Documentation | Phase 8 | stale check plus reviewed docs | implementation/tests/docs identical |

## 14. Decisions requiring owner input

Manual/direct endpoint connection was resolved after this plan was approved:
it is unsupported for release one under ADR-0002. The remaining historical
owner questions were:

1. Which Android/iPhone models, OS versions and hotspot configurations are in
   the explicit support promise?
2. Is first-contact trust AcceptAny with authenticated pairing, or must users
   verify an out-of-band host fingerprint/QR?
3. Are any deployed protocol-v1 clients required to interoperate, or may v2
   reject them with UpdateRequired?
4. May P2pKit publish an additive version if operational health/error APIs are
   needed?
5. Which signing/store accounts, privacy disclosures, analytics and
   crash-reporting systems are mandatory?
6. Should desktop/development builds persist a resume credential, or remain
   intentionally ephemeral?
7. Which existing dirty Parlor files belong to current user work when a phase
   needs to touch the same file?

The following can be decided technically and should remain unless the owner
chooses a different product policy:

* host remains authoritative;
* host process death ends the room in release one;
* lifecycle grace is 120 seconds;
* rejoin credentials are encrypted and replay-resistant;
* no client-supplied actor/player id is trusted;
* no non-idempotent game action is retried silently;
* buffering and admission are bounded;
* iOS permission state is operationally truthful;
* P2pKit encryption/authentication is never weakened;
* physical behavior is not marked verified from compilation/simulator evidence.

## 15. Final definition of done

The remediation is complete only when:

* every intended target builds in a supported environment or has an explicit
  external verification gate;
* Whodunit and Mafia rules/state transitions are centralized and fully tested;
* two peers complete the full lifecycle and game under fault injection;
* duplicate/order/version/snapshot/simultaneous-command behavior is deterministic;
* lifecycle, rejoin, admission, capacity and cleanup match the state machines;
* P2pKit ownership, permissions, privacy and security match actual transport;
* a test-only second game proves extensibility without networking-core change;
* no Blocker/Critical/High remains without explicit risk acceptance;
* productionCheck, productionAppleCheck, release/lint/R8/AAB, common/JVM/
  desktop, P2P adapter, iOS linkage, manifest and plist gates have recorded
  evidence;
* the signed physical-device matrix passes for every claimed platform/topology;
* documentation describes the exact final implementation and limitations;
* the final report lists exact code changes, commands/results, physical evidence,
  UNVERIFIED store/device checks and residual risks.

Until all required physical rows pass consistently, Parlor must not claim 100
percent Android, iOS, cross-platform, or hotspot compatibility.
