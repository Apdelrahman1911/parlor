# Android lint cache ownership — 2026-10-27

## Confirmed finding and repair

[#271](https://github.com/Apdelrahman1911/parlor/issues/271) tracks a P2
verification-reliability defect, distinct from #161's advisory-text volatility.
The combined verification-boundaries candidate `40720c4a` failed only its strict
lint inventory gate. `lintAnalyzeRelease` restored cached partial analysis;
the final `lintReportRelease` ran freshly. The partial XML stored root Gradle
locations as `$HOME/.../checkout-A/...` while module paths used project variables.
In checkout B those findings still pointed to A. Rejecting them was correct.

Reviewed AGP 8.13.2 source marks `AndroidLintAnalysisTask` cacheable and excludes
the project directory from analysis inputs. Pinned lint 9.1.1 (implementation
32.1.1) serializes/deserializes these path variables without relocating root
findings. Commit `a25985e1` adds canonical `parlor.lintRepositoryRoot` to every
Android partial-analysis producer, including test/vital variants. It changes
the producer cache key, not the reports, source guard or accepted warnings.
Compilation and same-root caches remain enabled. Re-review this internal task
API during an AGP/lint upgrade; no dependency/toolchain pin was changed.

## Actual committed-source exercises

All following exercises used clean source `141408e0b45369827d2a8825624211739cf41c0f`,
tree `e9482db69a4bdcf7e7fbbb1e1ad999a941ae114b`, Linux x64 / JDK 21, the wrapper,
strict verification, bounded commands and separate owned Gradle registries.

| Exercise | Executed result |
|---|---|
| First corrected lint run | PASS, 1m37s; 31 producer inputs invalidated; exact 37-warning inventory and current-root paths |
| Same-root repeat | PASS, 10s; all 31 analysis tasks up-to-date with unchanged keys |
| Same-root missing output | PASS, 8s; removing only the owned `lint-definite.xml` restored `:composeApp:lintAnalyzeRelease FROM-CACHE`, byte-identically |
| Fresh second worktree | PASS, 1m28s; all 31 analysis keys differ, no old-root analysis restored; 222 unaffected tasks restored from cache; all 37 paths belong to the new root |
| Stale-root and parent-traversal report faults | Both actual Gradle verifier invocations rejected the injected path; original report restored; source unchanged |
| `productionCheck allTests --continue` | PASS, 3m4s; 714 Python cases (one environment skip); 3,185 current Desktop/Android report cases, zero failures/errors, three existing real-LAN skips |

Valid unchanged-input outputs were reused in the aggregate: these are not
3,185 newly executed or unique behaviors. The new Kotlin cache contract executed
in the 342-case transport Desktop suite. Linux cannot execute Apple runtime tests.
The report faults deliberately exclude lint regeneration only to test its
existing verifier against synthetic reports; ordinary production checks still
execute the complete lint dependency graph.

## Custody and integration boundaries

Checks-log SHA256s: failed aggregate
`0c706ee90ba80830416a88c27d832b9d1cef537eabaeea838e128187194356cb`;
corrected full aggregate
`3b1449172cfb9d3379b9cb68e37ea9fa88bdc16b1d4fb1a1963636f7a9438ed9`;
second-worktree lint
`c569a49aaf4eabe7f0d8ce4ce535a43dacb3fcd2a6785f67121d13ffbb6ff341`.
Local custody beneath `.git/agent-custody/overhaul-2026-10-27/diagnostic-controls/`
retains receipts, research archives and failed evidence. No shared cache was
deleted, seeded or bypassed to obtain a pass.

This is a local candidate, not a merged or hosted-qualified change. Subsequent
documentation edits require affected checks again. #268/#269/#6 integration,
normal protected merge and post-merge verification remain pending. The separate
free-runner benchmark in #270 stays frozen and running; this repair neither
starts competing macOS jobs nor establishes its winner. No physical-device,
strict iOS protection, power/thermal, signing or Store acceptance is claimed.
