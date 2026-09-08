# WD-T2 — Ticker/reassignment test assertions do not exercise their stated claims

> **Final independent disposition (2026-09-05): TEST/EVIDENCE GAP — Low.** Validator `/root`; see `validations/WD-T2-root.md`. Ticker/reroll assertions do not exercise their stated claims; no shipping ticker/assignment defect is thereby established. Original candidate text below is preserved; its pending language is superseded. Administrative banner added by `/root/whodunit_cont` at `/root` request, not a new source approval.

- Finder: `/root/whodunit_cont`; independent validator: PENDING.
- Baseline: main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`; reviewed test SHA-256 `84c99331d6d8f60ea0edce8e14fd373b49627ab213015dae9e3a8ad06824b07f`.
- Proposed classification: TEST/EVIDENCE GAP, Low. Not a shipping application defect; no runtime failure claimed.
- Path: `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/flow/TickerAndRerollTest.kt`.

## Source-level proof

1. `ticker_does_not_double_tick_when_two_loops_share_a_timer_id` (272–297) only launches **one** loop (288). It duplicates the one-loop decrement assertion at182–199. It cannot verify the named two-loop scenario or the Compose LaunchedEffect ownership guarantee; the comment explicitly admits dependence on a guarantee it does not exercise.
2. `reroll_changes_role_assignment...` (411) compares the unchanged local input variable `sessionId` to its literal initializer333. It never reads the controller/snapshot identity after reroll. This assertion cannot detect identity mutation.
3. Case identity assertion372 compares two reads from the same after-reroll state (host projection vs public projection), not before vs after. A reducer mutation of case identity shared by both projections would not be detected here.

## Counter-evidence / impact limits

Production currently has a single LaunchedEffect owner per rendered round; this candidate does not establish duplicate tickers can occur. Other tests cover canonical session identity/content validation, multiple seeds, full replay, and assignment generation. Assertions on seed, mappings and events in the same test are meaningful. Defect count must not imply these gaps prove broken production behavior.

## Suggested remediation

Rename the one-loop test to its actual scope or add a deliberate two-owner/lifecycle regression test if single-owner correctness is the concern. Capture identity before reroll and compare to actual post-reroll controller/snapshot values. Do not invent duplicate-ticker idempotence as a game rule; establish the intended owner contract first.

No test run/mutation test was performed by this reviewer. Root owns the shared build lane.
