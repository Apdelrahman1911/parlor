# Mafia — reconstructed rules (from code)

Source of truth: `MafiaReducer`, `MafiaSessionRules`, `WinCheck`, `NightResolution`, `VoteResolution`, `MafiaSettings`, `MafiaObservableStateValidator`, `MafiaSnapshotRecovery`.

## Mode, roster, settings

One shipping mode: `classic` (`MafiaIds.ClassicModeId`). Players **5–16**. Unique ids, seats `0..n-1` matching list order, distinct `RoomInputPolicy` names. Other mode ids rejected at `createInitialState`.

Initial settings = `MafiaSettingsPresets.forPlayerCount(n)`:

| n | Mafia | Det | Doc |
|---|---|---|---|
| 5 | 1 | 1 | 0 |
| 6 | 1 | 1 | 1 |
| 7–9 | 2 | 1 | 1 |
| 10–12 | 3 | 1 | 1 |
| 13–16 | `n/4` ≥1 | 1 | 1 |

Validation before commit (`ApplySettings` / `StartGame` / `ConfigureAndStart`): ≥1 Mafia, Det/Doc in 0..1, ≥1 Civilian remainder, Mafia **strict minority** at start, `maxRevotes` in 0..3, **any non-null timer duration is invalid** (`TimersNotSupported`). Defaults: revealRoleOnDeath=true, no self-heal, no consecutive doctor protect, no detective self-inspect, no self-vote, no mafia-on-mafia, `voteTieBehavior=REVOTE_TIED_ONLY`, `maxRevotes=1`, `mafiaKillTieBehavior=REVOTE`.

`ConfigureAndStart` validates **incoming** settings then assigns; rejected config does not fall through to old settings.

**Verdict:** implemented and validated (`MafiaSettingsValidationTest` all reject branches + presets 5–16; reducer apply/start tests).

## Roles

`Role`: Mafia (team Mafia), Detective, Doctor, Civilian (team Town). Civilians = remainder. Assignment: `RandomSource.seeded(hostOnly.randomSeed).shuffled(players)` then take mafia, detective, doctor, rest civilian. Mafia `knownTeammates` = other Mafia; Town empty. Full map only in `hostOnly`. Living public `revealedRole` is always null during play unless `revealRoleOnDeath` and the seat is dead. PostGame copies every role public.

**Verdict:** implemented and validated (`RoleAssignmentTest`, recovery role-count checks).

## Phase machine

```
Setup
  --StartGame | ConfigureAndStart--> RoleAssignment
  --all active roleAcknowledged + AdvanceFromRoleAssignment--> Night(day=1, coordRound=1)
Night
  --all active-alive submitted (+ detective result acked if any) + ResolveNight-->
      if r1 tied AND REVOTE: Night(same day, coordRound=2)
      else if win: PostGame
      else NightAnnouncement(day)
NightAnnouncement
  --all active-alive nightAcknowledged + OpenDiscussion--> Discussion(day)
Discussion
  --OpenVote--> Voting(day, revoteRound=0)
Voting
  --all ballot acted + CloseVote-->
      Resolved elim → if win PostGame else VoteAnnouncement
      Tied + revote allowed → Voting(day, revote+1) with new candidates
      Skipped (tie policy / max revotes / all abstain) → VoteAnnouncement
VoteAnnouncement
  --if winner already set: PostGame
  --else all active-alive voteAcknowledged + AdvanceFromVoteAnnouncement--> Night(day+1, 1)
EndGame from any non-PostGame → PostGame
```

Night actions are **simultaneous** (no voter index). Day votes are also simultaneous (any ballot member may cast until they have acted). Contrast Whodunit sequential ballots.

**Verdict:** implemented and validated (reducer + FullGameDrive + recovery after steps).

## Night order / actions

Reducer does **not** sequence roles on the table. All living active seats must submit once before `ResolveNight`. Role gates:

| Action | Who | Target rules |
|---|---|---|
| SubmitMafiaKillVote | Role.Mafia | not self; if `!mafiaCanTargetMafia` not another Mafia; target alive+active or null skip |
| SubmitDoctorProtect | Role.Doctor | self only if `doctorCanSelfHeal`; not previous protect if consecutive off |
| SubmitDetectiveInspect | Role.Detective | self only if `detectiveCanInspectSelf`; result written **at submit** (even if later killed) |
| SubmitCivilianSuspicion | Role.Civilian | not self; flavor only (`lastSuspicion`) |

Duplicate night submit is no-op (first wins). Town night flags are **not** cleared on Mafia-only revote; Town already submitted remains submitted (`isNightReadyToResolve` still holds).

ResolveNight tally = living Mafia `pendingNightChoice` (nulls ignored). Round 1 + REVOTE + nonempty tally + >1 at max → open coord round 2 (clear Mafia pending/submitted only; store anonymized previous tally). Else `NightResolution.resolve` with RNG `seeded(randomSeed xor (day shl 16) xor coordRound)`.

NightResolution: plurality; ties on r2 or non-REVOTE: RANDOM_TIED / REVOTE-fallback pick `sortedBy raw` then `random.pick`; NO_KILL → no target. Doctor target dropped if consecutive-illegal. Saved iff mafia target == effective doctor. Kill if target alive and not saved. Detective result recomputed in helper but reducer already stored at submit.

**Verdict:** implemented and validated (edge cases + NightResolutionTest insertion-order invariance). Timers advertised in settings are **missing** as mechanics (rejected, not implemented) — GR-007.

## Day vote

OpenVote from Discussion only. Ballot = living − dropped. Candidates = same on first ballot. Cast/Abstain first-wins; self-vote gated by setting. CloseVote requires every ballot member acted.

`VoteResolution`: empty tally → AllAbstained. One leader → Eliminated. Tie: SKIP_ELIMINATION → SkippedDueToTie; `revoteRound >= maxRevotes` → MaxRevotesReached; REVOTE_TIED_ONLY → next candidates = sorted tied ids; REVOTE_ALL → same full candidate list.

Win check after elim uses `activeAliveSet` (living − dropped).

**Verdict:** implemented and validated.

## Win check

```
livingMafia == 0 → Town
livingMafia >= livingTown → Mafia
else continue
```

Empty alive → Town (helper total; reducer `evaluateCurrentWinner` returns null if alive empty **or** role map incomplete — Setup EndGame does not invent Town). Dropped seats excluded from parity.

**Verdict:** implemented and validated (`WinCheckTest`, early-end winner-null, dropped excluded in comments + continue-without ends game anyway).

## Host-only vs private vs public

| Bucket | Contents |
|---|---|
| Public | settings, day, roster (alive + optional revealedRole), lastNight, lastVote, activeVote (open tallies — **votes are public**), winner, disconnected, dropped |
| Private[id] | own role/team/teammates, mafiaCoordination (Mafia only, replicated identically), detective result, suspicion, previousDoctorProtect, pendingNightChoice, ack flags |
| HostOnly | fullRoleMap, randomSeed, nightLog/voteLog (capped 128) |

Projection: `toPublic`/`toPlayer` wipe hostOnly to empty map/seed 0/empty logs; `toPlayer` keeps only own private. PostGame projection also fills every `revealedRole` from the map.

**Verdict:** implemented and validated (projection tests + peer validator). Authority: SelfActor for all personal night/vote/acks; HostOnly for progression and connection. Role mismatch is reducer no-op (Town cannot kill). **Verdict:** implemented; unit matrix for `MafiaActionAuthority` is only via `MafiaPeerActionAuthorityTest` (wire), not a dedicated classify table like Whodunit.

## Disconnect / continue-without

`MarkPlayerDisconnected`: no-op in PostGame; no-op if unknown, not active-alive (eliminated/dropped), or already disconnected. **Does not set a pause flag** (there is none). `MarkPlayerReconnected` removes from set. `ContinueWithoutPlayer` requires current disconnect marker; Setup → unassigned PostGame; else drop seat, strip from activeVote/coordination, **always `finishGame`** (evaluate winner if role map complete). Live Mafia never continues with a reduced roster after grace expiry.

Public kdoc: “The game is paused while this set is non-empty.” **Reducer does not pause.** (GR-003)

**Verdict:** implemented as “end on expiry”; weakly tested as “pause” (tests only assert set membership).

## Snapshots / recovery

Encode/decode: `MafiaObservableStateValidator.requireValid` + `isValidRecoveryState` + exact canonical UTF-8 bytes. Resume additionally requires engine major match, `playMode=PassAndPlay`, phaseId match. Rejects: disconnected/dropped on local resume, forged roles/results, win already true in Night, incomplete logs vs announcements, vote ballot not exact ordered eligible set.

**Verdict:** implemented and validated. Recovery **rejects** rather than mutating game. Local-only resume path; multi-device snapshots are not this function.

## Illegal actions

Wrong phase, wrong role, already submitted, illegal target, incomplete readiness → no-op. `ResolveNight`/`CloseVote`/`Advance*` gated in reducer (UI is advisory).

## Rule status table

| Rule | Status |
|---|---|
| Classic 5–16, unique roster, presets valid | implemented and validated |
| Role assignment + Mafia teammates | implemented and validated |
| Atomic ConfigureAndStart | implemented and validated |
| Night simultaneous + all-must-submit | implemented and validated |
| Kill/inspect/protect/suspect role gates + settings | implemented and validated |
| Detective result at submit, ack blocks resolve | implemented and validated |
| Mafia kill tie REVOTE then random/no-kill | implemented and validated |
| Doctor consecutive / self-heal | implemented and validated |
| Day vote plurality + tie policies | implemented and validated |
| WinCheck Town/Mafia parity | implemented and validated |
| PostGame public role reveal | implemented and validated |
| Snapshot fail-closed, no silent rewrite | implemented and validated |
| Continue-without ends session | implemented and validated |
| Disconnect pauses gameplay | **contradictory** kdoc vs reducer (GR-003) |
| Night/discussion/vote timers | **missing** (settings reject non-null) (GR-007) |
| Host-submitted SelfActor on wire | rejected by `isAllowed` (host must use local path) — implemented |
| Multiple detectives/doctors | rejected by settings (MAX 1) |
