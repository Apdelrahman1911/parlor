# Findings — game-rules (GR-001+)

Only items with file/line evidence. UI not used as proof.

---

### GR-001 — Elimination `OpenVote` does not require a clue or discussion

- **Severity:** High
- **Confidence:** High
- **Category:** Rules / snapshot reachability
- **Platforms:** All (reducer)
- **Blocks release?** Needs lead confirm. Live play can enter a state the snapshot codec refuses.
- **Files/symbols:**
  - `WhodunitReducer.openVote` `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/domain/reducer/WhodunitReducer.kt:614-626`
  - `WhodunitReducer.advanceFromDiscussion` same file `:587-609`
  - `WhodunitStateValidator.validatePhaseShape` `.../state/WhodunitStateValidator.kt:667-686`
- **Evidence:**
  Elimination `validEntry` is only `phase is Round && voteState == Idle && timer == null`. No `revealedClues` check. Classic correctly requires `FinalVote`.
  `validatePhaseShape` for Elimination `Collecting` requires `clueCount == phase.index && timer == null`.
  After `RevealNextClue` (clueCount == index, timer null, Idle), `OpenVote` succeeds and **is persistable**, skipping `StartDiscussionTimer` / `AdvanceFromDiscussion`.
  Before any clue (clueCount == index-1), `OpenVote` still succeeds. The resulting Collecting **fails** `requireValid` / encode.
- **Current vs expected:** Discussion is mandatory on the `AdvanceFromDiscussion` path (`timer == null` rejects). `OpenVote` is a second, wider entry. Pre-clue Collecting is reducer-legal and validator-illegal.
- **Root cause:** `openVote` Elimination guard was not aligned with `advanceFromDiscussion` or `validatePhaseShape`.
- **Impact:** Modified/buggy host can skip discussion after a clue. Same host can open a vote with no evidence; the session then cannot snapshot or resume. `CloseVote` from that Collecting can produce further validator-illegal terminals (`validateVerdictProgression` requires a completed clue for that round).
- **Reproduction:** 5p Elimination, `AssignRoles` → drive to `Round(1)` with no clue → `OpenVote` → `voteState is Collecting` and `WhodunitStateValidator.requireValid` throws. After one `RevealNextClue` (no timer) → `OpenVote` → validator accepts.
- **Tests detect?** No. `TiedRevoteTest` / model drive always start the timer first. No test asserts `OpenVote` is a no-op without a clue or timer.
- **Fix direction / required tests:** Gate Elimination `OpenVote` the same as `advanceFromDiscussion` (current-round clue present). Decide whether skipping an already-started discussion via raw `OpenVote` is legal. Tests: pre-clue OpenVote no-op; post-clue pre-timer OpenVote no-op **or** explicitly allowed and documented.
- **Related:** GR-010

---

### GR-002 — `killerWins(SurvivedToFinalTwo)` writes a validator-illegal `Resolved`

- **Severity:** Medium
- **Confidence:** High
- **Category:** Rules / snapshot
- **Platforms:** All
- **Blocks release?** No for current legal live paths; yes if that helper is ever hit.
- **Files/symbols:**
  - `WhodunitReducer.killerWins` `:943-956` — always `VoteState.Resolved(killerId, wasKiller=true)`
  - `continueAfterUnresolvedEliminationVote` `:918-919` — `killerWins(..., SurvivedToFinalTwo)` when `survivors.size <= 2`
  - `resolveVote` `:806-823` — legal SurvivedToFinalTwo uses `Resolved(accused, wasKiller=false)`
  - `WhodunitStateValidator.validateCanonicalKillerWin` `:843-851` — requires `!resolved.wasKiller` and `accused == eliminated.last`
- **Evidence:** Snapshot test `eliminationTerminalOutcomesRejectReducerImpossibleHistories` decodes-rejects a SurvivedToFinalTwo state whose vote is `Resolved(killer, true)` — the exact shape `killerWins` emits.
  Legal live SurvivedToFinalTwo comes only from `resolveVote` after an innocent elim leaves ≤2 survivors (persistable; `WhodunitSnapshotValidationTest.reducerGeneratedEliminationFinalTwoRoundTrips`).
  `continueAfterUnresolvedEliminationVote` (all-abstain / second tie) would use the helper. In a legal active game `droppedPlayers` is empty (`validateConnectionShape`), so ≤2 survivors without going through `resolveVote` does not occur. The helper path is latent / legacy-dropped.
- **Current vs expected:** Two writers for one verdict; only one is persistable.
- **Root cause:** Shared `killerWins` assumes “resolved as killer identified,” which is wrong for SurvivedToFinalTwo.
- **Impact:** If dropped-during-vote or a future shrink path calls the helper, Reveal cannot be encoded. UI can still show the verdict.
- **Reproduction:** Construct Collecting with 2 survivors (or plant dropped as in GR-010) → `CloseVote` all-abstain → `verdict.cause == SurvivedToFinalTwo` and `voteState == Resolved(killer, true)` → `requireValid` throws.
- **Tests detect?** Inverse: snapshot test rejects the helper’s shape. Production-guards test **asserts the helper outcome** on an illegal planted state (GR-010) and never calls `requireValid`.
- **Fix direction / required tests:** Do not use `killerWins` for SurvivedToFinalTwo; reuse `resolveVote`’s Resolved shape. Drive all-abstain at 2 survivors from a **validator-legal** fixture (or prove the branch unreachable and delete it). After CloseVote, `requireValid`.
- **Related:** GR-010

---

### GR-003 — Mafia disconnect does not pause; public kdoc and tests imply it does

- **Severity:** Medium
- **Confidence:** High
- **Category:** Rules / authority
- **Platforms:** Multi-device (P&P has no disconnect chrome)
- **Blocks release?** Product decision. Host can still fire ungated HostOnly actions.
- **Files/symbols:**
  - `MafiaPublic` kdoc `MafiaState.kt:43-47` — “The game is paused while this set is non-empty.”
  - `MafiaReducer.markDisconnected` `:827-843` — only adds to `disconnectedPlayers`
  - Contrast `WhodunitReducer.markPlayerDisconnected` `:301-311` — sets `paused` and freezes timer
  - `MafiaReducer.reduce` has **no** paused short-circuit
- **Evidence:** After `MarkPlayerDisconnected`, host can still `EndGame`, and any HostOnly action whose other gates are already satisfied (e.g. `ResolveNight` if everyone already submitted; `OpenVote` if already in Discussion). Ack-gated advances still wait for the missing living seat. `ContinueWithoutPlayer` ends the session rather than pausing.
  `MafiaReducerTest.mark_player_disconnected_and_reconnected_round_trips` only asserts set membership.
- **Current vs expected:** Expected (kdoc): paused. Actual: set update only. Whodunit implements the kdoc.
- **Root cause:** Connection chrome copied as a set without a pause flag or reduce-time gate.
- **Impact:** No freeze of an in-flight night that is already ready; host can end or advance past a vanished peer when readiness is already complete. Peers see no canonical paused bit.
- **Reproduction:** Night, all submitted, `MarkPlayerDisconnected(mafia)` → `ResolveNight` still resolves. Discussion, all night-acked, disconnect → `OpenVote` still opens (disconnected seat remains on ballot).
- **Tests detect?** No pause assertion exists.
- **Fix direction / required tests:** Either implement pause (Whodunit-style, reject gameplay HostOnly while disconnected) or delete the kdoc and test the “stall on missing ack/ballot only” contract explicitly.
- **Related:** GR-004

---

### GR-004 — `droppedPlayers` comments contradict the writer

- **Severity:** Low
- **Confidence:** High
- **Category:** Spec drift
- **Platforms:** All
- **Blocks release?** No
- **Files/symbols:**
  - `WhodunitPublic.droppedPlayers` kdoc `WhodunitState.kt:79-83` — “New reducers never add entries”
  - `WhodunitReducer.continueWithoutPlayer` `:361-364` — `droppedPlayers + playerId` then end
  - `MafiaPublic` same pattern: active validator forbids dropped (`MafiaObservableStateValidator.kt:57-59`) while `continueWithout` writes dropped then `finishGame` (`MafiaReducer.kt:904-918`)
- **Evidence:** `ContinueWithoutPlayerTest.grace_expiry_reveals_case_marks_missing_seat` asserts `droppedPlayers` contains the seat. Validator allows dropped only in Reveal/PostGame.
- **Current vs expected:** Field is a terminal/legacy marker written on expiry, not “never written.”
- **Root cause:** Stale comments after continue-without was changed from roster-shrink to end-session.
- **Impact:** Auditors/hosts may think expiry leaves no dropped marker; replay correctly refuses when dropped is nonempty (`beginReplay` `:977-980`).
- **Reproduction:** Read kdoc vs `ContinueWithoutPlayer` after assignment.
- **Tests detect?** Tests document actual behavior; comments do not.
- **Fix direction:** Rewrite kdocs to “written only by grace expiry; active phases must be empty.”
- **Related:** GR-003

---

### GR-005 — `WhodunitEvent.PrivacyConcernRaised` is never produced

- **Severity:** Low
- **Confidence:** High
- **Category:** Missing rule / dead contract
- **Platforms:** All
- **Blocks release?** No
- **Files/symbols:** `WhodunitEvent.kt:18` — only occurrence in the repo (grep).
- **Evidence:** Reducer `when` has no branch that adds `PrivacyConcernRaised`. No test expects it.
- **Current vs expected:** Event ADT advertises a safety signal the state machine cannot emit.
- **Root cause:** Event left after private-review / privacy overlay was retired (`OpenPrivateReview` codec-retired).
- **Impact:** None at runtime. False API surface.
- **Reproduction:** Grep `PrivacyConcernRaised`.
- **Tests detect?** No
- **Fix direction:** Remove the event or emit it from a real reducer transition with a test.
- **Related:** none

---

### GR-006 — Mafia phase timers are modeled and rejected, not implemented

- **Severity:** Low
- **Confidence:** High
- **Category:** Missing
- **Platforms:** All
- **Blocks release?** No (fail-closed)
- **Files/symbols:**
  - `MafiaSettings.nightDurationSeconds/discussionDurationSeconds/voteDurationSeconds` `MafiaSettings.kt:24-26, 51-60`
  - `MafiaSettingsError.TimersNotSupported`
  - `MafiaSettingsValidationTest.rejects_non_null_timer_until_timer_transitions_are_implemented`
  - `MafiaActionCodecTest.apply_settings_round_trips` encodes `nightDurationSeconds = 30` (codec allows; reducer `ApplySettings` rejects)
- **Evidence:** Any non-null duration → `Invalid(TimersNotSupported)`. Reducer never reads these fields for transitions. Night/Discussion/Vote are host-paced only.
- **Current vs expected:** Settings type looks timed; engine is untimed.
- **Impact:** No silent timer. Hosts cannot configure durations. Wire payloads may still carry the fields.
- **Reproduction:** `ApplySettings` with `nightDurationSeconds=60` is a no-op.
- **Tests detect?** Yes — as explicit rejection, not as a shipping timer.
- **Fix direction:** Drop the fields from the shipping settings schema **or** implement bound timers. Do not leave codec-round-trippable unused rules.
- **Related:** none

---

### GR-007 — `MafiaActionAuthority` has no dedicated classify matrix test

- **Severity:** Low
- **Confidence:** High
- **Category:** Test gap (false confidence if relying on Whodunit-style coverage)
- **Platforms:** Multi-device
- **Blocks release?** No
- **Files/symbols:** `MafiaActionAuthority.kt`; only consumer test `MafiaPeerActionAuthorityTest.kt` (wire/bridge)
- **Evidence:** Whodunit has `WhodunitActionAuthorityTest` enumerating HostOnly vs SelfActor. Mafia `classify` is untested in isolation. `AdvanceFromCharacterReveal` equivalent is not the issue here; Mafia’s `ConfigureAndStart` / night submits are covered only if the bridge test enumerates them.
- **Current vs expected:** Same policy importance, thinner unit net.
- **Impact:** A new HostOnly action defaulting wrong in `when` would compile if the `when` stays exhaustive; a mistaken SelfActor would not be caught until a wire test is written.
- **Reproduction:** n/a
- **Tests detect?** Partially via bridge test, not a full matrix.
- **Fix direction:** Copy Whodunit’s exhaustive `isAllowed` table.
- **Related:** GR-010 (`WhodunitActionAuthorityTest` omits `AdvanceFromCharacterReveal` from its HostOnly *list* even though classify is correct)

---

### GR-008 — `AdvanceFromVoteAnnouncement` winner short-circuit is dead vs validator

- **Severity:** Low
- **Confidence:** High
- **Category:** Dead code / test false-confidence
- **Platforms:** All
- **Blocks release?** No
- **Files/symbols:**
  - `MafiaReducer.advanceFromVoteAnnouncement` `:775-781`
  - `MafiaObservableStateValidator.validateAnnouncements` `:136-138` — `winner == null || phase == PostGame`
  - `MafiaReducerEdgeCasesTest.advance_from_vote_announcement_jumps_to_post_game_when_winner_set` plants `VoteAnnouncement` + `winner=Town` on **Setup public** (day 0, no lastNight)
- **Evidence:** `applyVoteResolved` / `resolveNight` set `winner` only together with `phase = PostGame`. Validator forbids winner on VoteAnnouncement. The planted fixture is also missing `lastNight`/`lastVote` for that phase.
- **Current vs expected:** Test name claims a production transition; it exercises an unreachable shape.
- **Impact:** None in live play. Hides that win already terminates at resolve.
- **Reproduction:** Read reducer win branches vs the test fixture.
- **Tests detect?** The test passes on illegal state.
- **Fix direction:** Delete the branch or stop setting PostGame at resolve and persist winner on VoteAnnouncement (would need validator change). Replace the test with “CloseVote that hits parity → phase PostGame immediately.”
- **Related:** GR-010

---

### GR-009 — Whodunit display-name equality is exact / case-sensitive (intentional, identity-adjacent)

- **Severity:** Low
- **Confidence:** High
- **Category:** Identity / UX (not a reducer bug)
- **Platforms:** All
- **Blocks release?** No
- **Files/symbols:** `WhodunitRules.isValidRoster` → `RoomInputPolicy.areValidDistinctDisplayNames`; `MafiaSessionRules` same. Tests `exactDuplicateDisplayNames…` assert `Alice`/`alice` **accepted**.
- **Evidence:** Comment in `RoomInputPolicy` (line 37-38): equality is exact; `PlayerId` is identity. Arabic names accepted. No `equals(..., ignoreCase)` and no locale Collator in reducers. Tie-breaks use `PlayerId.raw` sort (`NightResolution.kt:103`, `VoteResolution.kt:48`).
- **Current vs expected:** Two seats can be labeled `Alice`/`alice`. Not a locale-sensitive compare bug; flagging because the audit asked for identity mixups.
- **Impact:** Human confusion only. Authority uses `PlayerId`.
- **Tests detect?** Yes, as allowed.
- **Fix direction:** None unless product wants case-fold uniqueness. Do not introduce locale Collator into reducers.
- **Related:** none

---

### GR-010 — Tests that claim rule proof on validator-illegal fixtures

- **Severity:** Medium
- **Confidence:** High
- **Category:** Test false-confidence
- **Platforms:** Test only
- **Blocks release?** No (does not prove production)
- **Files/symbols:**
  1. `CluePolicyTest.kt` `stateAtRound` — `privatePerPlayer=empty`, `seatToCharacter=empty`, `currentRound=4` in `Round(4)`. `requireValid` would throw (“Active state has no role assignment”). Assertions about `finalStrong` still hit `WhodunitCluePolicy` via the reducer, but not a legal session.
  2. `WhodunitReducerProductionGuardsTest.elimination_final_two_counts_only_active_non_eliminated_players` — plants `droppedPlayers={p3}` inside Collecting. `validateConnectionShape` forbids dropped in active phases. Never calls `requireValid`. Asserts `SurvivedToFinalTwo` from the helper (GR-002).
  3. `WhodunitActionAuthorityTest.host_only_actions_reject_peers` — list omits `AdvanceFromCharacterReveal` (still HostOnly in `classify`).
  4. `validatedWhodunitCaseForTest` — `PayloadValidator` always `Success(payload)`. `CluePolicyTest.emptyLateGamePoolDoesNotCrash` and several reducer tests are not shipping-content legal.
  5. `MafiaReducerTest.mark_player_disconnected_and_reconnected_round_trips` — no pause/progression assertion (GR-003).
  6. `MafiaReducerEdgeCasesTest.advance_from_vote_announcement_jumps_to_post_game_when_winner_set` — GR-008.
- **Evidence:** Read assertions vs `requireValid` / `isValidRecoveryState`.
- **Current vs expected:** Green tests ≠ legal state machine.
- **Impact:** GR-001 / GR-002 / GR-003 can survive CI.
- **Reproduction:** Run `requireValid` on those fixtures (expected throw).
- **Tests detect?** They are the false confidence.
- **Fix direction:** After every reducer step in rule tests, `WhodunitStateValidator.requireValid` / `state.isValidRecoveryState()`. Replace planted dropped Collecting with a legal drive. Add omitted authority actions. Do not use the payload-validator bypass for playability claims.
- **Related:** GR-001, GR-002, GR-003, GR-007, GR-008

---

## Confirmed non-findings (checked)

- Peer cannot submit HostOnly (classify + Whodunit unit matrix; Mafia bridge test).
- Whodunit vote targets redacted while Collecting; Mafia day votes are **intentionally public**.
- Mafia night RNG is `seeded(hostSeed xor day shl 16 xor coordRound)`, not `ctx.random`. Tie pick sorts `PlayerId.raw` first.
- Whodunit clue pick is seeded `randomSeed xor (round shl 8)`; golden sequences pinned.
- Current Whodunit/Mafia snapshot decode does not migrate/repair current schema (canonical byte/JSON equality).
- Mafia / Whodunit `ContinueWithout` after assignment ends the game (does not silently shrink a live investigation). Setup expiry does not invent a killer/Town win.
- `CancellationException` preserved in `loadMafiaResumedSession`.
- Display-name control/format characters rejected. Content ids kebab-case ASCII.
