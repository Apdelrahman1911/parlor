# M-C02 — independent validation

- **Classification: FALSE POSITIVE**, bounded to the claim below. No confirmed defect or severity assigned.
- Finder: `/root/mafia_cont` (`reviews/mafia-cont-notes.md:28`). Independent validator: `/root/session_cont`.
- Reviewed checkout: `main`, HEAD `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; no tracked/index changes. Current source hashes and exact reopened ranges: `M-C02-session_cont.sources.json`.
- Validation date: 2026-09-05T10:19:19.076960+00:00. Audit-only source reopening; **no test/device execution** by this reviewer.

## Candidate

The suspected path was: a required Mafia seat disconnects, its physical transport reconnects without completing the game-start/rejoin handshake, the one-shot grace deadline expires, and transport retirement refuses the seat because it is already connected. That refusal would leave the canonical disconnect marker with no replacement automatic expiry.

The prerequisite refusal is absent from the production implementation. This conclusion does not assert that every conceivable rejoin/lifecycle failure is impossible.

## Reopened production path

Paths below are rooted at `/Users/abdelrahman/Projects/parlor/`; the adjacent manifest records every absolute path and SHA-256.

1. The Android/iOS/Desktop production Koin modules construct `P2pKitRoomTransport` (`shared/transport-p2p/src/{androidMain,iosMain,desktopMain}/kotlin/com/parlor/transport/p2p/P2pTransportModule.*.kt:89–96,63–70,86–93`). Its `host` constructs **HostP2pRoom**, not the fail-closed interface default (`shared/transport-p2p/src/commonMain/kotlin/com/parlor/transport/p2p/P2pKitRoomTransport.kt:259–325`).
2. `MafiaHostLobbyFlow.kt:248–301` calls process-owned `freezeAdmissions`, then creates game players from its returned frozen roster plus the host. `ProcessMultiplayerSessionOwner.kt:260–289` installs only successful `room.closeAdmissions()` results. The actual transport close (`P2pKitRoomTransport.kt:2715–2773`) first rejects pending admission/resume barriers, sets `admissionsClosed`, removes disconnected lobby seats, and returns connected admitted members. Admission commit added each member to `previouslySeenPlayerIds` atomically (`2967–3007`, especially2982–2983).
3. `MafiaMultiDeviceHostFlow.kt:99–112` passes the process-owned room to `MafiaHostRuntime`. `MafiaRetainedMultiplayerRuntime.kt:44–108` installs the real bridge with `reconcileRoomTopology=true`, `requireStartHandshake=true`, and the frozen players. `MafiaHostRoomBridge.kt:75–105` excludes the host from remote identities and observes actual membership.
4. `MafiaHostRoomBridge.kt:279–349` records only remote disconnections and schedules expiry only while the canonical marker exists outside PostGame. Expiry and rejoin completion share `recoveryTransitionMutex`. Expiry takes ownership of the exact job, removes/cancels the in-flight rejoin, rechecks the canonical marker, and calls `retireAndContinue`. Completed rejoin checks exact job ownership before clearing the marker/deadline (`393–432`).
5. **`P2pKitRoomTransport.kt:2781–2851` has no connected-state rejection.** Its only explicit failure guards are host identity(2784), room already left(2789), admissions not closed(2793), or never-admitted identity(2799). A current frozen remote seat in a live host room does not satisfy these guards. SHA-256 of this source: `6f2584af596bb437007f856b5c6313b88f864855a31f77085052c3394ca4a263`.
6. Retirement removes the adopted session, pending transaction, both ready barriers, tracked-but-not-yet-indexed sessions, reservations, membership, deadline, and credential under one state mutex (`2804–2831`); it wipes the removed credential and performs noncancellable best-effort session closure (`2840–2849`), then returns Success. Physical `Connected` does not prevent revocation. A resume commit still in flight loses its reservation/pending/credential identity checks (`2512–2553`); a ready waiter loses its exact barrier/session checks (`2580–2607`).
7. Bridge `446–480` changes the canonical game only after that successful revocation. A revocation followed by a reducer invariant failure terminates Cancelled, rather than pretending to drop the seat. Reducer `MafiaReducer.kt:850–921` requires the canonical marker and ends the frozen session; it does not silently continue a changed roster. The `previouslySeenPlayerIds.clear()` path at transport3526 is guarded by terminal `left=true` before cleanup (`3488–3500`), not a normal reconnect transition.

The default `LocalRoom.retireDisconnectedMember` returning Unauthorized (`LocalRoom.kt:75–91`) is intentional fail-closed behavior for implementations without revocation. The shipping override above, not that default, governs this path.

## Counter-evidence and tests inspected

- `MafiaAuthoritativeLifecycleTest.kt:309–403` tests handshake failure plus grace expiry, late ready/ack non-revival, explicit early retirement, and injected retirement failure. The fake room at734–783 permits arbitrary `retirementError`; its injected `TransportFailure` is not a connected-state guard in production. A mock capable of returning a hypothetical error does not establish production reachability.
- `P2pKitRoomTransportLifecycleTest.kt:1265–1356` directly exercises the real room implementation with fake P2pKit sessions: retirement is idempotent/revokes credential replay; retirement while a resume offer is suspended returns Success and prevents ResumeCommitted. These assertions were read, **not executed for this validation**, and are not physical-LAN proof.
- Physical-session `Connected` checks exist in admission/resume commit/ready logic, but not in the retirement function. Those checks do not support the original hypothesized refusal.

## Disposition and limits

Keep M-C02 in the rejected-candidate register with this source-level rejection. Do not modify production to address a nonexistent predicate. Preserve the existing fail-closed retirement behavior and ownership checks. Separate tests of unexpected transport errors or real-device lifecycle behavior remain useful but do not turn this candidate into a confirmed application defect. This report does not certify LAN performance, socket-close timing, every race, or overall Mafia correctness. Root owns all build/device/cleanup execution; this review created only compact audit evidence.
