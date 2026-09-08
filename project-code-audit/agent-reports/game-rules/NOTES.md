# Game-rules workstream notes

Auditor: game-rules. Tree: on-disk 2026-09-01. No production edits. No commits.
No `*.md` outside `project-code-audit/` was read.

## Method

1. Enumerated every `domain/**`, `snapshot/**`, definition, play-mode policy, content validator, and related desktopTest/commonTest that claims reducer/authority/win/codec behavior.
2. Read production reducers and validators in full (WhodunitReducer 1231, MafiaReducer 1060, WhodunitStateValidator 1082, MafiaSnapshotRecovery 524, MafiaObservableStateValidator 371).
3. Reconstructed state machines from `when` branches and fail-closed guards, not comments or test names.
4. Read test *assertions* for vote/tie/win/authority/codec/snapshot. UI/layout/multidevice-transport tests listed as skipped.

## Highest-signal contradictions

- Elimination `OpenVote` is legal on any `Round + Idle + timer==null`, including before a clue and after a clue with no discussion. Pre-clue Collecting is rejected by `validatePhaseShape`. Post-clue skip-discussion is validator-legal.
- `killerWins(SurvivedToFinalTwo)` writes `Resolved(killer, wasKiller=true)`; `validateCanonicalKillerWin` requires `!wasKiller` and last-eliminated innocent. Live play reaches SurvivedToFinalTwo only via `resolveVote`.
- Mafia `MafiaPublic` kdoc says disconnect pauses the game. Reducer only mutates the set. No pause flag exists.
- `WhodunitPublic.droppedPlayers` kdoc says new reducers never add entries. `continueWithoutPlayer` adds the seat then ends.

## What is solid

- Both reducers are pure, topology-agnostic, host-owned. Illegal actions are no-ops (same state, no events).
- Whodunit Classic vs Elimination phase machines, sequential secret ballot, one revote, second-tie outcomes, clue policy, snapshot envelope, peer projection redaction.
- Mafia simultaneous night, kill/inspect/protect/suspect, one Mafia kill revote, day-vote tie settings, WinCheck parity, fail-closed snapshot recovery.
- Codecs reject retired actions, oversized payloads, malformed UTF-8. Mafia snapshots require canonical bytes + `isValidRecoveryState`.

## Test false-confidence (do not trust names)

- `CluePolicyTest` drives `RevealNextClue` on states with empty `privatePerPlayer` / empty seat map (validator-illegal).
- `WhodunitReducerProductionGuardsTest.elimination_final_two_counts_only_active_non_eliminated_players` plants `droppedPlayers` inside Collecting (validator forbids dropped in active play) and never calls `requireValid`.
- `WhodunitActionAuthorityTest.host_only_actions_reject_peers` omits `AdvanceFromCharacterReveal`.
- `MafiaReducerTest.mark_player_disconnected_and_reconnected_round_trips` only asserts set membership, not freeze.
- Several Mafia/Whodunit tests use `validatedWhodunitCaseForTest` which bypasses `WhodunitPayloadValidator`.
