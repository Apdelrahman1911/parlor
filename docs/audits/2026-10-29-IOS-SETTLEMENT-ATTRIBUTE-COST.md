# iOS pre-tap settlement: unnecessary snapshot attributes

## Confirmed failure, not the previous text-input failure

[PR #282 run 40774291250](https://github.com/Apdelrahman1911/parlor/actions/runs/40774291250)
at `871d153804f5970bd6ad315828b272ef56db3ba4` is **failed and unmerged**.
Seven other producers and the original optimized Apple Release join pass.
All 679 KMP Simulator cases pass; Swift has 22 passes and one failure.
Both additional-game name journeys pass, but this does not establish a repair
of #276's delayed input or #267's original lost Host event.

The failing `testKeyboardInsetsOnNameAndRoomCodeInArabic` reaches Whodunit
Join → public local-network rationale. Before any Continue tap or typing,
the ten-second settlement observer obtains only two samples: the first takes
5.940 seconds and has an enabled, nonempty frame; the second is interrupted
after 3.167 seconds. The screenshot still shows the ordinary Arabic rationale
and Continue button. It is not a missing permission screen or keyboard tutorial.

Independently downloaded runtime artifact 12856063752 ZIP SHA256:
`b20deb4a3600386392975ec17b0fa4fe5574b3888a71dab0b43100618680e03a`.
The failed method's attachment is resolved from its own payload reference,
not selected from an unrelated screenshot or later passing method.

The original XCTest detail explicitly identifies code 1001 as **“Interrupted
by waiter”** when the outer ten-second watchdog fires. It is not evidence of
an element disappearing. The full snapshot faults in unrelated value,
placeholder, identifier, visible-point and window attributes. These synchronous
remote reads contribute to exhausting the budget before two usable frames.
The cause of the runner's underlying accessibility latency remains unproven.

Apple's current [snapshot API](https://developer.apple.com/documentation/xcuiautomation/xcuielementsnapshotproviding/snapshot())
returns attributes and the descendant hierarchy, whereas [frame](https://developer.apple.com/documentation/xcuiautomation/xcuielementattributes/frame)
is the complete screen-space CGRect. The trace, not the documentation alone,
establishes unused attribute faulting in this failed observation.

The reviewed open/closed Issue index and scoped search found no equivalent
pre-tap attribute-cost defect. [Issue #283](https://github.com/Apdelrahman1911/parlor/issues/283)
tracks this CI failure, separately from #267/#276 and #270's optimized links.

## Targeted correction and safeguards

Settlement now reads only existence, one complete `CGRect`, and enabled state
per poll; native hittability is checked only after two exactly equal adjacent
valid/enabled samples. The observations are sequential, **not an atomic
snapshot**. Missing/disabled controls, invalid geometry and failed hittability
still reset adjacency. A missing element during a subsequent native property
read remains an XCTest failure, never successful settlement.

The same ten-second budget, one ordinary tap, failure guard/throw, bounded
12-row geometry/status/timing diagnostics and failure-only synthetic public
screenshot remain. No frame rounding, settling sleep, gesture replay, timeout
extension, arbitrary hierarchy capture, keyboard preference or shipping-code
change is introduced. All six frame-state methods remain; absence/disabled
regressions now cover repeated interruption. The Desktop source contract
rejects full-snapshot attribute faulting and guards every admission condition.

A direct `print(expected)` mutation also exposed a source-safeguard gap:
the prior interpolation-only ban did not reject it. Shipping/test source had
no such log; this is not a data leak. Both observer contracts now permit only
their existing single categorical failure-ledger print, also rejecting extra
or replaced direct print sinks. The initial stale-anchor driver failure and
then the unexpectedly passing privacy mutation remain retained, not passes.

## Verification and limits

The restored focused Desktop suite passes 18/18. All six revised source
mutations reject exactly their intended executed method: unnecessary full
snapshot, disabled admission, tap after failed wait, partial-value acceptance,
unbounded text sampling and direct expected-string logging. Source bytes
restore and all private registry stops pass. These are Linux source controls,
not Swift execution. Strict complete local checks and all original hosted
lanes remain required on the new committed source.
The actual Swift suite remains 23 methods. Native latency improvement is a
**hypothesis until measured on the new hosted run**, not a local Linux result.
Retain the failed run and independently verify protected integration and
automatic main. #276/#267 remain open for their separate causal investigations;
physical gameplay, protection, energy and Store acceptance remain outstanding.
Only the existing free standard public-repository runners are used.
