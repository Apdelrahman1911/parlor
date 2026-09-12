# WD-C3 — Active final-two Elimination snapshot accepted

Status: INDEPENDENTLY CONFIRMED DEFECT — Low, trusted-snapshot validation. Original finder /root/whodunit_cont; independent validator /root. Root report `validations/WD-C3-root.md` reopened and read; executed production-boundary witness PASS and expected current-snapshot rejection FAIL in `evidence/repro-pending-01`. Not a demonstrated normal-game producer or filesystem-authentication bypass.

Baseline main 3625d0663ba6eb51338cbd5f9dc45f859ec18846, tree db7f3d2afe73a13628296daee2cce71165eebc8d. All paths below are relative to `/Users/abdelrahman/Projects/parlor/` (absolute root supplied here).

## Deterministic source-level proof (subsequently independently executed by root)

1. Use real bundled `last-dinner`, six seats, Elimination mode. Legal role/intro/briefing/dossier setup followed by four rounds of clue, discussion, vote; each ballot accuses a distinct innocent, accused abstains, everyone else votes for them. Acknowledge only rounds1–3. `WhodunitReducer.kt:788–821` reaches Reveal/KillerWins(SurvivedToFinalTwo), four innocent eliminations and four deterministic clues. Rules maximum is6−2=4 (`domain/rules/WhodunitRules.kt:74–85`).
2. Construct a **current-schema**, otherwise identical authenticated synthetic snapshot: phase `Round(4)`, verdict null, and fresh `VoteState.Collecting(isElimination=true, ballotPlayerIds=twoSurvivors, candidatePlayerIds=twoSurvivors)`. Preserve assignment, seed, clues, own dossiers, content identity and session; set outer phaseId to Round4.
3. `domain/state/WhodunitStateValidator.kt:27–62` dispatches all guards. `validateVote:488–565` accepts canonical survivor ballot; `validatePhaseShape:613–729` checks eliminated count<=currentRound and correct clues/no timer but **does not reject active final-two states**; terminal guard762–775 merely rejects active verdict/active eliminated killer. No other field changed. `requireValidForCase:159–240` verifies count/content/assignment/clue history/timer, not terminal survivor cardinality.
4. `snapshot/WhodunitSnapshotCodec.kt:40–52,67–99` encode and current decode accept canonical JSON and these structural guards (not legacy migration). `ui/flow/WhodunitGameFlow.kt:413–465,474–489` accepts game/session/version/phase/mode/content identity and structural+case-bound guards. `246–281,341–359,787–807` installs restored state into actual PassAndPlaySessionController. UI router `WhodunitPhaseRouter.kt:901–909,1003–1102` displays a new two-seat ballot for a game already finished.
5. Extra vote can change/extend already-decided outcome. If remaining innocent votes for killer and killer abstains, `WhodunitReducer.kt:653–733,788–803` adds a fifth elimination and emits PlayersWin. Subsequent persistence fails `StateValidator.kt:623` (5>4), despite accepted restored input. If both abstain the special fallback911–965 returns the previous final-two result; that fallback does not reject the initial malformed current snapshot.

All paths above under `game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/` unless otherwise specified.

## Counter-evidence and limits

- Current normal reducer immediately ends when second survivor reached; no ordinary action/UI path to malformed snapshot proved. Store authentication and file protection are distinct controls and were not bypassed. Scenario requires synthetic authenticated malformed state (test store) or an erroneous trusted producer.
- `src/desktopTest/kotlin/com/parlor/games/whodunit/domain/WhodunitRulesInvariantTest.kt:359–440` explicitly creates the corresponding five-seat impossible state at417–428, expects snapshot boundaries to accept it, then all-abstains. This is counter-evidence of possible deliberate defensive reducer support, not proof current snapshots should accept it. Current-snapshot contract is no compatibility repair; legacy and current codec both use same validator. Test is narrower than shipping six-seat proof above.
- Recent commits inspected: cf589ec0 (terminal clue/verdict binding),82ad9d57 (elimination terminal history); no explicit current-schema exception found in inspected paths. No claim of introducing commit.
- Peer validator uses same phase shape (`StateValidator.kt:143–151`), so malicious/invalid **host** projection can also be accepted, but no peer authority escalation, role leak, or ordinary host output proved.
- Finder ran no Gradle/device/runtime reproducer. Root independently reopened full validator/callers, considered the defensive-fallback counter-evidence, and ran2 isolated synthetic tests through actual codec/load/case/peer/reducer paths. No physical-device or real-save evidence is claimed; see the independent report and cycle receipt.

## Suggested remediation/test

For active Elimination Round/TiedRevote require more than two remaining required seats, consistently for canonical and peer state. Confirm historical bare snapshots separately before making a compatibility decision; do not silently normalize malformed current data. Add real six-seat bundled-content negative current codec/case-bound/peer tests; retain legal final-two terminal save/recovery and early-end cases. Existing synthetic fallback reducer test should explicitly distinguish defensive reducer behavior from admissible current snapshot. Do not weaken rules or move game-specific logic into shared code.
