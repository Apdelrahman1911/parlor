# M-C01 — Doctor recovery history can contradict the last resolved night

- Finder: `/root/mafia_cont`; independent validator: `/root/whodunit_cont`.
- Source: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; no tracked modifications.
- Classification: **CONFIRMED DEFECT**, **Low, defense-in-depth semantic validation** (source-level proof, independently approved in `validations/MF-C1-whodunit_cont.md`). Root-owned isolated execution completed; see execution evidence below.
- Affected: Mafia local/pass-and-play snapshot encode/decode/resume on Android/iOS and development Desktop. **Prerequisite: a malformed but successfully authenticated current snapshot**, such as synthetic fixture or a future app-side producer defect. No normal gameplay producer of the contradiction, remote exploit, or cryptographic bypass demonstrated.

## Expected and actual

The current codec explicitly rejects non-reducer-reachable state. Default settings prohibit consecutive protection of the same player. A snapshot whose Doctor private `previousDoctorProtect` contradicts the latest host night record is not reducer-reachable and should be rejected, not silently accepted. Current validation checks each structure separately; null or another valid player in private history is accepted despite the contradictory retained night record. On resume the Doctor can then repeat a protection the actual history forbids (or incorrectly loses a permitted choice).

## Complete path and deterministic witness

1. Start legal six-player seed43 game with default roles. Acknowledge roles and advance to Night1. Doctor protects living non-self X, other players explicitly skip. ResolveNight. Both private Doctor history and `nightLog.last().doctorProtect` become X from the same `effectiveDoctorTarget`.
2. Everyone acknowledges; host opens discussion/vote; all abstain; close/ack/advance to Night2. Normal advancement preserves private X. Repeating X is correctly a reducer no-op.
3. Synthetic copy changes only `privatePerPlayer[doctor].previousDoctorProtect` to null. Roster, settings, other private fields, host records, announcements and phase remain unchanged.
4. Recovery119–124 allows null (and any legal non-self ID);187 only enforces null in Night1. History346–423 validates the retained suffix internally using a local prior-protection variable, then returns only a mortality equality; it never binds the final record to the Doctor private slice. Other phase/target guards remain satisfied. Codec28–47 therefore encodes and decodes the contradictory state canonically.
5. Production `loadMafiaResumedSession` decodes after store authentication/metadata validation; `MafiaGameFlow` installs it as restored local canonical state. The local Doctor picker and `SubmitDoctorProtect` both rely on the corrupted private history. Repeat-X is accepted. NightResolution also receives that history from reducer389–398.

Reproducer: `reproducers/MC01DoctorHistoryRecoveryTest.kt.txt`, two tests: witness acceptance/change in legal actions and regression expectation rejecting malformed encoding. Source-set injection/execution is root-owned; no execution was performed by finder.

## Counter-evidence and boundaries

- Normal reducer writes both fields from the same resolution value; no spontaneous corruption path demonstrated. Authentication prevents ordinary raw disk edits from reaching the codec.
- PostGame intentionally clears private gameplay state and must remain exempt from nonterminal binding. Missing/dead Doctor and skipped previous nights require correct null semantics. The bounded suffix always retains the newest record.
- Peer snapshots intentionally exclude host history and all other private slices. Do not expand peer payloads to fix this local canonical validation defect.
- Existing tests400–453 exercise pregame/first-night history, not later private/log disagreement. A passing ordinary round-trip test does not cover this adversarial case.
- Independent validator reopened entire recovery/codec/observable validator/rules/settings plus callers and reached the same conclusion; five-player independent source witness avoids depending on finder fixture.

## Recommendation and regression scope

Bind the nonterminal assigned Doctor's private previous protection to the latest resolved night record, with null before any resolved night and terminal cleanup handled explicitly. Reject contradictions instead of repairing them. Cover null/wrong-valid-target mutation, every nonterminal phase, skipped nights, absent/dead Doctor, capped history, valid round trips and unchanged default no-repeat reducer rules. No implementation performed.

## Source locations and SHA-256

- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/snapshot/MafiaSnapshotRecovery.kt` lines 35–64, 119–124, 176–218, 346–423, 467–478; SHA-256 `a2d00bc13749ddbce8759174ccc7729ace240d4a0d23f806306860c23ad15121`.
- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/snapshot/MafiaSnapshotCodec.kt` lines 28–47; SHA-256 `9d37b28b40c2a295afb5cfd4889e39a3803f8ad86623c8c37320d7c00cf4f20c`.
- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/domain/reducer/MafiaReducer.kt` lines 279–304, 386–404, 445–475, 775–801; SHA-256 `2ff9754d497b8258613badb057ab915a6d457786c56b0d5e725f2ba0053e56e2`.
- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/passandplay/MafiaGameFlow.kt` lines 117–177, 243–263; SHA-256 `8f250d62f8afb48e407c30e5382eb4758bc4ad53dee5168af8e3452ad3e840f6`.
- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/passandplay/MafiaPassAndPlayPhaseRouter.kt` lines 388–402; SHA-256 `3df606724bceca7d5a78d6479bcdc44eec8c3f7f135228ccdd308dfc837c42ae`.
- `/Users/abdelrahman/Projects/parlor/game-modes/mafia/src/desktopTest/kotlin/com/parlor/games/mafia/snapshot/MafiaSnapshotRecoveryTest.kt` lines 400–453; SHA-256 `19f594d105ba8a352922a361a6647ced90ee6c47a30c8f7f5fdcf73187e09de7`.

## Executed verification evidence (root-owned lane)

`evidence/repro-mafia-01/receipt.json` pins exact source, command, timestamps and cleanup. `test-receipts.json` and preserved XML in `reports/` show 2 tests, 1 failure, 0 errors, 0 skipped. `witnessAcceptedContradictionReenablesIllegalConsecutiveProtection` **PASS**; `regressionCodecShouldRejectDoctorHistoryContradictingLastEffectiveProtection` **FAIL** at fixture59 because no IllegalArgumentException was thrown. This is the expected falsification of the codec invariant, not a newly introduced application test failure. Cycle start2026-09-05T07:10:07Z, finish07:10:36Z, exit1. Gradle stopped exit0; exact task-owned outputs removed; cleanup errors[]/remaining outputs[]; source commit/tree/tracked status unchanged. Synthetic reducer/codec JVM proof only, not device or authentication-bypass evidence.
