# Correctness and state review

Lead verified GR-001, GR-002, GR-003 against reducer/validator source.

## Confirmed defects

### F-003 (GR-001) — Elimination `OpenVote` wider than discussion path — High

`openVote` Elimination guard: Round + Idle + `timer == null`. No clue check.
`advanceFromDiscussion` requires a current-round clue **and** a timer.
`validatePhaseShape` for Elimination Collecting requires `clueCount == phase.index`.

So `OpenVote` before any clue produces Collecting that **fails** `requireValid`
(unsavable / unresumable). After a clue but before the timer, `OpenVote`
skips discussion and **is** persistable.

UI happy path starts the timer first (`TiedRevoteTest`). A modified host,
queued action, or future UI shortcut can hit the hole. No test asserts
pre-clue OpenVote is a no-op.

### F-004 (GR-003) — Mafia disconnect does not pause — Medium

`MafiaPublic.disconnectedPlayers` kdoc: “The game is paused while this set is
non-empty.” `markDisconnected` only adds the id. Reducer has no paused
short-circuit. Host can still `EndGame` / `ResolveNight` / `OpenVote` when
other gates are already satisfied. Whodunit actually pauses.

Host auto-progression skips when the set is nonempty (partial mitigation in
UI/bridge, not in the reducer).

### GR-002 — `killerWins(SurvivedToFinalTwo)` emits illegal Resolved — Medium

Helper writes `Resolved(killer, wasKiller=true)`. Validator requires
`!wasKiller`. Legal live path uses `resolveVote`’s other shape. Helper is
latent unless a shrink/dropped path hits it. Snapshot tests reject the
helper’s shape; a production-guards test asserts the helper on an illegal
fixture (GR-010).

## Verified non-defects

- Peer cannot submit HostOnly (Whodunit matrix + Mafia bridge).
- Display-name equality is exact; `PlayerId` is authority (GR-009, intentional).
- Night/clue RNG is seeded, not `Random` wall-clock (except session seed source
  which is CSPRNG at start).
- Continue-without after assignment ends the game rather than silently
  shrinking hidden-role rosters.
- Snapshot decode does not “repair” current-schema illegal state into another
  game.

## Residual

Clock/timeouts are injected `Clock` in reducers; transport uses wall millis
for admission/grace. Locale is not used in rule compares. State divergence
host vs peer is by design (projections), not a bug — unless a leak exists
(none confirmed on the encode path).
