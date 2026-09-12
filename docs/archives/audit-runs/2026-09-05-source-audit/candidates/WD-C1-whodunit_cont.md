# WD-C1 — Whodunit peer readiness resubmits when command overlay removes router

Finder `/root/whodunit_cont`; independent validator `/root/session_cont`, report `candidates/WD-C1-independent-session_cont.md`. Classification **CONFIRMED DEFECT by deterministic reachable source-level proof**, Medium UX/performance (no established privacy/authority breach). Baseline `3625d0663ba6eb51338cbd5f9dc45f859ec18846`; absolute prefix `/Users/abdelrahman/Projects/parlor/`.

## Reachable path and deterministic source proof

- Game binding attaches WhodunitMultiplayerPeerFlow for a started LAN peer; initial authoritative snapshot completed, active room, host remains at PublicIntro/RulesBriefing.
- `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt:1340–1385` renders PeerPhaseRouter only when hasAuthoritativeSnapshot && commandProgress is Idle && !paused. `1369–1375` shows a separate overlay on any non-Idle command, removing the router subtree.
- `.../ui/flow/WhodunitPhaseRouter.kt:339–349` uses LaunchedEffect(phase,selfPlayerId) to submit AcknowledgeIntro or AcknowledgeBriefing, without checking canonical acknowledgement membership and without retained per-phase submission state.
- `.../ui/flow/multiplayer/WhodunitPeerRoomBridge.kt:187–209` delegates action to coordinator; `shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/AuthoritativeSessionCoordinator.kt:1449–1505` creates a fresh ID/sequence and sets Awaiting before transport send. No same-payload dedupe exists there. With result delayed past a frame, overlay composes, removing the auto-ack effect owner.
- After result + snapshot, coordinator `1645–1684` publishes Resolved; GameFlow `1291–1323` consumes it and acknowledgeCommandOutcome returns Idle at coordinator `1520–1532`, or after matching snapshot at `1632–1640`.
- PeerPhaseRouter re-enters same phase; its new LaunchedEffect runs again. A duplicate readiness ACK is an unchanged reduction in WhodunitReducer `262–284`, converted to InvalidAction by WhodunitHostRoomBridge `245–275`.
- GameFlow `1312–1323` now shows invalid-command danger toast, acknowledges result, and repeats until host leaves phase or transport/session changes. Reducer protects canonical readiness set but not UI effect/network churn.

## Counter-evidence investigated

- Fast transport can conflate Awaiting/Resolved/Idle before one Compose frame, avoiding removal in that timing. A delayed synthetic transport makes removal deterministic; no claim every instantaneous fixture exhibits the bug.
- Host stepping phases quickly bounds time, but ordinary host reading intro/rules yields arbitrary repeat duration.
- Coordinator permits only one pending command, bounding simultaneous in-flight work but not sequential auto-submissions; every new effect obtains new commandId/clientSequence.
- PassAndPlay is unaffected by this overlay. Host UI does not run this peer presentation branch.
- Privacy boundaries/host-only reducers remain intact. Extra dossier handoff after StartCharacterReveal uses the same subtree-removal mechanism but is not a separate count without focused verification.

## Proposed reproduction

Using the real peer composable in a controlled Compose test, start peer at PublicIntro with an in-memory room/coordinator and hold host replies for >1 frame. Release first ACK result/snapshot, leave phase unchanged, then observe additional ClientCommand ACKs and InvalidAction toasts. Assert exactly one successful phase ACK across wait/overlay/recomposition. Repeat RulesBriefing and active-session UI recreation; test rejected stale ACK can still recover deliberately without blind non-idempotent action retries.

## Suggested fix (not implemented)

Gate auto-acks on own membership in the corresponding authoritative readiness set and/or own them in retained orchestration. Avoid transient command overlay destroying mandatory effect ownership. Verify stale revision handling and UI re-entry explicitly; do not relax reducer/coordinator validation or optimistic peer state.

The independent validator reopened the shipping peer binding, retained session, action authority, reducer, coordinator, readiness projection and toast paths; checked the pinned Compose effect implementation; and confirmed the delayed-frame schedule after investigating the fast-transport, pending-command, cancellation and explicit-host-advance counter-evidence. No app/Compose/runtime test was run by finder or independent validator. Confirmation is source-level; physical latency/visual behavior remains unexecuted.
