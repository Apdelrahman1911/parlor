# iOS keyboard-tutorial query correction — November 6, 2026

## Original failure and scope

[PR328 run42177783654](https://github.com/Apdelrahman1911/parlor/actions/runs/42177783654)
executes authored `5649dea4` as merge `02956f62`. Its iOS producer fails:
809 KMP Simulator methods pass, but Swift is39 PASS/1 FAIL/0 SKIP. The failed
method is `IOSAppLaunchUITests.testAdditionalGamesExposeOnlyMultiplayerSetupInArabic`,
at the first game's Join-name exact-value assertion, line144. This is not a
Host navigation failure, an Android locale failure, or complete PR qualification.

Independent source/artifact/cleanup intake SHA256:
`6622fbc95847b7bf1f5b2b9d61730772397afa9b17d474e16c68dd1c7be455eb`.
The original xcresult ZIP SHA256 is
`7f109a838c023b4c537f10226b5df1a122a88ef1adc5fcb27ff4369b6a6d24b8`.
Retained session objects and the actual failure PNG were inspected offline;
no application or diagnostic workflow was replayed to obtain these findings.

## Confirmed boundary

Original November6 UTC, application PID50453 / test runner49359:

1. `typeText("Table Test")` completes at07:29:56.256. This alone does not prove
   text delivery or accessibility settlement.
2. The unchanged ten-second exact-value waiter starts at07:29:56.258.
3. Its first callback starts at07:29:57.335. The late Apple keyboard tutorial
   is identified, followed by separate remote Continue-presence, button-frame,
   application-frame and hittability resolutions. The callback takes9.519550s.
4. Continue's ordinary tap starts at07:30:05.317, but its remote resolution is
   interrupted at the outer deadline: `com.apple.dt.XCTest.XCTFuture Code=1001`,
   `Interrupted by waiter`. XCTest records `Failed to Tap "Continue" Button ...
   because future was canceled`. There is no successful dismissal event here.
5. The subsequent field snapshot is also immediately interrupted. The helper
   records `snapshotPresent=false`, not an observed empty or incorrect value.
   It never obtains a successful field snapshot inside this assertion.
6. The original07:30:18.334 failure screenshot shows the complete synthetic
   value and the still-present Apple introduction. This is **after** the
   deadline, not proof of timely delivery or a passing assertion. PNG SHA256:
   `c0abb2f3e76bd2cf06d34f3e97a420ba5f531e694f89c38a12b52309c115fb91`.

The helper unnecessarily spends its observation budget resolving the same
public surface repeatedly. Why individual hosted AX calls were slow, and the
precise insertion time in this run, are not established by this review.
Do not relabel the older keyboard-service or synthetic-digitizer failures as
this newly confirmed observation defect; [#267](https://github.com/Apdelrahman1911/parlor/issues/267)
and [#276](https://github.com/Apdelrahman1911/parlor/issues/276) remain open.

## Correction and safeguards

`IOSAppLaunchUITests.swift` now resolves one app snapshot for tutorial identity,
unique enabled Continue frame and application bounds. Traversal is bounded to
2,048 nodes/32 levels and retains no snapshot, labels or hierarchy outside that
read. Missing, ambiguous, disabled or incompletely traversed targets admit no
tap. Exact English Apple tutorial text is required; a generic Parlor Continue
is never accepted as an interruption.

The existing geometry validation and fresh native hittability remain required
before the single ordinary dismissal. The original ten-second waiter, exact
full-value assertion, failure-only screenshot, subsequent real typing/actions
and one-dismissal budget are unchanged. No retyping, warmup, extra tap, larger
timeout, stale snapshot fallback or shipping input patch is introduced.

Two Swift selection regressions cover the sole enabled target and all missing,
duplicate and disabled combinations. Existing four dismissal controls still
cover late appearance, nonhittability, invalid/offscreen geometry and the
single-action budget. A Desktop workflow contract enforces the coherent bounded
read and forbids reintroducing separate remote presence/frame properties.

## Verification boundary

Fresh affected controls, all42 Swift methods including the English/Arabic
public journeys, full protected PR qualification, review/merge and a distinct
main workflow are required on the final integrated source. Prepared code or a
passing unrelated producer does not satisfy them. The prior failed result stays
failed. Physical-device acceptance and the upstream input/service requirements
are separate; no Store release or security waiver is involved.
