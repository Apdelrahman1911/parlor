# Whodunit WD-C3 regression — independent test-tooling follow-up

**APPROVED (scoped test-only change).** Author `/root`; independent reviewer `/root/factory_review`. This does not create a seventeenth application defect or replace the earlier WD-C3 production-fix review.

## Exact scope and source

`/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/snapshot/WhodunitFinalTwoRecoveryTest.kt:298–311` is the only changed block in this follow-up. Entire349-line test reopened, including every assertion. Full definition and SnapshotStore plus actual production loader/case validation `/Users/abdelrahman/Projects/parlor/game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitGameFlow.kt:350–490` reopened. Original WD-C3 candidate/independent-validation dossiers reread.

- Branch `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, Git tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; dirty remediation source, not identified by commit alone.
- Before test SHA256 `58e56cfae03d3f13fa7d62b866ae4d671dc8289b8766d04f6cbed2bcb3e6b9c0`; after `b6f36aa2ea3615dcee2c563087d67120e321146d4ae0a2e40cb0f696e865d906`.
- Frozen final source manifest `e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7`.
- Removing only the four added comment/reference lines and restoring the direct call reconstructs the complete original file hash from `combined-production-03`. Ineffective `definition: WhodunitDefinition` annotation was fully reverted. Reviewed production-file hashes also match final receipt. Machine-readable companion contains exact diff, hashes/ranges, raw evidence references and task lines.

## Why the correction is valid

`assertValidRecovery` genuinely requires suspension: production `loadResumedSession` is declared suspend at413 and calls `SnapshotStore.load` at418 (and delete at437 on its retired-Solo branch). Removing suspension or replacing the loader would be wrong. The final helper binds **the same production function** via `val load: suspend (...) -> Result<ResumedSession, DataError> = ::loadResumedSession`, then calls it with the same arguments. This is not a fake suspend wrapper, mocked loader, artificial yield or weakened assertion.

No test body, scheduler, payload, assertion or positive/negative control changed. Actual codec/reducer/content/loader/own-private validator checks remain. Nine tests cover all reopened substates, current encode/decode, legacy rejection, case-bound and own-private peer checks, plus legal final-two terminal/postgame/replay, legal killer-elimination and early-end saves. Normal reducers already terminate at final two; these negative tests require malformed trusted input, not an invented normal-game producer or storage-authentication bypass.

Detekt1.23.7's tagged rule counts resolved suspend descriptors; compiler and analyzer environments differ (repository Kotlin2.4.10 versus Detekt tagged compiler2.0.10). Tagged KMP source sets/classpath and parser environment were inspected. The exact erroneous binding-context/metadata/friend-path condition was **not instrumented**; no specific decoder bug is claimed. An explicit real-function reference restores analyzability without disabling or suppressing a rule. Research receipts retain official tagged URLs/access times/hashes.

## Independently inspected verification

| Root-owned cycle | Whodunit tests | Type-aware result | Whole cycle |
| --- | --- | --- | --- |
| `combined-production-03` |9fresh PASS,0skip| `detektDesktopTest` redundant-suspend diagnostic at298 | FAIL, retained |
| `whodunit-type-aware-01` |9fresh PASS,0skip| Same diagnostic despite definition annotation | FAIL, retained; ineffective change reverted |
| `test-type-aware-green-02` |9fresh PASS,0skip| Fresh `detektDesktopTest`, zero findings; `typeAwareStaticAnalysis` aggregate completes | PASS, exit0 |

Raw XML, timestamp, Gradle task lines, source before/after manifests and exit receipts—not only parent summaries—were inspected. Final `desktopTest` and `detektDesktopTest` are neither FROM-CACHE nor UP-TO-DATE. Other aggregate dependencies may be cached; this does not claim that every task was fresh. The final cycle also executed toast tests, but their change belongs to another independent reviewer. Combined-production-04 was in progress; no combined success is claimed here.

## Compatibility and cleanup

This follow-up changes no shipping source, game rules, protocol4.2, secrets/projections, save schema, content version, or settings. JVM tests are not device/runtime or Store proof. All three inspected cycles retain stop exit0, raw `No Gradle daemons are running`, no remaining owned workers/outputs, no cleanup errors and unchanged cycle source. Final cleanup completed `2026-09-05T22:23:21.739557+00:00`. Root had begun another coordinated build afterward; the reviewer did not interrupt it.

Reviewer launched no build/background worker, touched no production/test/config source or Git state, and generated only this compact evidence. Full combined/native/external verification remains root-owned.
