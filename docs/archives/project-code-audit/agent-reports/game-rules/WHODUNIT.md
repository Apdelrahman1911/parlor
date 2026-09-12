# Whodunit — reconstructed rules (from code)

Source of truth: `WhodunitReducer`, `WhodunitRules`, `WhodunitStateValidator`, codecs. Comments that contradict code are called out.

## Modes and roster

| Mode | `ModeId` | Players | Max evidence rounds |
|---|---|---|---|
| Classic Vote | `classic-vote` | 4–8 | 3 if count≤4, else 4 |
| Elimination | `elimination` | 5–8 | `playerCount − 2` |

`WhodunitDefinition.supportedPlayerCounts` is 4–8 (union). `createInitialState` requires `WhodunitRules.isValidRoster`: unique ids, seats `0..n-1` matching list index, distinct `RoomInputPolicy` display names, known mode. Case must also support the count (`isSupportedByCase`). Local entry: `PlayMode.PassAndPlay` only.

**Verdict:** implemented and validated (definition + `WhodunitRulesInvariantTest` + session rules).

## Phase machine

```
Setup
  --AssignRoles--> PublicIntro
  --all introAcknowledged + AdvanceFromIntro--> RulesBriefing
  --AdvanceBriefingCard 1..3 ungated; card 4 gated by briefingReady--> CharacterReveal(0)
  --all rolesViewed + AdvanceFromCharacterReveal--> Round(1)
```

Then per mode:

**Classic:** `Round(i)`: clue → timer → AdvanceFromDiscussion. If not last: `Round(i+1)`. If last: phase=`FinalVote` and immediately `OpenVote`. First tie → `TiedRevote` + `VoteState.Tied`. Host `OpenVote` → second Collecting (`isSecondRound`). Second tie or all-abstain → killer wins `TieUnresolved`. Resolved accusation → `Reveal`.

**Elimination:** After discussion (or `TimerExpired` at remaining≤1), `OpenVote` stays on `Round(i)`. Killer accused → `Reveal` PlayersWin. Innocent accused and survivors>2 → hold `Resolved(wasKiller=false)` on `Round(current)`; `AcknowledgeRevealCard` → next Idle round. Survivors≤2 after innocent elim → `Reveal` `SurvivedToFinalTwo`. Second tie / all-abstain: if survivors≤2 → SurvivedToFinalTwo; if at max rounds → TieUnresolved; else next Idle round.

`Reveal` + `AcknowledgeReveal` (blocked while `disconnectedPlayers` nonempty) → `PostGame`. `BeginReplay` only from PostGame with empty disconnect/dropped.

**Verdict:** implemented and validated for the happy/regression paths in `TiedRevoteTest`, `WhodunitRulesInvariantTest.modelDrive`, `WhodunitTerminalAndCluePolicyTest`. See GR-001 for OpenVote being wider than the discussion path.

## Roles / assignment

- Exactly one Killer, rest Innocent. Seeded `RandomSource.seeded(seed)`: shuffle characters, take `n`, zip to seats; pick killer from eligible (replay excludes previous killer).
- Killer private `deflectionTargets` = authored guilty targets ∩ assigned characters. Innocents empty.
- `roleAssignmentGeneration` increments from 0; first assignment is 1. Reveal actions must carry current generation.
- AssignRoles rejected if not Setup, invalid roster/case, `playersAtTable != players`, private/seat already nonempty.

**Verdict:** implemented and validated (`everySupportedCountAndSeed…`, bias sample 4000, rematch exclusion weakly via code only).

## Reroll

Only in `CharacterReveal`. Rejection-samples up to 64 seeds until killer **and every seat character** change; else deterministic rotate seats + next killer. Clears readiness, clues, vote, timer, pause, verdict, eliminated.

**Verdict:** implemented and validated (`privacyRerollChangesKillerAndEveryPreviouslyViewedDossier` for 4–8 × seeds 0..100).

## Clues

`WhodunitCluePolicy.select`: seed `randomSeed xor (roundIndex.toLong() shl 8)`.

- Last authored round: prefer undrawn `finalStrong[killer]`; else union of pointing+contradiction+redHerring+publicUniversal.
- Round 1: publicUniversal + killerPointing.
- Other: pointing + contradiction + redHerring.
- Filter `appliesToModes` if present. Never reuse `drawnClueIds`. One clue per round (`revealedClues` already has that `roundIndex` → no-op).

`StartDiscussionTimer(seconds)` must equal `WhodunitRoundPolicy.discussionSeconds` (authored nearest-bucket, default 180, must be >0). Timer only after that round's clue; vote Idle; no existing timer.

**Verdict:** implemented and validated by golden sequences (`WhodunitPolicyGoldenTest`) and mode-restriction test. `CluePolicyTest` assertions are real but fixtures are validator-illegal (GR-010).

## Timers / pause

- Session `Pause` only in `Round`, not while Collecting, not while any dossier unlocked/review-open. Freezes timer.
- While `public.paused`, only Pause/Resume/EndGameEarly/MarkDisconnected/Reconnected/ContinueWithoutPlayer apply.
- `TimerTicked` ignored if session or timer paused; never increases remaining; warning when crossing 10s.
- `TimerExpired` only if Round, timer present, not paused, `remainingSeconds <= 1`; then same as AdvanceFromDiscussion + `TimerExhausted`.
- Disconnect of a **non-eliminated** player in active phases (not Setup/Reveal/PostGame) sets paused + freezes timer. Eliminated audience disconnect is a no-op.

**Verdict:** implemented and validated (`ContinueWithoutPlayerTest`, pause guards test).

## Voting

- Sequential: only `ballotPlayerIds[currentVoterIndex]` may act. First submission wins. No self-vote. Target must be candidate, not dropped, not eliminated.
- Classic ballot = active (table − dropped). Elimination ballot = survivors (active − eliminated).
- Revote: all voters still vote; candidates = tied ∩ ballot.
- CloseVote requires every ballot id in cast∪abstain. Empty tally treated as unresolved (same as second tie).
- Plurality among `candidatePlayerIds` with `tally[id]==max`. Tie if >1 such candidate.

**Verdict:** implemented and validated (`TiedRevoteTest`, ballot guards). Sequential order is load-bearing for multi-device (not simultaneous).

## Win / reveal

| Outcome | How |
|---|---|
| PlayersWin | Classic or Elimination vote names killer |
| KillerWins(InnocentAccused) | Classic names innocent |
| KillerWins(TieUnresolved) | Classic second tie / all-abstain; Elimination unresolved at last authored round |
| KillerWins(SurvivedToFinalTwo) | Elimination after innocent elim or unresolved vote leaves ≤2 active survivors |
| KillerWins(GameEndedEarly) | EndGameEarly(withReveal) or ContinueWithout after assignment (not Setup/Reveal) |

`killerWins()` helper always writes `VoteState.Resolved(killerId, wasKiller=true)`. Validator for SurvivedToFinalTwo requires `!wasKiller` and last eliminated == accused innocent. Therefore **only `resolveVote`'s dedicated SurvivedToFinalTwo branch** produces a persistable SurvivedToFinalTwo state. The helper path is contradictory (GR-002).

## Continue-without / dropped

After assignment, grace expiry **ends the case with reveal**. It also writes `droppedPlayers += id` then `endGameEarly`. Comment on `droppedPlayers` says new reducers never add entries — **false** (GR-004). Setup expiry → PostGame, no verdict, no invented killer. Reveal expiry → PostGame, clears disconnects. Replay forbidden if dropped or disconnected nonempty.

**Verdict:** implemented and validated (`ContinueWithoutPlayerTest`, terminal tests). Kdoc is stale.

## Authority

HostOnly: all progression, timers, pause, end, reroll, connection chrome, `AdvanceFromCharacterReveal`.
SelfActor: Start/Complete reveal (generation-bound), intro/briefing acks, Cast/Abstain/Refuse.
Dropped SelfActor rejected at authority layer. Host is trusted for HostOnly; host **cannot** submit another player's SelfActor via `isAllowed`.

**Verdict:** implemented and validated except the host-only list test omits `AdvanceFromCharacterReveal` (classify still HostOnly — test gap, not a hole).

## Snapshots

Encode: `requireValid` then envelope `{kind, schemaVersion:1, state}`. Decode versioned: exact canonical JSON (no missing-field repair). Legacy bare: normalize untimed revote, killer deflection, generation=1 if assigned. Peer: public + own private; hostOnly redacted sentinels; vote targets redacted while Collecting.

**Verdict:** implemented and validated (`WhodunitSnapshotValidationTest` + codec tests). Recovery does not silently change game for current schema.

## Illegal actions

Wrong phase / wrong generation / not current voter / paused gameplay / incomplete readiness → `Reduction(state)` no events. Not an exception.

## Rule status table

| Rule | Status |
|---|---|
| Modes, counts, roster, case bind | implemented and validated |
| Assign / one killer / dossiers / generation | implemented and validated |
| Intro/briefing/reveal readiness | implemented and validated |
| Simultaneous CharacterReveal (playerIndex always 0) | implemented and validated |
| Classic clue→discuss→next / final vote | implemented and validated |
| Elimination vote every round | implemented and validated |
| Sequential secret ballot, no self, first wins | implemented and validated |
| First tie → host-paced TiedRevote | implemented and validated |
| Second Classic tie / all-abstain → killer | implemented and validated |
| Second Elimination tie → next or terminal | implemented and validated |
| Clue pools + last-round finalStrong | implemented and validated |
| Authored timer seconds | implemented and validated |
| Session pause + disconnect pause | implemented and validated |
| Continue-without ends (does not shrink live roster) | implemented and validated |
| Replay / reroll | implemented and validated |
| Snapshot fail-closed + peer case refs | implemented and validated |
| OpenVote only after discussion | **contradictory** (GR-001) |
| SurvivedToFinalTwo via killerWins helper | **contradictory** (GR-002) |
| droppedPlayers “never written” | **contradictory** kdoc (GR-004) |
| Simultaneous / out-of-order votes | intentionally rejected (not a defect) |
| Structured actions | retired; codec fail-closed |
