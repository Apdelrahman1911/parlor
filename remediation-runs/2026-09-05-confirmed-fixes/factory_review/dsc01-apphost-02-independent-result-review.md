# DS-C01 app-host attempt 02 — independent result review

**Bounded verdict: PASS. Cleanup: PASS.** Reviewer `/root/factory_review` did not author the production correction or harness. This closes the **actual-app owned-simulator restart/System matrix** gate only; DS-C01 remains **PARTIALLY VERIFIED** for the separately stated legacy-ownership/active-session gaps.

## Exact execution and source

Root executed one real XCTest, **1 passed / 0 failed / 0 skipped / 0 expected failures**,65.510s; Xcode exit0. Cycle2026-09-06 01:33:36–01:37:36 UTC. Fresh owned iPhone17Pro iOS26.5/23F77 ARM64 simulator; Debug `com.parlor.app.debug`, unsigned. No physical-device or Store evidence.

Branch `main`, HEAD `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Dirty source manifest `e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7`, tracked diff `60b555c0dd323a086e959ffbc5aa3fd7c6629ae1d0ad016021572e7ae272e4dd`, controls `5eed8a6fb73960bcf352c8792a4961fa58bee6b52a41861bce5af449aa8dca0f` all equal before/after/current independent computation. All658 source rows and18 copied inputs rechecked. Four-file copied diff independently reconstructed in memory; no code copied back.

## What is directly verified

| Flow | Actual evidence |
| --- | --- |
| Fresh System → Arabic | Events1–3: no original override/marker; actual Arabic button installs `[ar]` with owner; translated Arabic UI and root native RTL. |
| Process restart → System | Events4–6: owner/[ar] already present **before App/Koin/root creation**; actual System removes only own override/marker and restores English/native LTR. |
| Synthetic previous `[ar-EG,en]` → English | Events7–9: prior array exists before App, no marker; actual English selection records that exact previous array, uses `[en]`, English UI/native LTR. |
| Another restart → System | Events10–14: persisted owner/en/previous array before App; actual System restores `[ar-EG,en]`, removes marker, Arabic UI/native RTL; final completion agrees. |

All14 observed states independently checked against an exact expected table, without reusing the author's validator. Four distinct boot UUIDs are corroborated by raw log app PIDs `2139, 2320, 2375, 2452`. Original root controller count stays1 per process. Raw log identifies exact English/Arabic Settings tabs plus actual Arabic/System/English/System taps; no fallback diagnostics. No `-AppleLanguages`/`-AppleLocale` masking, Kotlin fixtures, replacement settings, or preference-owner injection. The production Settings→dispatcher→persistent store→iOS defaults→locale provider/ownership path was reopened.

## Counter-evidence and boundaries

Attempt01 remains **one real failed XCTest**, not rewritten into a pass or an app-crash report. The new exact combined selector now has both pinned-source and observed-runtime evidence.

Native root direction is not direct Compose layout/gesture/VoiceOver proof. One controller does not prove active Whodunit/Mafia/LAN session continuity. Previous per-app preference is deliberately synthetic, not actual OS Settings execution. Controlled restarts do not guarantee power-loss durability for asynchronous NSUserDefaults writes. Old unmarked override provenance remains an unresolved product policy; legitimate OS preferences must not be deleted. IOS-R1, physical-device/LAN, other iOS/iPad versions, Store/signing and broader lifecycle gates remain separate. Selected binary hashes are not full signed-bundle provenance.

## Cleanup independently checked

Embedded build and immediate Gradle-stop exits0; both outer stops exit0. Exact owned simulator shut down/deleted, post-delete exact-name list empty. Attested secondary FIFO pair and empty parents removed. At01:37:35.569226UTC cleanup complete; no outputs, workers, unknown holders or errors. Reviewer independently confirmed owned paths absent and all54 recorded PID identities absent (targeted ps exit1, no rows/stderr); global cache/wrapper directories remain. No reviewer build/test/native worker, process signal, application edit, Git/Store action, or user-data access occurred.

Full raw hashes, honest source ranges, transition proof and cleanup evidence: companion JSON and `dsc01-runtime-binding-cleanup-02.json`.
