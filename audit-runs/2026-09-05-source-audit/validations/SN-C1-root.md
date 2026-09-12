# SN-C1 — independent validation by `/root`

**Classification: CONFIRMED DEFECT; Medium.** Finder `/root/session_cont`; validator `/root`. Source `main` at commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`, unchanged tracked files. Absolute source prefix `/Users/abdelrahman/Projects/parlor/`.

## Reopened path and proof

1. Both actual peer-start callers (`WhodunitPeerSessionFlow.kt:638–717`, `MafiaPeerLobbyFlow.kt:554–583`) call `awaitAuthoritativeSessionStart`; only Success installs the retained Started checkpoint. Failure is retained and displayed, so a later duplicate commit cannot on its own install the game coordinator.
2. `SessionStartHandshake.kt:202–287` prepares matching start/protocol state, sends Ready, then encloses both receiving a commit and sending its acknowledgment in one `withTimeoutOrNull(deadlineMs)`. Matching valid commit at255–262 passes all checks. The suspension at265–271 precedes Success272.
3. The helper at356–369 rethrows outer cancellation. Thus when that deadline expires during acknowledgment, the outer expression returns null and287 returns Network(Timeout), despite already accepting valid irreversible host authority. An inner ACK timeout helps only when it expires first.
4. Host `AuthoritativeSessionCoordinator.kt:970–995` records completed start, sets Started and completes Success before commit delivery;734–741 expressly completes delivery without rollback on ACK deadline. The peer coordinator1800–1826 can re-ack committed starts only after successful runtime installation. No hidden rollback rescues the failed peer.
5. Production `P2pKitRoomTransport.kt:4608–4641` invokes suspending `P2pSession.send`, not an always-synchronous mock. Maven Central **0.7.0-rc3** source `P2pSessionImpl.kt:359–369` acquires `sendMutex` and calls suspending `protocol.sendMessage`. A commit arrival near the wait deadline and an ACK extending past the remaining budget is a valid production schedule, not a test-only API assumption.

## Executed isolated reproduction

Cycle `evidence/repro-session-01/receipt.json` ran `:shared:session:desktopTest --tests '*SN_C1LateStartCommitAckTest*'` with an audit-only init-script source addition, JDK21/Gradle8.13/strict verification. Compilation succeeded; **one test ran and the Success assertion failed with actual Result.Failure** (XML and `test-receipts.json` preserved). The test delivers a valid commit at99 ms of a100-ms wait and delays ACK10 ms with a20-ms ACK timeout. The source path identifies the failure as Network(Timeout); the initial XML asserts only the result subtype, so it is not presented as a printed Timeout value. This is a deterministic coroutine/session-boundary reproduction, **not physical P2pKit/LAN or device UI evidence**.

Existing test931–952 was reopened: it returns immediate NotConnected at ACK send and therefore correctly succeeds. This is counter-evidence against a general failed-ACK defect, but not against the deadline race. Explicit caller cancellation, invalid commits, host-lost recovery, and rapid ACK success are different paths and should retain existing semantics.

## Impact / remedy

Both multiplayer games can show failed start while the host is already playing, requiring recovery/rejoin rather than entering the committed session. Pass-and-play does not use this handshake. No secret leak, speculative reducer mutation, permanent loss, or incidence rate is claimed.

Separate accepted-commit decision from best-effort delivery so the receive deadline cannot retroactively invalidate accepted authority. Preserve cancellation, bounded ACK sending, all exact4.2/content checks, and duplicate recovery. Regression tests: valid commit just before deadline with delayed/failed ACK; missing/invalid commit still fails; duplicate commit remains idempotent; caller cancellation; revision-zero snapshot recovery.

## Research and cleanup

Exact-version public library URL/access/hash: `evidence/research-p2p-send/receipt.json`; relevant source saved alongside it. Accessed2026-09-05. This resolves the suspend/send-semantic question, not real network performance.

Reproducer cycle stop exit0, cleanup errors[], remaining outputs[]; source SHA/tree and tracked status unchanged. Only task-created generated directories removed, no global Gradle cache or private inputs touched. Full suite verification remains separate.
