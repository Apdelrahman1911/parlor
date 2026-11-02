# Phase 1: native editor attachment and text delivery

## Why this is a different investigation

Run [41431562253](https://github.com/Apdelrahman1911/parlor/actions/runs/41431562253)
(`4e6282eb`) is terminal failure: 19 actual Mac safeguards/zero skips, successful
preboot tool readiness, correct owned Simulator boot/cleanup, but unchanged
8-second `xctrace list devices` deadline exceeded. The own-PID log has only its
header. No attachment, target, recording or samples exist. Boot readiness alone
is not a sufficient correction; the internal blocked call remains unknown.

Run41425590946 admitted both original AR/EN methods as passed, but app stack
capture returned75 with no native report. Do not replay those successful journeys
hoping to reproduce. Earlier41412185488 retains empty Arabic name text after
synthesized typing; that is not proof of native editor mutation delivery.

## Supported source boundary

Published Compose1.10.3 `ComposeSceneMediator.uikit.kt` passes its
`OverlayInputView : CMPScrollView` to `UIKitTextInputService`. The service's
`attachIntermediateTextInputView` attaches a new direct `CMPEditMenuView` subclass
implementing `UITextInput`. `IntermediateTextInputUIView` owns insert/replace/
marked/delete/presses and resign overrides. `becomeFirstResponder` is inherited.
The existing PIN42 owned observer covers touch/arbitration only; PIN25 enumerates
startup classes and PIN27 instruments a Kotlin-supplied container. Neither is
substituted as proof of this new native-only, actual-view attachment boundary.

## New limited adapter

PIN51 binds the unique source-matching overlay at the original appearance
boundary, using a bounded view traversal. It intercepts only that leaf's inherited
`didAddSubview:` with exact ABI validation; the parent method table is unchanged.
Only the exact owned container and weakly tracked attached editor instances admit
numeric rows, including delayed calls to a formerly attached editor.
Seven existing editor overrides forward unchanged original arguments once and
synchronously. Validate owned methods/ABI and unchanged inherited responder IMP;
never enumerate classes, add a gesture, read text/ranges/events/AX properties,
change input, or hook `becomeFirstResponder`. Reattachment gets a numeric ordinal,
not a retained/exported object identity. Buffers are bounded and overflow rejects
complete evidence. Observation can perturb scheduling; neutrality is not claimed.

Only after XCTest reports a real failure does the original PIN02 request/ACK
freeze private-free native/runloop clocks and rows in exclusive0600 files under
0700 owned staging. Incomplete safe installation evidence is preserved but cannot
pass final admission. The old frozen runner/profiles remain byte-identical; an
external dispatcher admits only the new source-specific result, never qualification.

## Execution and acceptance

One standard free `macos-15` runner, JDK21/wrapper/strict dependencies,
Xcode26.3/17C529, exact original5029b3f4 source, iOS26.2/iPhone17Pro. First run
actual native ABI/argument/nil/NSRange/instance/parent/bounding controls and iOS16
Simulator typecheck. Then one separately named **isolated control**, not the
accepted original pair: original public Egyptian Dominoes Join/name helpers,
one original synthetic `Table Test` typing, original exact-text assertion and
Done, followed by a deliberately declared exporter failure. No network submission,
retyping, repeated tap, relaxed original guard, assertion extension, opaque upload,
permission change, Store operation, diagnostic cache save or paid infrastructure.

A green diagnostic requires actual XCTest65 solely at the declared exporter,
actual same-PID attachment/mutation/return rows and ACK, original/product manifests,
exact command budgets, and owned cleanup. An earlier original helper failure is
retained and rejects the capability; it is not ignored. Fresh original AR/EN
causal work is justified only after independently admitting the new capability.
#267/#276/Phase1 remain unresolved; no shipping correction, merge or Issue closure.
Phases2/3 remain sequential; physical Phase4 is owner-reserved.

## First actual attempt: exact diagnostic root cause

[41434446453](https://github.com/Apdelrahman1911/parlor/actions/runs/41434446453)
at `24bb0fa3c678c72339999cb8e6077bba6b69eaae` is terminal failure. Independent
frozen readback admits all500 supporting/1,214 original inputs, nine actual Mac
controls/zero skips, built Debug product, sole declared exporter failure after
original public-name/exact-text/Done helpers, real XCTest65, and owned cleanup.
No request/ACK/native companion exists; strict capability admission correctly
rejects the missing actual acknowledged post-failure request. No native editor
installation or delivery may be inferred from the absent export. Artifact
13138010437/79,991,898bytes/SHA256
`172671e97fba47cb18eb6740457d079edd93178dc43be0ce2c619913b05b1741`.

Root cause is the new **isolated diagnostic testcase**, not a shipping finding:
it configured the app but omitted registration in `pin11PublicApp`. The copied
original `record(_:)` interceptor requests capture only for that registered app.
Original journey helpers perform that registration; this separately named control
did not. With `continueAfterFailure=false`, a post-assertion/defer request is not
a legitimate repair. Register the exact app once **before launch/any helper** so
the existing unchanged interceptor captures before forwarding the real failure.
Add a regression check for registration-before-launch and no extra request.
Original input actions/assertions/deadlines, native templates/ABI, dependency
verification, failure-only policy and existing evidence remain untouched. A new
source-specific control is justified by this correction, not an unchanged retry.
#267/#276/Phase1 still require actual capability and application causality/fixes.
