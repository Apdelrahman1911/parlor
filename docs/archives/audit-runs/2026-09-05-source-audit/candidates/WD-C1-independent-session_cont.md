# WD-C1 — Independent validation

Original finder: `/root/whodunit_cont`. Independent validator: `/root/session_cont`.
Classification: **CONFIRMED DEFECT by deterministic reachable source-level proof**.
Suggested severity: **Medium** (unrequested repeated commands, invalid-action notices, waiting-screen churn; escalation to impaired progress is timing-dependent).
Baseline: commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. File hashes and independently reopened line ranges: `coverage/reviews-session_cont.jsonl`.

## Complete path and expected/actual behavior

All paths below are relative to `/Users/abdelrahman/Projects/parlor/`.

The applicable implementation is commonMain and is compiled into shipping Android/iOS. `composeApp/build.gradle.kts:136` includes Whodunit; its `build.gradle.kts:8-24` wires session, networking, and Compose. The peer game entry is called by `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitPeerSessionFlow.kt:407-431` after accepted start/content loading. No test-only or obsolete route is involved.

1. `.../ui/flow/WhodunitGameFlow.kt:1340-1384` reads a complete own-player snapshot, requires an initial authoritative snapshot, and conditionally composes `PeerPhaseRouter` only when command progress is Idle and public state is not paused.
2. `.../ui/flow/WhodunitPhaseRouter.kt:137-159,329-348` unconditionally sends its seat's Intro or Briefing acknowledgement from `LaunchedEffect(phase,selfPlayerId)` whenever this subtree enters composition. It does **not** consult `introAcknowledged`/`briefingReady`.
3. `.../multiplayer/WhodunitRetainedMultiplayerRuntime.kt:161-172` provides a retained `PartyAwareSession` with MultiDevice mode. `shared/session/.../party/PartyAwareSession.kt:53-81` only performs automatic pending-ack logic for local modes, so multiplayer passes through. There is no acknowledgement cache here. `ShadowSessionController.kt:59-60` forwards the action without speculative state.
4. `.../multiplayer/WhodunitPeerRoomBridge.kt:187-209` encodes every submitted action; `shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/AuthoritativeSessionCoordinator.kt:1449-1505` allocates a fresh command ID and publishes Awaiting before its transport send. A subsequent UI frame observes non-Idle and removes the entire router (`WhodunitGameFlow.kt:1369-1384`).
5. Host coordinator `:1099-1165` checks protocol, membership, command ID, client sequence and expected revision. The correctly authenticated own-seat acknowledgement is authorized (`WhodunitActionAuthority.kt:54-61,85-95`). `WhodunitReducer.kt:262-284` adds the ready seat once but **does not advance phase**.
6. The first acknowledgement changes state and returns Applied; host snapshot carries that readiness revision. Peer coordinator `:1645-1684` changes progress to Resolved; the persistent flow collector `WhodunitGameFlow.kt:1291-1323` acknowledges the outcome. Coordinator `:1520-1532,1625-1640` returns to Idle once the matching authoritative revision is present. No parent state records that the entry effect already ran.
7. Since the host is still reading the intro/briefing, the same phase is re-composed. Effect keys only preserve an effect while its remembered node survives; they do not suppress a fresh entry after removal. It sends another new command ID.
8. The reducer's set-membership guard makes repeated acknowledgements no-ops (`WhodunitReducer.kt:270-271,282-283`). Host bridge converts unchanged receipts into `CommandApplication.InvalidAction` (`WhodunitHostRoomBridge.kt:268-274`). Host returns that outcome without a new revision. The peer displays an error toast, then acknowledges Idle (`WhodunitGameFlow.kt:1312-1322`). The router returns and submits again.

Expectation is established by executable readiness semantics: a seat's acknowledgement is recorded in a set; duplicates cannot change state; these waiting screens require no repeated action. Network deduplication applies to command IDs, not semantic equality, and therefore does not stop fresh commands on every remount.

## Deterministic reproduction schedule (not executed)

Use a legal four-player Whodunit lobby/content combination and enter PublicIntro on a peer, with the host not pressing Continue. Allow a UI frame after every outbound acknowledgement before delivering the corresponding host result (e.g. controlled 100-ms frame delay). Deliver the first Applied result and authoritative snapshot; allow recomposition; observe a second fresh acknowledgement. Deliver InvalidAction; allow the waiting state then Idle to render; observe the third fresh acknowledgement. Repeat. RulesBriefing has the same source path. This schedule uses only legitimate same-room frames; no malformed input or old snapshot is required.

This proves a repeating reachable transition, not that every physical network will show an infinite loop: sufficiently fast round trips may conflate non-Idle away before composition, and an explicit host phase change ends these effects. No physical-device, runtime screenshot, or UI-test execution is claimed.

## Counter-evidence considered

- A key prevents a restart from ordinary recomposition, but **not removal followed by entry**. Exact library source confirms this (research below).
- `introAcknowledged`/`briefingReady` are present in public/player projection; `WhodunitProjectionPolicy.kt:42-67` does not redact them. The effect could inspect them but currently does not.
- Host advancement is gated on explicit `introAdvanceRequested`/`briefingAdvanceRequested` and button callbacks (`WhodunitPhaseRouter.kt:180-235`); a valid scenario keeps the phase unchanged.
- A pending command blocks competing submission; that guard is cleared after resolved acknowledgement. Every subsequent effect therefore receives a fresh command ID, not a duplicate ledger hit.
- Unmount cancellation may interrupt the first send. Coordinator `:1483-1518` deliberately retains ambiguous outcome recovery. A schedule in which the send has settled before the UI frame avoids this complication entirely; neither path supplies an entry-ack guard.
- Invalid-action toast insertion is synchronous and coalesces duplicate text (`ParlorToastHost.kt:75-88`), but it does not block outcome acknowledgement or router remount. Coalescing limits visible duplicates, not traffic.
- Paused state and transport failures can stall/terminate the loop. They do not prevent the valid active-room schedule. Protocol validation and first-valid reducer rules are functioning correctly and should not be weakened to hide the UI defect.
- Pass-and-play has no peer-command conditional subtree. Mafia has no inspected equivalent Intro/Briefing auto-entry action; no Mafia defect is asserted from this proof.

## Exact-version reference and limitations

Accessed 2026-09-05: official Maven Central `org.jetbrains.compose.runtime:runtime:1.10.3` module metadata depends on `androidx.compose.runtime:runtime:1.10.5`. Official Google Maven sources `commonMain/androidx/compose/runtime/Effects.kt:269-356` show `LaunchedEffectImpl.onRemembered()` launches, `onForgotten()` cancels, and two-key `LaunchedEffect` creates that implementation with `remember(key1,key2)`. URL/hash receipts and excerpt are in `evidence/session_cont-research.json`; downloaded JARs were removed after preserving the relevant source excerpt.

## Suggested remediation and tests (not implemented)

Keep phase content/effect ownership alive beneath a non-interactive waiting overlay, and/or guard entry acknowledgement against the authoritative seat-readiness field and retained phase/generation identity. Ensure only one intended acknowledgement can be active, while a definitively rejected stale request remains recoverable. Avoid caching across replay without a generation/phase check. Add a Compose test with delayed transport, first Applied acknowledgement followed by repeated frames in the same phase, and assert no second command. Cover a return from pause, UI root recreation, rejoin, replay, stale rejection, and normal host progression in both intro and briefing. Preserve host-only reduction, own-seat authority, snapshot visibility, and protocol 4.2.
