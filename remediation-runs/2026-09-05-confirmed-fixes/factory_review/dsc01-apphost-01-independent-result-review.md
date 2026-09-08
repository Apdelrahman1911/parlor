# DS-C01 app-host attempt 01 — independent result review

**Reviewer:** `/root/factory_review`; operator `/root`; harness author `/root/native_fix_review`.

## Verdict

**Runtime: EXECUTED FAIL. Cleanup: PASS.** This attempt does **not** verify the DS-C01 actual-app restart/System behavior. Do not treat it as unexecuted, a repair PASS, or an app-crash reproducer.

Source `e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7` and exact reviewed controls `82c4efd11d4c9885423066e6318967321c5dc1a7c3afe550cae42c3ff1cdb375` match before/after. Branch `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`, dirty diff `60b555c0dd323a086e959ffbc5aa3fd7c6629ae1d0ad016021572e7ae272e4dd`. No shipping edits occurred.

## What actually ran

2026-09-06 00:57:11–01:01:09 UTC; unsigned Debug `com.parlor.app.debug`, fresh owned iPhone17Pro iOS26.5/23F77 arm64 simulator. Xcode exit65. **One** exact XCTest discovered/executed; **zero** passed, **one** failed, **zero** skipped/expected failures. Raw xcresult summary and test tree agree.

Actual app reached foreground, `parlor-home-brand` existed, and the copied observer sampled original root native `force_ltr` with System/English preferences. First `openSettings()` called `tap(label: "Open settings.")`; template line115 timed out locating an exact label. Failure happened before Arabic was chosen, any restart, or System restoration. Probe contains only two observations in one boot, `completed=false`, and unchanged synthetic language preferences.

The retained evidence establishes the failed selector, **not its deeper cause**. It lacks the full runtime accessibility hierarchy. No Kotlin exception, OS termination, or app crash is shown. General simulator/launch warnings are not evidence of a crash. Production code should not be changed to satisfy a guessed selector.

The original receipt's `runtime_evidence_status: NOT_RUN` is a harness reporting discrepancy: it was not updated after the failed-summary assertion. Raw XCTest is authoritative: **FAIL_EXECUTED**. Original receipt remains immutable; author/root notified for a versioned harness correction.

## Independent binding and cleanup checks

- Retained four-file unified diff applied **in memory only**, checking every original line/hunk; all18 copy-manifest hashes match reconstructed/current originals. No code copied back.
- Nested build/Gradle-stop exits0; both immediate/final outer stops exit0 with `No Gradle daemons are running.`
- Exact owned simulator shutdown/delete exits0 and post-delete exact-name list is empty.
- Live-attested secondary `ibtoold` FIFO pair and empty parents removed; no pending/remaining paths, unknown holders, or attestation/cleanup errors.
- Reviewer independently checked all recorded removed paths/temp roots/FIFOs: absent. Targeted `ps` for all71 recorded owned PID identities has no remaining rows (exit1/empty stderr); no signals sent.
- Global Gradle cache/wrapper directories still exist. Root's source identity matches; reviewer made only compact evidence files and did not invoke builds, tests, Xcode, or simctl.

## Required continuation

Independently review a versioned evidence-only selector/receipt correction grounded in actual pinned Compose iOS semantics. Root must rerun the complete four-process matrix on a fresh owned simulator with strict source/control binding and cleanup. Until then, actual-app System/restart behavior remains unverified; physical devices, active-session retention, direct Compose direction, accessibility/gestures, Store and signing remain outside this evidence.

Full hashes, raw result references, command/cleanup timestamps, source identity and independent path checks: companion JSON and `dsc01-runtime-binding-cleanup-01.json`.
