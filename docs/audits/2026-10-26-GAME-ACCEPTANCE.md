# Three-game acceptance follow-up — 2026-10-26

Scope: remaining feasible automated acceptance in [#260](https://github.com/Apdelrahman1911/parlor/issues/260),
based on aggregate PR #263 at `6151fb4b`. This follow-up does not qualify physical
multiplayer, native lifecycle, screen readers, fairness or human playtesting.

## Explicit appearance and interaction coverage

Twelve new named Desktop test methods exercise English/Arabic × Light/Dark at
320 × 740 dp and 200% text. Existing System-mode tests remain. They reuse the
actual game table/reducer interaction journeys rather than static previews:

- **Egyptian Dominoes:** reveal/select legal tiles, completed match results,
  reset scores, new-hand token and covered rematch.
- **Ghamza:** private role reveal, Back/interruption concealment, readiness,
  confirmed/cancelled self-report, final guess, result and rematch.
- **Word Impostor:** both private roles, Back/interruption concealment, private
  question progression, sealed ballot, independent five-choice guess, public
  result and host-only rematch.

Screenshots contain only synthetic public/covered state, never an unrevealed
private role/hand/word. The game tables intentionally retain their recognizable
felt/teal/purple palettes in both app appearance preferences. This verifies the
explicit preferences, not a claim that every game surface changes palette.

## Confirmed additional defect and correction

The rendered Domino opponent roster used a fixed three-way weighted `Row`.
At 320 dp/200%, its text area was **82 px**, versus **91 px** for the English
word “Player” and **160 px** for “عبدالرحمن”. Ordinary names broke mid-word;
this was distinct from the previously corrected result scoreboard.

Four locale/theme regression methods failed against unchanged production code
at `3b9921f6`. The tests measure the actual public seat card against the same
font's longest name word, preserving intentionally consolidated accessibility
semantics. An initial test-compilation mistake (`DpRect.width`) at `8efca565`
was corrected before that real four-failure baseline; it was not a product bug.

Correction `c0577e12` uses a weighted `FlowRow`: intrinsic word widths determine
wrapping before each line shares space. No font scaling is capped. Optional
opponents now follow the board/current hand controls, so expanded cards do not
bury the next action. The tests also verify that action ordering. Public seat
labels, counts, team colors, turn highlighting and animation anchors are retained.

## Differential recovery boundary

Two additional common-test methods compare complete interrupted and
uninterrupted deterministic matches:

- **Ghamza:** 90 scenarios, every 3–12-seat roster, 1–3 attempts, and 1/3/5 rounds;
  correct and incorrect final guesses occur across the matrix.
- **Word Impostor:** 42 scenarios, 3/7/12 seats, all supported impostor counts,
  1/3/5 rounds and two seeds, rotating through all 12 topics; mixed correct and
  incorrect personal guesses and deterministic ballots.

These are **reducer-lifecycle/action/projection/authority-snapshot codec models**,
not network fault injection or `SessionController` credential restoration.
Queued encoded commands are held across two absent seats; one returning seat
cannot clear another's pause. Authority snapshots round-trip while paused.
Identical redelivery models a lost acknowledgement; all older accepted commands
are replayed out of order and across rematches to assert inertness.

Every accepted step compares roster/seat identity, phase, public and private
state and host state except the additional lifecycle history. Serialized public
views have no host/private bucket; each recipient gets exactly its own slice,
and wrong-recipient decoding fails. Final results/scores and rematches agree.
No claim of native process restoration or secure-file persistence follows.

## Executed verification

Linux, JDK 21, Xvfb, strict dependency verification; exact source
**`0107522cb1dcadb321a1fcbbba5c0ae5475274b6`**. All three test tasks were explicitly
rerun, not satisfied from prior test outputs:

```bash
PARLOR_UI_SCREENSHOT_DIR=<evidence>/screenshots xvfb-run -a ./gradlew \
  :game-modes:egyptian-dominoes:desktopTest --rerun \
  :game-modes:ghamza:desktopTest --rerun \
  :game-modes:word-impostor:desktopTest --rerun \
  :game-modes:egyptian-dominoes:detektMetadataCommonMain \
  --continue --dependency-verification=strict --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx3g -XX:MaxMetaspaceSize=1g' \
  -Pkotlin.compiler.execution.strategy=in-process
```

**PASS, exit 0, 1m41s**: Dominoes **41**, Ghamza **20**, Word Impostor **24** =
**85 JUnit cases**, zero failures/errors/skips. The 132 differential scenarios
are loops inside two cases, not 132 additional JUnit tests. An earlier same-head
1m19s run passed plain Detekt and type-aware Desktop-test analysis for all three;
Domino Desktop-main analysis was correctly `NO-SOURCE`, so the explicit
common-main type-aware task above checked the changed shipping source.

Generated **70 public/covered PNGs**, including **48 explicit Light/Dark**
frames. Contact-sheet inspection exposed and then verified the narrow roster
correction; the unchanged Ghamza/Word frames were also inspected. Scrolling
remains necessary at 200% text; reachable controls and full-text assertions are
not physical accessibility certification.

Local custody: worktree `game-acceptance/build/acceptance-evidence/`, with separate
initial, compilation-error, four-failure baseline, final and forced-execution
logs. `executed/receipt.json` binds source/tree, current per-module XML and PNG
hashes; SHA-256 `d8667d38a4d9f3a7820d1715bc6c83958b0dce8e4df4ebd4bfadad5a75a59407`.
Reports/inventory added afterward do not retroactively certify a later integration
head. Full hosted and merged-head qualification remains the lead workflow's gate.
