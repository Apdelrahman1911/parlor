# DOC-C2 — Room lifecycle diagram conflates game completion and room termination

> **Final independent disposition (2026-09-05): DOCUMENTATION MISMATCH — Low; lifecycle clarification only.** Validator `/root`; see `validations/DOC-C2-root.md`. The original candidate text below is preserved as the pre-validation record; its pending language is superseded by this disposition.

**UNCONFIRMED — independent documentation adjudication pending.** Finder `/root/whodunit_cont`; proposed Low DOCUMENTATION MISMATCH or clarification-only. Baseline main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.

Document: `/Users/abdelrahman/Projects/parlor/docs/PRODUCTION_ARCHITECTURE.md:129–148`, SHA256 `0662f57345e04990baa76f45328779c525d780544585b4bd2b2b4b2dbc1536b3`, says terminal state is a SessionEnded envelope and diagrams Playing -> Ended when the game completes. This appears to imply automatic room teardown at natural game completion.

Source-level counterpath (base `/Users/abdelrahman/Projects/parlor/`):

- `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/reducer/WhodunitReducer.kt:988–1063`: acknowledge natural Reveal -> PostGame; valid BeginReplay in intact PostGame deterministically reassigns and returns PublicIntro without changing the room.
- `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitPhaseRouter.kt:312–319,453–459`: host has Replay in PostGame; peers wait for host in the same flow.
- `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt:1034–1048,1143–1153`: host routes with the retained bridge and only library exit calls finalLeave. `ui/flow/multiplayer/WhodunitHostRoomBridge.kt:170–205` publishes host mutations normally; explicit terminate sends the terminal envelope.
- `ui/flow/multiplayer/WhodunitRetainedMultiplayerRuntime.kt:47–134`: owns the controller/bridge and delegates termination only when asked by its process owner, not on canonical PostGame observation.
- Mafia similarly renders its PostGameScreen with an explicit onExit callback (`MafiaMultiDevicePhaseRouter.kt:187–192`), not an immediate transport end; no Mafia replay feature is inferred.

The ordinary result/post-game transition is thus distinct from SessionEnded/room destruction. Calling transport end on natural game result based on the diagram would break Whodunit's intended same-room replay. **The code is not proposed as defective**; the recommendation is to label game result/post-game separately from logical-room termination and document which explicit exit sends SessionEnded.

Counter-evidence: the diagram may be deliberately high-level and “game completes” could be shorthand for a later user-confirmed finished room/session. In that reading this is ambiguity, not a confirmed defect. Hosts with dropped/disconnected seats cannot replay; the reducer explicitly rejects that, so the counterpath requires an intact normal completion. No runtime/network reproduction was executed. Keep this candidate outside confirmed counts unless a separate validator agrees the current wording materially misstates the lifecycle.
