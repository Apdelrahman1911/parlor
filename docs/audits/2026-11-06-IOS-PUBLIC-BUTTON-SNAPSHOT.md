# iOS public-button snapshot correction — November 6, 2026

## Failed source and immediate cause

[Main42261837661](https://github.com/Apdelrahman1911/parlor/actions/runs/42261837661)
on `3fde30d5db6eeb3288f71136f7c044bf3bf87525` is terminal **FAILURE**.
Independent original source/XML/artifact/cleanup readback admits seven other
producers and the optimized Apple Release join,819 KMP Simulator methods and
457 readiness controls. Swift is41 PASS/1 FAIL. Main is **not qualified**.
Intake SHA256:
`14666c64fb8d4ab6267abeb05805b8791498fd7acb489407e2f3a31d623734cb`.

The failed method is
`IOSAppLaunchUITests.testAdditionalGamesExposeOnlyMultiplayerSetupInArabic`,
line305: **Ghamza's Join permission Continue pre-tap settlement**. The same
game's Host/Back/Join transitions passed. No Continue tap was sent. This is
not the original Word Impostor failure, a typing failure or Android #332.

Original November6 UTC, app68166/runner66166, Xcode26.3/17C529:

1. Reachability computes a valid native hit point at18:09:33.904, then the
   ten-second settlement waiter starts. This earlier result is not final
   settlement or permission to reuse a cached hit point.
2. Two successful enabled snapshots have the exact same frame
   `(32,466.3333435058594,338,52.000030517578125)`. Their prerequisite remote
   first-match traversals take2.61765s and4.94945s, before snapshot capture.
3. The separate `isHittable` call starts another app-root label-prefix traversal
   at18:09:43.309. At18:09:44.162–44.167 the outer waiter expires and interrupts
   it: `com.apple.dt.XCTest.XCTFuture Code=1001`, `Interrupted by waiter`.
4. The app's original traversal finds the same Button at18:09:44.799539 and
   replies at44.877818, after the deadline. XCTest's immediate internal retries
   are interrupted too. **No native hit-point computation completed in that
   failed observation**; its `false` is not proof of native unhittability.

This establishes redundant remote-query budget exhaustion and interruption,
not why individual native queries were slow. The post-failure screenshot shows
the unobscured permission page, but is not a timely hit-test assertion. No
shipping input, Compose or UIKit defect is inferred from that image.

## Correction and preserved requirements

Public buttons now obtain geometry and enabled state from **one fresh bulk app
snapshot per sample**. Selection uses the same public Button/label-prefix
predicate locally instead of first re-running the remote app-root query. Only
one complete, unambiguous match is accepted. Traversal is limited to2,048 nodes
and32 levels, including pending nodes; missing, duplicate or incomplete results
throw a numeric selection error and break adjacency. No snapshot, labels,
typed values or hierarchy survive the read or enter diagnostic logs.

The existing sampler still requires two exact adjacent valid/enabled frames,
then the unchanged fresh `control.isHittable` query and exactly one ordinary
tap. The ten-second waiter, failure assertion/screenshot, failed-hit adjacency
reset and all English/Arabic journey outcomes remain. Text controls retain
their existing per-field snapshots. There is no coordinate tap, repeated input,
warmup, longer timeout, stale result reuse, dependency or application change.

Five Swift regressions cover coherent selection, missing/duplicate controls,
disabled adjacency, node budget and depth budget after an earlier match. The
Desktop workflow contract guards the adapter, local selection and existing
hit-point/deadline/single-action requirements. Native EN/AR execution, complete
protected PR qualification, review/merge and distinct main verification remain
required on the correction; prepared source is not a successful test.

## Original evidence and remaining boundaries

No app or diagnostic workflow was replayed for this investigation. Original
xcresult directory references were decoded offline to their retained native
logs, not represented as an official `xcresulttool` export:

| Original evidence | SHA256 |
|---|---|
| iOS artifact13529455415 | `dbb6f19b01fa2c406784200d01b1b1e7ec7b96c940c6ba73ea4c6c5fb97fad1b` |
| Session log | `25eebf139fc2f9be9afc2301166ecf73cb2111372799cf2228879541cd3aa060` |
| Application log | `f9a73918ae3928fe495f4a6848c8e3a0540a54de13aa72c418c0cbfabc9bca64` |
| Original failure PNG | `0e0261032d1a826e5aac527ed5e44932a660c1db111ab9cf7b0c9e926c81c972` |
| Exact native-line binding | `5efcc75f8ae500f458f4783a7907a7311d6f8b05bb37ef0ea134a9fa9a6b4927` |

[#276](https://github.com/Apdelrahman1911/parlor/issues/276) tracks this new
observation correction; #267/#276's older cancelled HID/keyboard-service and
[upstream](https://github.com/actions/runner-images/issues/15252) requirements
remain separate and unresolved. Do not close those Issues merely because a
later run passes. Physical Phase4, positive #258 protection and Store release
requirements are unchanged. #332 retains its whole-main gate despite its
successful native Android rotation/process evidence.
