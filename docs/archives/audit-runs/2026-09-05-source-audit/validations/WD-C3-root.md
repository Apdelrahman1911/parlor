# WD-C3 — independent validation

**CONFIRMED DEFECT — Low, trusted-snapshot validation.** Finder `/root/whodunit_cont`; independent validator `/root`. Baseline commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. No ordinary gameplay producer or storage-authentication bypass is alleged. Prerequisite: an erroneous trusted producer/current authenticated snapshot (synthetic store in reproduction), or an invalid host projection. Applies to Whodunit Elimination, not Mafia or Classic Vote.

## Locations

All paths are absolute under `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/`:

- `src/commonMain/kotlin/com/parlor/games/whodunit/domain/state/WhodunitStateValidator.kt:27–62,143–151,159–240,488–565,613–729,762–775`: independently read entire1084-line file. Active phase validation never requires more than two survivors.
- `src/commonMain/kotlin/com/parlor/games/whodunit/domain/reducer/WhodunitReducer.kt:653–733,788–821,911–965`: canonical final-two termination and defensive ballot fallback.
- `src/commonMain/kotlin/com/parlor/games/whodunit/domain/rules/WhodunitRules.kt:74–85`: Elimination bound is player count minus two.
- `src/commonMain/kotlin/com/parlor/games/whodunit/snapshot/WhodunitSnapshotCodec.kt:40–52,67–99`: current encode/decode validation, not a legacy migration.
- `src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt:246–281,341–359,413–489,787–807`: production load/content validation and local-controller installation.
- `src/desktopTest/kotlin/com/parlor/games/whodunit/domain/WhodunitRulesInvariantTest.kt:359–440`: explicit counter-evidence, discussed below.

Reviewed file hashes are in `coverage/reviews-root.jsonl` and `validations/WD-C3-source-hashes-root.json`; fixture/init hashes in `evidence/repro-pending-inputs-01.json`.

## Executed proof

The independent root fixture loads the **actual six-player bundled Last Dinner** via the production case validator; uses seed73, synthetic seats and the real reducer; completes intro, briefing, dossiers and four legal innocent-elimination rounds. The canonical result is Reveal/KillerWins(SurvivedToFinalTwo), four eliminations. It round-trips through the codec.

Only the terminal presentation is then deliberately malformed: change phase to Round4, remove verdict, replace the vote with a fresh canonical two-survivor ballot. Assignment, seed, content identity and evidence history remain unchanged; current engine version and matching outer phase are used. Production current codec, `loadResumedSession`, `validateResumedSessionForCase` and every own-private peer validator accept this state.

The remaining innocent votes for the killer; killer abstains; host CloseVote produces **PlayersWin and five eliminations** in round4. The next codec encode throws `IllegalArgumentException("Elimination history exceeds completed rounds")`. Thus accepted current recovery can alter a completed outcome and produce an unsaveable state with ordinary subsequent reducer actions.

Executed in `evidence/repro-pending-01`: `WD_C3FinalTwoRecoveryAuditTest` ran2 tests. Full production-boundary witness **PASS**; requirement that the malformed current snapshot be rejected **FAIL**, as expected. Raw XML/log, baseline and cleanup receipts retained. No real saves, players, devices or signing used.

## Counter-evidence and expectation

The normal reducer terminates at two survivors, so ordinary current gameplay does not generate the malformed input. Existing invariant test359–440 deliberately constructs an impossible five-player active state, expects snapshot acceptance, and exercises an all-abstain fallback. Root considered it: defensive reducer handling is useful but does not justify current recovery reopening a terminal game, especially when a different legal ballot changes the outcome and violates the validator itself. The current-source termination rule plus the repository's explicit no-repair/no-impossible-current-snapshot contract establish the expected rejection. No broader attack severity or claimed introduction commit follows.

## Recommendation, not implementation

Reject active Elimination Round/TiedRevote states with two or fewer surviving required seats at canonical and peer validation boundaries. Decide legacy compatibility separately; do not normalize malformed current data. Test real six-seat bundled current-codec/load/content/peer rejection, valid terminal recovery, early end and legal replay. Keep any defensive reducer fallback test separate from admissible current-snapshot tests. No game rules or production files changed.
