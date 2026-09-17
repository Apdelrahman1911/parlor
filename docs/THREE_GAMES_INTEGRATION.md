# Egyptian Dominoes, Ghamza and Word Impostor

These three games use Parlor's existing same-LAN, host-authoritative multiplayer
system. They do not introduce a transport, server, permission, dependency version,
or protocol change. Android and iOS remain the mobile targets. The separate
[GitHub distribution pipeline](GITHUB_DISTRIBUTION.md) adds native Desktop
packaging without claiming Store or physical-device qualification.

## Architecture and implementation sequence

The implementation first inspected `GameDefinition`, reducers/projections,
`SessionController`, host/peer coordinators, the acknowledged start barrier,
process-owned room retention, recovery credentials, catalog bindings, lifecycle
privacy and the existing Back policy. Work then followed this order:

1. Research rules and choose explicit house rules below.
2. Add independent pure-domain game modules, strict codecs and tests.
3. Adapt the existing coordinators through a typed composition-root binding.
4. Register each game and build its own settings, gameplay and results UI.
5. Exercise complete games, malformed/duplicate commands, privacy, recovery,
   localization, layouts and animation; run repository/platform gates.

| Game/module | Stable game ID | Players | Settings |
|---|---|---|---|
| `game-modes/egyptian-dominoes` | `egyptian-dominoes` | 2–4 | Default/Draw/Block; target 51/101/151; four-player Default partnerships |
| `game-modes/ghamza` | `ghamza` | 3–12 | 1/2/3 attempts; 1/3/5 rounds |
| `game-modes/word-impostor` | `word-impostor` | 3–12 | Topic; 1/2/3 impostors; 1/3/5 rounds |

All use mode `standard`, game/codec/snapshot version **2**, and session protocol **4.2**.
Version 1 contracts are deliberately rejected, not silently reinterpreted:
Dominoes settings/scoring, Ghamza's removed score field, and Word Impostor's
personal awards are incompatible with the previous rules. All peers must update.
Actual admitted roster size determines player count; Start validates the range
and settings. Only Host/Join are offered: secret-role games are not presented as
solo or shared-device games. Existing games' local-play modes are unchanged.

Each module owns `domain/`, `protocol/`, `ui/`, `di/`, EN/AR resources and tests.
`composeApp/shell/game/*GameShellBinding.kt` supplies the definition, codec,
authority policy, settings and UI. The typed adapter under
`composeApp/shell/game/multiplayer/` handles identical room/start/recovery glue.
There are no new game branches in shared engine/session/networking/transport
production source. The root registry and shell-dispatch gate enforce this seam.

## Egyptian Dominoes — الدومنة المصرية

### Research and chosen rules

Research retrieved **2026-09-17**:

- [Pagat: Draw Dominoes](https://www.pagat.com/domino/line/draw.html), including
  its Egyptian/Iraqi variant with an ordered boneyard drawn from one end.
- [Pagat: Block Dominoes](https://www.pagat.com/domino/line/block.html), covering
  open-end play, blocked hands and scoring variations.

Egyptian tables differ on drawing, opening, scoring targets, partnerships and
double-out bonuses. This implementation is an explicit Egyptian **house-rule
baseline**, not a claim that one exclusive national ruleset exists:

- Double-six set: 28 unique tiles. The host privately shuffles/deals from a
  fresh seeded sequence. **Default** is selected initially; target **101** and
  **Individual** are the other defaults.
- **Default, two players:** seven each; exactly the Draw behavior below.
  **Default, three players:** remove only double-blank/double-zero **0–0** before
  dealing; nine each, 27 total, no stock or drawing. Other zero-containing tiles
  remain. **Default, four players:** seven each, no stock; choose Individual or
  Teams. These are owner-selected table rules, not a claim of a universal variant.
- **Teams:** opposite seats 1+3 versus 2+4. One canonical score is stored per
  side, not copied into two independently mutable player scores. A teammate's
  hand stays private. Setup validates exactly four actual players and Default
  mode; changing variants clears an incompatible team selection.
- Highest dealt double opens; if no double is dealt, highest pip sum, then
  highest end. Thereafter the previous hand's winner leads with any tile. A
  tied blocked hand rotates the leader.
- Match either open end. Doubles lie perpendicular but are not branching
  spinners. Every peer sees the same physical chain orientation, including RTL.
- **Draw**: with no legal play, draw from the fixed stock end one at
  a time until playable. No voluntary drawing. Pass only with no play or draw.
  **Block**: the undealt stock is unused; pass when no tile fits. With four
  players all 28 tiles are dealt, so neither variant has a draw stock.
- Going out scores all opposing sides' remaining pips; a partner's pips do not
  count against their team. A blocked hand compares combined side pip totals;
  the unique lowest side scores opponents' total minus its own. A tied lowest
  side scores nobody. The winning side's lowest-pip member leads next; seat
  order breaks a teammate tie. No double-out bonus is assumed.
- Targets are **51, 101, or 151**. In **151** mode, a side reaching **at least
  101** while every opposing side remains at zero wins immediately. This also
  applies to teams, and both teammates are match winners. The reducer ends the
  match automatically after awarding that hand; UI does not decide winners.
  A defensive 64-hand cap still finishes by highest score, allowing shared
  winners. Draw/pass/placement have exact
  round-token and move-number validation; scoring happens once per hand.

The wood/felt table, ivory pip tiles, opponent tile-back fans and connected
serpentine chain are code-native artwork. Accepted placements fly from measured
hand/seat positions, rotate and settle at the actual chain position. Draws
travel face-down from the stock. No optimistic tile removal occurs. Initial,
skipped or recovered snapshots never replay historical flights; reduced motion
settles immediately. Team labels list both partners and shared points on the
table/results, so color is not the only indication. The results screen retains
the table's visual treatment.

## Ghamza — غمزة

[Wink murder](https://en.wikipedia.org/wiki/Wink_murder) was consulted on
2026-09-17 for physical-wink/private-role and accusation variants. The requested
Ghamza mechanics take precedence: no detective, camera, automated wink or
drinking mechanic is included.

- Exactly **one Winker**, randomly reassigned privately each round. Everyone
  reveals only their own role and confirms readiness before social play starts.
- The wink happens in person. An ordinary player confirms **“I Was Winked At” /
  “اتغمزلي”**; the app records the next numbered attempt, announces only the
  recipient and updates remaining attempts. It cannot authenticate honesty.
- Settings grant each ordinary player one, two or three reports before they
  are out. An eliminated player remains a seated participant, not a spectator
  or a newly reusable room slot. The Winker cannot self-report a wink.
- When only one ordinary player remains, that player guesses any other
  original participant. Correct: the Winker loses/is eliminated; the guesser
  wins. Incorrect: the guesser loses/is eliminated; the Winker wins. Final
  elimination is derived from the result, not recorded as a fictitious wink.
  The normal result reveals the role and guess; abort does not.
- Matches have 1/3/5 rounds. The host advances results and may start a fresh
  same-roster rematch. **There is no scoring**, score field, scoreboard, cumulative
  champion, or score persistence. Everyone returns with full lives each round.
  The Winker is drawn again independently; the same player may be picked again.

The teal/mint social table uses a concealed eye motif, private reveal, a
confirmed report action, public report feed, attempt status and final guessing.
Public eliminations can naturally support deductions; that does not authorize
publishing the secret role map.

## Word Impostor — لعبة الإمبوستر

The offline bilingual bank contains **12 topics, 240 words and 216 questions**:
Food, TV Series, Movies, Football, Sports, Anime, Animals, Countries, Cities,
Games, Technology and Famous People. `WordTopicBank` owns stable word/question
IDs and plausible-choice families; `WordContentResources` maps them to EN/AR
resources. Add content in both places and extend bank/parity tests. Display copy
is not a wire identifier. Titles/names are text references, not licensed artwork
or endorsements; distribution/editorial review remains an owner gate.

1. **Private reveal:** ordinary players receive the word; impostors receive
   only their role and teammates. A strict ordinary majority is required:
   `2 × impostors < players` (two need at least five; three need at least seven).
2. **Automatic questions:** a freshly shuffled Hamiltonian cycle gives everyone
   exactly one asking and one answering turn, with no self-pair or duplicate
   directed pair. Every turn uses a distinct topic question, privately shown
   to the current asker. The asker confirms the spoken interaction. Questions
   do not include the selected word; a later round may reuse a bank question.
3. **Free discussion:** the screen clearly changes phase. The host opens
   voting when discussion is finished. There is no cosmetic or unenforced timer.
4. **Private voting:** everyone selects one other player, confirms once and
   cannot replace the vote. Only participation is public until all votes arrive.
   Then counts are revealed. Each ordinary player's own single vote targeting
   **any** impostor earns that voter **one point**. Incorrect votes earn zero;
   the top-K poll and ties are informational, not scoring conditions.
5. **Impostor guessing:** each impostor guesses independently from five shuffled
   options in the same topic and plausible family, including the answer. The
   full topic list is never displayed. Unfinished guesses and correctness are
   private; only the number submitted is public.
6. **Results:** reveal word, impostors, vote totals, guesses, awarded points and
   updated scores. Each correct impostor independently earns **one point** for
   their word guess, without canceling anyone's correct-vote point. Personal
   awards/correct-voter membership publish only after all guesses, avoiding a
   premature role or answer-confirmation leak. Round counts are 1/3/5; tied
   match scores share the win.

The ink/indigo/peach UI separates secret cards, pair-guided questions, discussion,
private voting, five-choice guessing and scored results. Reveals/selections
reset on phase/round changes, concealment and recovery, never in saved UI state.

## Authority, privacy and interruption

- Only the host reduces. Transport-bound actors, expected revisions, client
  sequences and the existing bounded duplicate ledger validate commands.
  Lifecycle actions cannot be decoded as player actions. UI reserves one
  pending submission synchronously; stale/rejected actions require an explicit
  fresh tap rather than an automatic non-idempotent retry.
- Each peer receives **public state plus its own recipient-bound private slice**.
  Seeds, stocks, full hands, role maps, unfinished votes and other private data
  stay host-only. Intentional public result reveals occur only at the documented
  result boundary. The host's screen also receives only its seated projection.
  This is a trusted-host model, not protection against a modified host binary.
- Strict bounded canonical JSON rejects extra/duplicate fields, wrong versions,
  recipient transplantation and impossible projections. Authority snapshots
  require full deterministic replay equality; peer snapshots do not contain
  replay history. Extreme history exhaustion ends safely instead of freezing.
- Admission freezes before the existing acknowledged start barrier. No late
  join, raw-IP join, spectator or host migration is added.
- Existing best-effort brief background retention and 120-second rejoin policy
  apply to every game. Inactivity covers private content immediately. A missing
  required seat pauses play; completed authenticated rejoin restores its state.
  Expiry/required-seat loss aborts without revealing unfinished secrets or
  awarding new points. Host exit ends the session; host process death is not
  recoverable authority migration. Peer cold-start resume uses existing protected
  rejoin credentials while the original host remains alive.
- Rematches reset applicable scores (Dominoes/Word Impostor), lives and hidden
  setup but advance a monotonic round token,
  so old-round commands cannot affect the fresh match. Changing players/settings
  requires a new room. Existing local saves are not read, migrated or deleted.
- Private UI is non-saveable and removed from accessibility semantics while
  concealed. Back dismisses a selection/reveal/help before shared Leave
  confirmation; repeated Back never confirms exit. EN/AR plural/placeholder
  parity, mixed-direction name isolation, 48dp controls and reduced motion are
  tested. Physical VoiceOver/TalkBack and tactile quality still need device runs.

## Verification

Focused tests include complete seeded matches/settings matrices, invalid and
duplicate actions, scoring/ties, rematches, full replay-budget exhaustion,
malformed wire/snapshots, recipient privacy, real coordinator start/recovery,
simultaneous commands, 100,000 rapid submissions and non-replayed outcomes.
Compose tests exercise EN/AR compact large-text layouts, concealment, choices,
results and measured Dominoes flights. Geometry tests cover chain connectivity,
overlap and bounds through all 28 tiles.

```bash
./gradlew :game-modes:egyptian-dominoes:desktopTest \
  :game-modes:ghamza:desktopTest :game-modes:word-impostor:desktopTest \
  :composeApp:desktopTest :shared:design-system:desktopTest \
  :composeApp:verifyGameShellDispatch --dependency-verification=strict
./gradlew productionCheck allTests --dependency-verification=strict
./gradlew productionAppleCheck --dependency-verification=strict
./gradlew productionIosSimulatorRuntimeTests --dependency-verification=strict
```

Use JDK 21 and the wrapper. See [project status](PROJECT_STATUS.md) for executed
evidence, not this command list. Unattended tests do not certify physical
Android↔iOS LAN play, social play, touch/gesture quality or Store readiness.
The existing [release gates](RELEASE_GATES.md), unresolved strict-protection
finding and disabled **Store** publishing workflows remain unchanged. GitHub
publication has its own protected signing, acceptance and provenance gates.

## Physical-device acceptance matrix

**Not executed for this implementation.** Automated coordinator tests and owned
Simulator UI tests do not replace these checks. At the local verification
checkpoint, `adb devices -l` reported no attached Android. The release owner
needs consenting testers and a same-LAN Android/iOS device group. Use synthetic
names and never capture hidden roles, words, votes, credentials or real saves
in shared diagnostics. Record source SHA, build identity, devices/OS versions,
locale, expected/actual result and non-private evidence for every row.

| Check | Required device exercise | Status |
|---|---|---|
| Bidirectional LAN | Android host → iOS peers, then iOS host → Android peers; each game at minimum and maximum seats. Wrong-game room codes and joins after Start must be rejected. | NOT TESTED |
| Dominoes table | 2/3/4 seats; Draw and Block; both chain ends, doubles, ordered draws, exhausted stock, blocked hands, scoring, rematch and synchronized placement flights. | NOT TESTED |
| Ghamza social play | 3–12 seats; 1/2/3 attempts; reveal/hide/readiness; cancel/confirm physical-wink reports, rapid simultaneous reports, elimination, correct/incorrect final guess and multi-round scores. | NOT TESTED |
| Word Impostor | 3/5/7/12 seats and supported 1/2/3-impostor settings; every topic; private word/team, unique question cycle, free discussion, sealed votes/ties, five-choice individual guesses, scoring/rematch. | NOT TESTED |
| Interruption/rejoin | Background or lock host/peer for 1/10/15/30 seconds; interrupt Wi-Fi, return before the 120-second deadline and verify unchanged state plus re-covered secrets. The 15-second retention window is best-effort, not an OS guarantee. | NOT TESTED |
| Terminal loss | Explicit Leave, expired required seat (including an eliminated participant), host exit and host process death. No unfinished secret reveal, new points, late seat replacement or authority migration. | NOT TESTED |
| Native permissions | Deny/re-enable local-network permission and return from App Settings on both platforms; retry the real room operation without bypassing permission or admission checks. | NOT TESTED |
| UI/accessibility | English/LTR and Arabic/RTL, mixed-direction names, compact/tablet layouts, large text, VoiceOver/TalkBack, haptics and reduced motion. Check iPhone/iPad edge gestures, Android predictive Back and guarded Leave without exposing covered private content. | NOT TESTED |

Resume-list deletion remains [issue #253](https://github.com/Apdelrahman1911/parlor/issues/253),
not a data-deletion change in these games. This matrix does not waive the
existing Strict Complete Protection, signing, editorial or Store-release gates.
