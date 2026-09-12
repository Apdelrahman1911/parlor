# WD-C4 candidate — exit confirmation implicitly freezes an unpaused discussion timer

**Status:** UNCONFIRMED — independent validation and intended-policy adjudication pending. Do not add to confirmed counts yet.  
**Finder:** `/root/whodunit_cont`; **independent validator:** requested from `/root`.  
**Proposed severity if accepted:** Low (discussion duration/clock-state consistency; no demonstrated secret leak, winner corruption, or permanent deadlock).  
**Source:** `main`, `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked files unchanged. Exact absolute paths, hashes, and one-based ranges: `evidence/WD-C4-source-hashes-whodunit_cont.json`.

## Deterministic reachable behavior

1. In a supported six-player bundled Whodunit Classic or Elimination session, proceed through briefing/roles, reveal a clue, and start the authored discussion timer. `WhodunitReducer.kt:506–532` creates an unpaused timer with authored seconds; `WhodunitPhaseRouter.kt:827–873` mounts the sole production ticker in `RoundSegment`'s `LaunchedEffect(timerId, session)`.
2. During an unpaused discussion, the host taps the visible **Leave** affordance (or issues guarded Back). `WhodunitHostSessionFlow.kt:236–261` sets only `leaveConfirmationOpen = true` and replaces the `else` branch containing `WhodunitMultiplayerHostFlow` at lines345–357 with the confirmation.
3. Removing the child removes `RoundSegment`. Pinned Compose effect semantics cancel its job (`LaunchedEffectImpl.onForgotten`; exact-version research below). There is no second ticker in the retained `WhodunitHostRuntime` (complete189-line file read), nor another production `TimerTicked`/`TimerExpired` producer found by call-site inspection. `DiscussionTickerLoop.kt:43–60` holds only a delayed decrement, not an elapsed-time deadline.
4. The canonical multiplayer session/bridge does **not** end or pause. Runtime scope belongs to `ProcessMultiplayerSession` (`ProcessMultiplayerSessionOwner.kt:216–238,322–359`); the confirmation callback does not call `leaveRoute`, `Pause`, `PauseDiscussionTimer`, `beginExit`, or change transport visibility. Those terminal effects occur only after the separate confirmed-exit callback (`WhodunitHostSessionFlow.kt:161–175`). Consequently an otherwise connected foreground room retains `public.paused == false`, `timer.paused == false`, and the same remaining seconds while the host considers the confirmation.
5. Choosing **Stay in game** sets only the boolean false (`WhodunitHostSessionFlow.kt:252`), reattaches the retained runtime (`WhodunitGameFlow.kt:994–1037`), and starts a fresh one-second delay from unchanged remaining seconds. Time spent in the confirmation is not counted. Waiting beyond the entire remaining duration in the confirmation still does not expire the discussion.
6. The same implicit freeze occurs in pass-and-play: its controller stays remembered outside the confirmation conditional (`WhodunitGameFlow.kt:754–854`); lines899–907 replace the router at922–932. Staying remounts the timer without a canonical Pause/Resume pair.

This is source-level proof of the freeze, **not** a performed UI/device reproduction. A cancellation-boundary tick may already have committed; once the effect has been forgotten no further ticks occur until remount. Use settled composition and an interval greater than one second for a test.

## Expected behavior and unresolved product decision

The ticker implementation/tests distinguish running and paused clocks: `DiscussionTickerLoop.kt:12–27,43–60`, `TickerAndRerollTest.kt:182–255`, explicit reducer Pause/Resume (`WhodunitReducer.kt:1067–1094`), and retained lifecycle bridge pause (`WhodunitHostRoomBridge.kt:113–136,489–507`). A real-time **unpaused** timer should not silently gain arbitrarily long extra duration merely from the host opening an exit question. A coherent implementation could either keep the host-owned clock alive or deliberately pause it with truthful canonical state, preserving pre-existing pause ownership.

However, no authoritative product contract was found explicitly choosing whether an exit confirmation should allow time to pass. Freezing a party game while its host considers leaving could be intentional. The shared confirmation intentionally removes private presentation (`SessionExitControls.kt:143–145`), and its copy only promises Stay/Exit transaction behavior (`shared/design-system/.../values/strings.xml:3–14`). This uncertainty must be adjudicated; do not manufacture a requirement that every host modal must run the clock. The stronger, source-proven claim is **a clock freeze outside the canonical pause model**, not a violation of an explicitly documented modal rule.

## Counter-evidence and scope limits

- The broader tab-stranding hypothesis is rejected: `AppNavigator.selectTopLevel` refuses a non-top-level Game route, `navigateBack` refuses Game, the app bottom bar is hidden in Game, and game Back delegates into its confirmation. No ordinary Settings-tab path was found that strands an unpaused timer in a hidden stack.
- Explicit Pause and transport suspension deliberately freeze the canonical clock; those are not defects. The lifecycle bridge submits Pause and resumes only its owned pause; no such action accompanies this confirmation.
- iOS inactive privacy masking leaves `ComposeView` mounted beneath an opaque layer (`ContentView.swift:14–49`), so that masking is not the same unmount path. This does not prove physical background timing.
- Peers do **not** presently render the numeric discussion countdown: `WhodunitPhaseRouter.kt:378–411` renders a waiting screen outside voting/outcome. Do not claim a visibly frozen numeric peer timer. They merely wait longer for host progression; canonical timer flags transmitted in snapshots remain unpaused.
- The host already owns progression, can explicitly pause, and can use Move On. This is not a peer authority bypass or unrecoverable stall. Stay restores ticking.
- Mafia intentionally has no timed rounds; no analogous Mafia timer claim is made.
- Existing ticker tests invoke the loop directly. No test was run in this follow-up, and those tests do not mount/unmount the production confirmation.

## Suggested verification/remediation (not implemented)

- First decide and document modal clock policy. Then keep the ticker at a game-specific lifecycle owner outside presentation replacement, or introduce an explicit pause token/ownership policy for confirmation. Do not weaken the opaque privacy replacement, start peer reducers, or put Whodunit timer rules in generic shared session infrastructure.
- Add a production-composable test that starts a valid discussion, opens confirmation, advances deterministic time, stays, and checks remaining seconds plus pause flags against the chosen policy. Test local and retained-host paths, already-paused state, cancellation near a tick, repeated open/stay, transport suspension/resumption during confirmation, and confirmed exit cleanup.
- No protocol-version change or real-device result is asserted; any proposed change should preserve exact4.2 and snapshot pause semantics.

## Exact pinned effect-semantics research

Research already captured in `evidence/session_cont-research.json` and reopened here; authoritative excerpt reopened at `evidence/androidx-runtime-1.10.5-LaunchedEffect-excerpt.txt`. Access2026-09-05:
- `https://repo.maven.apache.org/maven2/org/jetbrains/compose/runtime/runtime/1.10.3/runtime-1.10.3.module` maps pinned CMP runtime1.10.3 to AndroidX runtime1.10.5.
- `https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime/1.10.5/runtime-1.10.5-sources.jar`: `Effects.kt:282–284` cancels in `onForgotten`; two-key `LaunchedEffect`343–355 remembers that observer and documents cancellation on leaving composition.

Source semantics establish effect lifetime; they do not substitute for device execution or decide intended game policy.

## Hygiene

Source reads and audit-evidence writes only. No Gradle/Xcode task, simulator, server, production edit, commit, or process termination performed by this reviewer. Root owns the shared build lane.
