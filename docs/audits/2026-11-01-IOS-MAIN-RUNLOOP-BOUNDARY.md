# Native main-run-loop evidence boundary

## Accepted predecessor and missing measurement

Full-context [41335643678](https://github.com/Apdelrahman1911/parlor/actions/runs/41335643678)
at `15445c82` independently accepted507 Apple controls,774 original KMP methods/
149 reports and all23 native methods on original26.2/17Pro; zero skips/failures/
exports and owned cleanup. Receipt SHA256:
`fb910a482b4a095eac63696441692bfc0459f64bc2892a5c842529ffa2cb5ff2`.
This is not a natural cause, observer-neutrality or shipping correction.

Original41180128325's second remote observation took8.200s. Retained native
getter measurements cover method bodies, not main-thread dispatch, run-loop
work or UIKit/XCTest round trips. PIN46 adds passive main-run-loop phase
intervals to investigate that missing boundary. It does **not** directly measure
queue dispatch latency or AX round trips, and cannot alone establish causality.

## Bounded private-free implementation

CoreFoundation phase callbacks only: no timer, polling, thread, gesture, view/
AX getter, label/value, networking or application-state mutation. Retain counts,
maximum intervals and eight longest active/waiting intervals, plus an explicitly
partial active interval at capture. This is not an all-call/history ledger.
Waiting includes legitimate idle time; nested run-loop phase sequences must not
be interpreted as exact CPU utilization or complete outer-loop execution spans.
Do not allocate retained rows for ordinary intervals below the current top-eight
threshold. Freeze before removing/invalidation; verify actual observer removal.

The new native software control executes once on one standard free macos-15
runner, Xcode26.3/17C529 and JDK21. It deliberately blocks **only its isolated
macOS control's** main thread once for80ms, requires a measured interval≥50ms,
then executes a second control callback after freeze to verify unchanged evidence
and disallowed reinstallation. No application, Simulator or shipping test is
blocked, retried or weakened. Native control source/exit/stdout and failure logs
are retained with exact source hashes. No private UI data is read.

## Acceptance and remaining work

Three portable schema/source controls pass; the actual-Apple software control
is unavailable locally and must execute on the free runner. Freeze source and
independent readback before publication. Do not replay the already accepted507
controls/774-method aggregate/23 native journeys merely to generate this new
software capability receipt. Required shipping checks remain untouched.

Native software success is not iOS execution or application qualification.
After actual capability acceptance, integrate the observer only into the copied,
failure-owned diagnostic adapter and execute a justified natural boundary study.
Original failures and ten-second assertions remain unchanged. Phase1/#267/#276
stay open; Phases2/3 follow genuine Phase1 resolution. Physical Phase4 remains
owner-reserved. No security waiver, AGENTS change, dependency override or release.

## Actual compiler failure and correction

[41339329869](https://github.com/Apdelrahman1911/parlor/actions/runs/41339329869),
exact `43e92f65`, failed before native execution: Xcode26.3 rejects obsolete
`kCFRunLoopCommonModes` in Swift. Use `CFRunLoopMode.commonModes` consistently
for installation, membership/removal and deinitialization; retain the original
compiler log, state and source. The source control rejects the obsolete name.
The frozen reader rejected this failed capability correctly. Fresh execution of
the corrected source is required; no iOS/app cause or qualification follows.
