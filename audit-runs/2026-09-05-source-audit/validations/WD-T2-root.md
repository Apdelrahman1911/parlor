# WD-T2 — independent validation

**TEST/EVIDENCE GAP**, Low, not an application defect. Finder `/root/whodunit_cont`; validator `/root`; main `3625d0663ba6eb51338cbd5f9dc45f859ec18846`.

Read all467lines of `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/flow/TickerAndRerollTest.kt` plus all62lines of production DiscussionTickerLoop.kt. The named two-loop test272–297 launches exactly one loop at288; it does not execute a two-owner scenario or a Compose lifecycle. Its comments do not constitute coverage. Reroll session identity411 compares the original immutable local argument to its literal constructor333, not an observed session/snapshot identity. Case identity372 compares two post-reroll projections rather than a before/after invariant.

Counter-evidence: tick decrement/pause/expiry and reroll assignment/generation/event assertions are genuine. Production deliberately has a single ticker owner; this finding does not impose idempotent concurrent ticking as a new game rule and does not prove duplicate tickers occur. Other identity tests may cover the wider contract. The observation is limited to overclaimed named scenarios/tautological assertions in this test file. No mutation or device test executed.

Rename the single-loop test or add an actual lifecycle/owner test; compare captured pre-reroll identity with post-reroll controller or snapshot values. Do not weaken rules to accommodate an invented test expectation.
