# DOC-C2 — independent root adjudication

**DOCUMENTATION MISMATCH — Low (lifecycle clarification), not an application defect.**
Finder `/root/whodunit_cont`; validator `/root`. Baseline main
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`.
Absolute source base `/Users/abdelrahman/Projects/parlor/`; hashes in `pending-root-source-hashes.json`.

Reopened `docs/PRODUCTION_ARCHITECTURE.md:115–155`: line131 describes explicit SessionEnded and
line148 diagrams natural game completion as Playing->Ended in the room lifecycle.

Independent complete counterpath:
- `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/reducer/WhodunitReducer.kt:974–1079`
  allows intact Reveal->PostGame->BeginReplay, clearing ballots/private readiness and deriving the next seed.
- `.../ui/flow/WhodunitPhaseRouter.kt:299–326,434–462` exposes the host Replay action; peers wait in PostGame.
- `.../ui/flow/WhodunitGameFlow.kt:986–1058,1106–1165` routes host actions through a retained runtime;
  explicit library exit calls finalLeave. Its separate local completed-snapshot policy at760–860 is not room termination.
- `.../ui/flow/multiplayer/WhodunitRetainedMultiplayerRuntime.kt:1–189` retains session/bridge; termination
  is an explicit method, not an effect on PostGame.
- Reopened bridge105–330 (including the formerly truncated109+ range). submitHostAction170–187
  publishes mutations normally; explicit terminate190–205 sends coordinator.end. Snapshot278–289
  remains public+own-private. Runtime/lifecycle collectors do not end on a normal game result.
- `shared/session/.../ProcessMultiplayerSessionOwner.kt:783–880` invokes runtime.terminate and room.leave
  from physical-session close. It is not a game-phase observer. Mafia's phase router172–199 similarly
  keeps PostGame displayed until onExit; no Mafia replay is inferred.

Counter-evidence: a high-level diagram can use “completes” to mean a later user-finished room, and an
explicit exit really does end it. Nevertheless this diagram names a room terminal envelope, omits
postgame/replay, and labels the transition as game completion. That is materially ambiguous for an
implementer following this current architecture contract. Narrowly classify as documentation drift:
show result/PostGame remaining in the room and the explicit exit that terminates it. Do not alter
working room/replay code to match the shorthand. The counterpath requires intact seats (the reducer
correctly rejects replay with dropped/disconnected seats). No LAN/runtime execution is claimed.
