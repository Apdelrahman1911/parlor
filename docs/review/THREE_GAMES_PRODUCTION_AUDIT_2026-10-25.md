# Three-game production audit — 2026-10-25

Scope: Egyptian Dominoes, Ghamza and Word Impostor on `feat/last-light`
base `39aacd31`. This is a scoped audit, **not** a production or device qualification.
See `THREE_GAMES_INTEGRATION.md` for established house rules and the physical-device
acceptance matrix. The lead audit owns GitHub deduplication and issue linkage.

## Architecture and behavioral review

All three modules separate pure reducers/projections, strict serialization and
Compose UI. Transport admission, authenticated actors, revisions, pending command
reservation and actual reconnection belong to the existing shared session shell.
These reducers do not independently authenticate a network actor. The authority
policy checks a bound actor; lifecycle actions cannot be decoded as player commands.
No networking dependency or alternate transport was introduced.

Reducers stop player actions when any required seat is disconnected. Reconnection
removes only that seat's pause. Rematches retain the roster, reset the match and
advance a monotonic token. Round/move/attempt guards make delayed and duplicated
actions inert. Host-only snapshots use deterministic replay equality; peer state
contains the public projection and only its recipient's private slice. Aborts clear
unrevealed private UI content without assigning new points. A trusted host still
knows all hands/roles: this is not protection against a modified host binary.

### Egyptian Dominoes

Reviewed 2–4 seats, Default/Draw/Block, three-seat removal of only double-blank,
ordered stock, required opening, both chain ends, passing and blockage, opposite-seat
teams, canonical side scores, 51/101/151 targets and the 151-mode shutout rule.
Highest dealt double leads; later hand winners lead freely. A blocked tie scores
nobody; team ties use seat order only within an already winning side. Partner hands
remain private. Existing tests exercise deals/hands, scoring and maximum history.
New differential recovery coverage runs complete matches at every player count and
variant, comparing interrupted and uninterrupted authoritative outcomes.

### Ghamza

Reviewed 3–12 seats, 1/2/3 attempts, 1/3/5 rounds, exactly one Winker, readiness,
self-reported physical winks, last ordinary player's final guess, result-derived
elimination and rematches. There is deliberately no score field or cumulative
champion. Repeated random Winker assignments are permitted. The app cannot verify
physical winks or honesty; automatic camera/face recognition is not appropriate.
An eliminated participant still occupies a required seat and can interrupt play if
lost, matching the existing social-game contract.

### Word Impostor

Reviewed 3–12 seats, strict ordinary majority, 12-topic offline content,
Hamiltonian question cycles, private asker prompts, host-opened voting,
single immutable votes, hidden guesses and independent personal scoring. Tied
informational poll results do not remove ordinary players' correct-vote points.
Impostors each receive five plausible same-family choices and independently earn
one point for the correct answer. Results publish only when every impostor has
submitted. Abort preserves previous points but does not score an unfinished round.

## Findings and implementation

| ID | Classification | Evidence and impact | Change |
|---|---|---|---|
| NG-01 | Confirmed UI defect, medium | Hide/Cancel controls used the network action `enabled` flag; touch could not conceal a shown secret while a request was pending. Initial role reveal additionally offered readiness but no independent touch concealment. | All three games keep local concealment available. Ghamza/Word reveal offer separate Hide without submitting readiness; local cancellation also stays enabled. Three Compose regression tests. |
| NG-02 | Confirmed integrity gap, medium | Ghamza's final two active seats could both be given Guest private projections despite exactly one Winker. | Require a possible non-reporting Winker seat consistent with own role and final guesser. Reject a contradictory old private payload. |
| NG-03 | Confirmed integrity gap, medium | Word private payload containing a submitted guess could be combined with public `guessCount=0`. | Reject the contradictory assembled projection; legitimate partial submissions remain accepted. |
| NG-04 | Confirmed integrity gap, low | Domino opening private projection accepted a lower double while the same hand contained a higher double. | Centralize highest-opening rule and validate the own-hand opening constraint. No rule change. |
| NG-05 | Confirmed defensive parser gap; shared security finding | Legal byte ceilings alone do not bound recursive JSON parser depth. | Shared allocation-free core preflight rejects depth above 64 before all three codecs decode. Deep malformed action/public/authority tests. |
| NG-06 | Confirmed navigation friction, low | Word guessing placed a potentially 12-row poll recap before the player's next action. Secondary secret reminders repeated large decorative artwork. Phase progress used color-only bars. | Move guess interaction ahead of recap; compact reminders; label EN/AR phase progress with accessibility range semantics; move Domino turn ahead of roster. |

NG-02–04 are defense-in-depth checks for impossible/corrupted projections, not a
claim that a remote peer can replace host state. Shared revision/recipient binding
remains necessary and is not replaced by game validation.

## Verification status

Executed in the lead integration worktree at **`15f127d9`**, JDK 21, Linux/Xvfb,
strict dependency verification. The lead ran:

```bash
PARLOR_UI_SCREENSHOT_DIR=<local-evidence-directory> xvfb-run -a ./gradlew \
  productionDesktopCheck --continue --dependency-verification=strict \
  --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx3g -XX:MaxMetaspaceSize=1g' \
  -Pkotlin.compiler.execution.strategy=in-process
```

Read back the actual JUnit XML under each module's
`build/test-results/desktopTest/` (not an earlier CI run):

| Module | Tests | Failures/errors/skips |
|---|---:|---:|
| Egyptian Dominoes | 30 | 0 / 0 / 0 |
| Ghamza | 13 | 0 / 0 / 0 |
| Word Impostor | 17 | 0 / 0 / 0 |

**All 60 scoped tests passed**, including these new cases:

- `DominoIntegrityTest`: highest own-hand opener; nesting resource guard.
- `GhamzaIntegrityTest`: impossible role/progress combination; nesting guard.
- `WordImpostorIntegrityTest`: partial guess/public count mismatch; nesting guard.
- `DominoRecoveryJourneyTest`: nine complete-match differential recovery scenarios,
  duplicate actions, two missing seats, out-of-order return and recipient filtering.
- Three game UI suites: local touch concealment while host actions are disabled,
  alongside existing English/Arabic large-text and complete interaction journeys.

This is scoped evidence only: the aggregate run also reported failures outside
these modules (shell compilation and a Mafia state-machine assertion). It does
not certify the entire application, later source changes or mobile runtime.
`git diff --check` and EN/AR XML name/indexed-placeholder parity also passed
(63 Dominoes, 60 Ghamza, 546 Word Impostor resource entries).

All three Rules surfaces now offer numbered quick-start guidance plus optional
full details, rather than leading with a large rules paragraph. The shared
`QuickStartSteps` is placed on a theme-matched `ParlorCard` because game tables
have deliberate dark backgrounds; otherwise Light preference would create dark
text on a dark table. Three new 320px/font-scale-2 Light-theme interaction tests
verified the compact guide and detail toggles. This is not pixel-level contrast
or screen-reader qualification.

## Remaining limitations and unverified risks

- No attached Android/iOS physical-device exercise, phone calls, screen lock,
  thermal/battery measurements, TalkBack/VoiceOver or multi-device LAN acceptance
  is claimed here. Existing #252 and #6 remain the appropriate interruption/LAN
  tracking contexts, not duplicate new-game issues.
- Authority replay reconstructs full histories and repeatedly copies bounded
  history lists. Worst-case persistence CPU/allocations on low-end mobile hosts
  need profiling; an incremental proof optimization must preserve replay and
  exhaustion validation, not skip it.
- No human playtest certifies pacing, entertainment, Arabic editorial quality or
  the topic bank's cultural familiarity. Those are acceptance tasks, not proven
  reducer defects. Seeded tests do not substitute for them.
- All games deliberately stop on expired required seats. No host migration or
  removal/replacement of participants mid-match was added.
- UI changes improve behavior and hierarchy, but do not alone constitute an
  exhaustive redesign or a claim that every screen has been visually qualified.
