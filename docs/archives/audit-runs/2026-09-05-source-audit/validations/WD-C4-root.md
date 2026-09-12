# WD-C4 — independent disposition

**UNCONFIRMED — BLOCKED (modal-clock product policy).** Finder `/root/whodunit_cont`; independent validator `/root`. No defect severity or additional positive finding is approved. Baseline `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.

## What is established

All paths are beneath `/Users/abdelrahman/Projects/parlor/`; exact hashes and actually reopened ranges are in `coverage/reviews-root-closeout.jsonl`.

- `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitHostSessionFlow.kt:140–361` keeps the room/route owner outside `leaveConfirmationOpen`; lines249–258 replace, rather than cover, the game subtree. Stay only clears the boolean. `leaveRoute` runs on separately confirmed exit, not on opening this question.
- `.../ui/flow/WhodunitGameFlow.kt:754–950` independently shows the local controller, canonical persistence collector and session identity outside the confirmation conditional. Local Stay also changes only the boolean.
- `.../ui/flow/WhodunitPhaseRouter.kt:827–938` owns the sole game ticker effect in the removed RoundSegment. `.../ui/timer/DiscussionTickerLoop.kt:1–62` delays and decrements, rather than computing a wall-clock deadline. `LaunchedEffectImpl.onForgotten` in the independently reopened pinned AndroidX runtime1.10.5 excerpt cancels that effect.
- The complete `.../ui/flow/multiplayer/WhodunitRetainedMultiplayerRuntime.kt:1–189` contains no replacement ticker. `shared/session/src/commonMain/kotlin/com/parlor/session/multidevice/ProcessMultiplayerSessionOwner.kt:216–250,322–370` retains the runtime/scope independently of this presentation branch.
- Reducer `WhodunitReducer.kt:506–584,1067–1094` distinguishes session/per-timer pause, but neither exit-confirmation callback sends these actions. After the removal has settled, remaining seconds are unchanged and both pause flags can stay false. Stay remounts a new interval from that value. No runtime/device reproduction was performed for this candidate.

## Why this is not approved as a defect

The observed freeze alone does not settle whether an exit question should count as discussion time. The ticker's own cancellation contract expressly permits its composition owner to cancel it. An active timer value describes reducer state; no examined executable guard mandates continuous wall-clock counting through every modal interruption. Shared `SessionExitControls.kt:142–207` deliberately replaces private pixels/semantics, while its copy/policy concerns save/leave authorization, not elapsed-time semantics.

Freezing a host-controlled party game while the host considers exiting is a plausible intended policy. It may deserve an explicit canonical pause owner, but asserting that requirement without a product decision would manufacture a rule. The alternative policy, keeping the game-specific ticker running outside private presentation, is also viable. Neither is authorized here.

Counter-evidence: peers actually show a waiting screen during discussion (`WhodunitPhaseRouter.kt:378–411`), not the numeric countdown. Host/local Stay resumes; no permanent stall, wrong winner, peer reducer, secret disclosure or navigation bypass is established. Mafia timers are intentionally unsupported. Explicit Pause/transport suspension remain different, modelled paths.

The broader tab-stranding lead is rejected: all172 lines of `composeApp/src/commonMain/kotlin/com/parlor/app/AppNavigation.kt` were reopened; `selectTopLevel:104–109` and `navigateBack:139–151` refuse leaving active Game, and App renders the bottom bar only for top-level routes. No Settings tab can silently strand this game using the inspected public UI.

## Continuation

Ask the product/game owner to specify time behavior during a foreground Leave confirmation. Then add a production-composable deterministic test for local and retained-host open/wait/Stay, existing pause ownership, a tick at the cancellation boundary, repeated rapid requests, lifecycle interruption and confirmed exit. Preserve the opaque privacy replacement and exact protocol4.2; do not put Whodunit clock rules into shared networking.

This is a recorded source behavior plus an unresolved requirement, **not** an implemented fix, accepted timer bug, physical-device result or proof that every related layout/lifecycle flow is correct. Only audit evidence was written; no build/process was started by this source validation.
