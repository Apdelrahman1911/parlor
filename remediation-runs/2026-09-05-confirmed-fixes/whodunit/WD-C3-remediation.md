# WD-C3 — Author remediation review

Author `/root/whodunit_cont`; baseline `main` at
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`. **Patch and tests are written;
execution and independent patch validation remain pending.** No commits,
branches, version changes, or original audit evidence changes.

## Evidence and root cause

Original source proof and independent root validation are preserved in
`audit-runs/2026-09-05-source-audit/candidates/WD-C3-whodunit_cont.md` and
`validations/WD-C3-root.md`. The original isolated reproducer and root-owned
`evidence/repro-pending-01` receipts remain authoritative for the pre-fix
execution, not proof these new tests pass.

The Elimination reducer immediately enters a terminal result when only two
survivors remain. Previously, the structural validator admitted an active
round/revote at that point. A malformed trusted/local producer could remove
the verdict, reopen a ballot and then reverse the decided outcome or exceed
the authored round bound. This is not a legitimate saved intermediate state.

## Bounded correction

`WhodunitStateValidator.validatePhaseShape` now requires **more than two
non-eliminated roster members** for Elimination `Round` or `TiedRevote`.
The existing connection validator runs first and rejects dropped active seats,
so this count matches the active required roster. Existing duplicate/unknown
elimination guards also run before it.

The same cross-field check is used by canonical encode/decode, case-bound
local resume and generic/case-bound peer validation. It rejects malformed
state rather than inventing a winner or normalizing it. Legal terminal
states with two survivors are unaffected, including elimination of the
killer on the last round. Classic mode and all reducer transitions are
unchanged. Elimination FinalVote remains rejected by its existing mode guard.

## Tests written (not executed by this agent)

New `game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/snapshot/WhodunitFinalTwoRecoveryTest.kt`
contains **9 tests**. A strict validator loads actual bundled six-seat
`last-dinner`; seed 73 drives real legal reducers through the final
three-survivor ballot. A valid terminal state is then deliberately malformed
into Round Idle/Collecting/Resolved or TiedRevote Tied/Collecting. Tests cover:

- Current encoder; raw canonical current payload decoder independent of the
  encoder guard; bare legacy decode; case-bound recovery; actual
  `loadResumedSession` using an isolated synthetic store; all six own-private
  generic and case-bound peer boundaries.
- Rejection checks at the throwing boundaries require the **specific final-
  two invariant message**, not merely any exception from another invalid field.
- Positive full recovery/peer checks for the last legal three-seat ballot,
  final-two KillerWins, last-round PlayersWin, PostGame, replay generation
  reset, and explicit early end with/without disclosure.

Existing `WhodunitRulesInvariantTest` deliberately reopened a final-two
ballot and incorrectly asserted it was a valid snapshot. It now explicitly
requires rejection while preserving the existing defensive reducer fallback
test. Raw interim abstentions exercise that fallback; its eventual valid
terminal result is still checked at all snapshot boundaries. No test was
deleted/ignored and no valid-state assertion was relaxed.

## Author review and execution request

Reviewed the full validator during implementation and its relevant canonical,
peer, vote and connection call paths again; reopened codec/format, real local
loader, final-two reducer termination, original issue/validation/reproducer,
the changed existing fixture and all new test lines. Current hashes and
changed ranges are recorded in `source-review.json`. Own whitespace diff
validation exited 0; compiler/test success is **not claimed**.

Suggested root-owned strict batch:

```sh
./gradlew :game-modes:whodunit:desktopTest \
  --tests '*WhodunitFinalTwoRecoveryTest*' \
  --tests '*WhodunitRulesInvariantTest*' \
  --tests '*WhodunitSnapshotValidationTest*' \
  --tests '*WhodunitLegacySnapshotGoldenTest*' \
  --tests '*WhodunitResumeReconstructionTest*' \
  --dependency-verification=strict
```

Root must also run relevant complete-game/peer tests and broader combined
checks. Root owns build artifacts/daemons and mandatory cleanup. This author
ran no Gradle, compiler, app, Xcode, device or process-management command.

## Regression and compatibility assessment

No field/schema/engine/protocol/content version changes. Exact protocol 4.2,
host authority, own-private projections and deterministic seed behavior are
preserved. Valid current and legacy snapshots should remain readable; only
reducer-impossible active-final-two inputs become rejected. Existing legacy
goldens must be executed before verification is complete. No physical-device,
Store or actual encrypted-storage evidence is implied by a synthetic store.
Independent patch validator/result: **pending root assignment**.
