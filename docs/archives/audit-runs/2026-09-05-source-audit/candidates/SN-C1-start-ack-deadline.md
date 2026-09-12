# SN-C1 — Late valid start commit can fail while sending its best-effort acknowledgement

Finder: `/root/session_cont`. Independent validator: **`/root`**. Status: **CONFIRMED DEFECT** (independent evidence in `validations/SN-C1-root.md`). Severity: **Medium**. Applicable Android/iOS/Desktop multiplayer, both games; not pass-and-play.

Baseline commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Source hashes/ranges are in `coverage/reviews-session_cont.jsonl`.

## Source locations (absolute prefix `/Users/abdelrahman/Projects/parlor/`)

- `shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/SessionStartHandshake.kt:210-287`: outer commit-delivery `withTimeoutOrNull(deadlineMs)` includes handling the accepted commit, suspending `sendCommitAck` at 265, and only then returns Success at 272. `sendStartAcknowledgement:356-369` has an inner timeout but rethrows cancellation from the outer timeout.
- `shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/AuthoritativeSessionCoordinator.kt:970-995`: host commits permanently at Ready quorum; completed start and Started state precede commit dispatch. `:1800-1826` peer game coordinator can acknowledge stable duplicate commits, but only after start succeeded and it was constructed.
- `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitPeerSessionFlow.kt:638-717`: actual shipping start caller maps generic failure to retained Failed; success alone produces game start data.
- `game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/multidevice/MafiaPeerLobbyFlow.kt:554-583`: same generic handshake, same success/failure fork.
- `shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt:4608-4641`: actual peer send acquires mutexes and then calls suspending `session.send(payload)`; it does not promise synchronous acknowledgement completion. Independent validator inspected Central rc3 `P2pSessionImpl.kt:359–369` (send mutex plus suspending protocol write); reference/hash in `evidence/research-p2p-send/receipt.json`.
- `shared/session/src/commonTest/kotlin/com/parlor/session/multidevice/SessionStartHandshakeTest.kt:931-952,1274-1291`: existing irreversible-peer-commit test only returns an immediate NotConnected from acknowledgement send. It does not delay a commit ACK across the enclosing deadline.

## Reachable schedule / root cause

1. Peer validates offer and finishes preparation. Ready send succeeds; outer commit-wait budget begins.
2. A valid, matching commit arrives shortly before that budget expires. No invalid ID, mismatched protocol, wrong identity, or malformed state is necessary. The host has already irrevocably started.
3. Peer handles the valid commit and starts its best-effort commit acknowledgement, but that send suspends past the remaining outer budget.
4. Outer timeout cancels the send, the helper rethrows its CancellationException, outer `withTimeoutOrNull` returns null, and line 287 returns a **Network Timeout** instead of Success.
5. The actual game flows retain a failed start, rather than install the already-committed runtime. Retrying may recover the room; this is not proof of permanent data loss, but creates a false failure, disconnect/rejoin interruption and inconsistent start outcome.

Expected behavior is not inferred solely from comments: host already changes to Started at Ready quorum and its commit-ACK timeout is delivery-only (`AuthoritativeSessionCoordinator.kt:734-741`); peer game coordinator explicitly exists to re-ack commits after entry. The immediate failed-ACK test also asserts Success. The bug is the timeout boundary enclosing the acknowledgement and success return, not ordinary missing commits.

## Isolated reproducer (EXECUTED by independent validator)

`reproducers/SN-C1LateStartCommitAckTest.kt` uses public production `awaitAuthoritativeSessionStart`, a bounded synthetic LocalRoom and virtual time. A 100-ms commit budget with a commit at 99 ms and a 10-ms ACK delay expires at 100 ms even though ACK's own 20-ms timeout has not elapsed. Root-owned cycle `evidence/repro-session-01/receipt.json` compiled and executed exactly one isolated test: the desired Success assertion failed with Result.Failure. The deterministic source path identifies Network Timeout; the XML itself asserts only the result subtype, not a printed Timeout value. Stop/cleanup completed without errors or remaining outputs. Real defaults correspond to a commit at 19,999 ms and a send taking more than 1 ms; no slow two-second send is required.

## Counter-evidence / limits

- Inner send timeout handles a stalled ACK only if it wins **before** the enclosing commit timeout. Immediate send failures do not reproduce.
- Host retries help deliver the commit but cannot restart a peer handshake that has already returned Failed; later game-side re-ack is unavailable until a runtime exists.
- Explicit user/session cancellation should still propagate. The candidate concerns handshake-owned timeout after accepting the commit, not cancellation of a still-undecided offer.
- A fast successful ACK avoids the issue. No device or production incidence is claimed. Exact-version P2pKit-send inspection and independent validation are complete; full suite / physical-device results remain separate.

## Suggested remedy / regression test

Separate commit acceptance from best-effort acknowledgement delivery: once a valid commit is accepted within the wait budget, finish the decision independently of the expired waiting deadline; preserve normal cancellation semantics and bounded ACK sending. Add near-deadline tests for success, delayed/failed ACK, duplicate replay, cancellation, and continued first-snapshot recovery. Do not weaken protocol/content checks or alter host commit semantics.
