# DS-C02 — independent validation

**CONFIRMED DEFECT — Low.** Finder `/root/mafia_cont`; independent validator `/root`. Version: commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; unchanged production source. Shared Compose implementation affects Android/iOS and the Desktop development target. Desktop production-component reproduction executed; mobile runtime/device timing not executed.

## Source and expected behavior

- `/Users/abdelrahman/Projects/parlor/shared/design-system/src/commonMain/kotlin/com/parlor/designsystem/components/ParlorToastHost.kt:75–101,126–191` (SHA256 `555c56e57bfc6a7173d9034136f6a3a4de1dce9a068a66b3c04ca4da8a32d3f9`). Full file reopened independently.
- `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/App.kt:81–89,320–323` retains one state/host for the application.
- `/Users/abdelrahman/Projects/parlor/composeApp/src/commonMain/kotlin/com/parlor/app/shell/settings/SettingsScreen.kt:77–83,111–129` and game peer-flow callbacks legitimately enqueue asynchronous failure/outcome notifications.

The public `show` API enqueues a new, non-coalesced notification to be presented and automatically expired. This expectation follows its state/host implementation and actual caller use for errors, not an invented game requirement.

## Complete failure path and reproduction

1. Toast A receives ID1; host remembers `visible=true` and launches an effect keyed1.
2. Its own timer sets `visible=false`, waits the exit duration and internally dismisses A.
3. A separate producer calls public `show(B)` before an empty composition frame. Because the live queue is empty, B receives ID1 again.
4. StateFlow/Compose can conflate the intermediate empty state. The same keyed composition survives, retaining false visibility and an already-completed effect. B is not displayed; no new expiry effect is launched.

Root independently constructed and executed `reproducers/design/DSC02ToastIdentityReuseTest.kt` against the unchanged production state/host/theme. An Unconfined synthetic producer controls only the legal arrival schedule through public `show`, never `dismiss` or private visibility. Virtual time drives the production timer. Assertions44–45 **passed**, proving automatic dismissal ran and the replacement was actually queued. Assertion47 **failed** because no replacement semantics node existed. The subsequent expiry assertion did not execute; missing timer restart is source-proven rather than falsely reported as a second observed assertion.

Evidence: `evidence/repro-pending-inputs-01.json`, `evidence/repro-pending-01/receipt.json`, `test-receipts.json`, and the DS-C02 test XML under its `reports/` tree. One test ran, one expected failure. This is not an ordinary repository-suite failure. Cleanup completed; Gradle stop0, no task outputs left.

## Counter-evidence and independent conclusion

IDs are unique within the current bounded queue; CAS update is pure. Neither property prevents identity reuse between distinct toast lifetimes. Normal events separated by a rendered empty frame work. Same-text coalescing does not apply to the reproducer's distinct text. No speculative game mutation, privacy leakage, unbounded queue, or transport defect is implied. Existing queue-only tests do not mount the effect lifecycle. Pinned runtime effect-key/conflation references are retained in `evidence/design-toast-api/` and `evidence/androidx-runtime-1.10.5-LaunchedEffect-excerpt.txt`.

Recommendation only: assign lifecycle-unique identities independent of live queue contents, with atomic update and wrap behavior; keep bounded queues. Add same-frame auto-expiry/replacement tests for distinct and identical text, concurrent producers, eviction and stale timers. Preserve session/navigation ownership. No fix implemented.
