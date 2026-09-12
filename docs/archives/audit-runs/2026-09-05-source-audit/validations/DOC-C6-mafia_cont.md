# Independent validation — DOC-C6: peer countdown comment contradicts current UI routing

## Classification and identity

**DOCUMENTATION MISMATCH — Low.** The independently verified issue is one stale source-comment assertion. It is **not a missing-countdown feature request, multiplayer synchronization defect, or host-authority defect**.

- Finder: `/root`; independent validator: `/root/mafia_cont`.
- Checkout: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.
- Exact hashes/read ranges: `validations/DOC-C5-C6-mafia_cont-source-hashes.json`; `coverage/reviews-mafia_cont.jsonl`.
- Narrow source-level proof; no app/device run, build, code modification, or protocol change. Android/iOS/common Desktop rendering shares this code; local pass-and-play is the counterexample sibling, not an affected peer topology.

## Exact location, expectation, and impact

`/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitPhaseRouter.kt:868–869` says **“Peers render the countdown from the host's snapshots instead.”**

Current peer discussion UI renders a waiting-for-host screen, with no countdown input or timer-rendering child. The host/local discussion screen renders the timer. Documentation should describe that actual split. The concrete consequence is misleading maintainer/test guidance about what peers display; it does not establish that peers are required to display a countdown. Severity is Low because this assertion cannot change execution and correct adjacent router structure is readily inspectable.

## Complete narrow source proof

1. An active discussion is reachable: `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitPhaseRouter.kt:884–900` submits `StartDiscussionTimer` after a clue. `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/reducer/WhodunitReducer.kt:506–532` accepts the authored duration in a Round with an existing clue, `VoteState.Idle`, and no timer, and installs the public timer without changing that Round/Idle state.
2. The public data actually contains the timer: `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/state/WhodunitState.kt:30–48,134–140`. `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/projection/WhodunitProjectionPolicy.kt:42–67` preserves public fields including this timer while redacting host/private/vote-target data. Receiving authoritative timer data and rendering a countdown are distinct claims.
3. The production peer flow at `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt:1224–1387` attaches the retained peer runtime, collects its own single-revision private/public projection, gates initial snapshots/command progress/host pause, and otherwise calls `PeerPhaseRouter` (lines1376–1384). There is no alternate countdown in that flow's rendering branches.
4. `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitPhaseRouter.kt:132–159` requires a non-host and the peer's own projection. Its Round branch at378–411 renders a ballot only for Collecting and the public innocence outcome only for Resolved/nonkiller; the reachable active discussion's Idle state enters404–410. That call supplies only an eyebrow, title, body, waiting hint, and modifier to `PeerWaitingForHostScreen` — no timer.
5. `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/peer/PeerWaitingForHostScreen.kt:29–70` renders exactly those text inputs in a scrolling column. It neither reads a session/timer nor calls a timer-rendering component. Current text reinforces that role: `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/composeResources/values/strings.xml:151,156–157` says “The host is leading this round. Watch the table and contribute when prompted.” Arabic equivalents are `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/composeResources/values-ar/strings.xml:151,156–157`.
6. The host/local sibling is explicitly separate: `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitPhaseRouter.kt:98–129,169–179,265–272` guards the host route and sends its Round to private `RoundSegment`. Only that segment calls `DiscussionScreen` at894–900. `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/round/RoundScreens.kt:134–165` passes the actual remaining/total/paused values to `TimerRibbon`.
7. The comment's **authority motivation remains correct**. `WhodunitPhaseRouter.kt:861–872` requires nonnull `hostState` before launching the tick loop. `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/timer/DiscussionTickerLoop.kt:38–62` submits ticks/expiry against live session state. `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/authority/WhodunitActionAuthority.kt:28–52,85–95` classifies those actions as HostOnly; `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitHostRoomBridge.kt:245–275` applies that permission guard before the reducer submission. This evidence supports retaining the guard, not weakening it.

## Counter-evidence and rejection of a code-defect interpretation

- Peer projections preserve public timer values, so “from snapshots” is plausible as a data-flow statement. It cannot make the affirmative **render** claim true: the peer's reachable branch does not consume those values.
- Host/local `DiscussionScreen` does render the countdown. `RoundSegment` is private and called from the separately guarded host route; it is not the current peer discussion tree.
- Collecting/resolved vote branches are intentionally interactive/read-only peer exceptions, not a timer-rendering route. Session pause replaces them with a host-paused banner; pending commands and first-snapshot loading use reconnecting surfaces, not a timer.
- Current waiting text and router roles are consistent with the implemented host-led table experience. No authoritative product requirement for a peer countdown was established. The stale comment must not be used to invent one.

## Reproduction and suggested remediation

A runtime confirmation, if separately authorized, would start a supported Whodunit LAN room, complete role readiness, reveal a clue and begin the discussion: the host sees `TimerRibbon`; a connected idle peer sees the waiting text. **This validator did not execute that device scenario.** The deterministic branch and child-rendering proof above is the evidence.

Change only the inaccurate sentence to describe host/local ticking and the current waiting peer UI (or remove the peer-rendering claim). Preserve host-only reducer ownership and the existing projection/authority guards. No game-rule, timer, protocol4.2, snapshot, or UI feature change is required. Review the peer and host route branches against the new wording; any future product-authorized peer countdown should receive its own UI/authority/projection regression tests rather than being smuggled into a documentation correction.
