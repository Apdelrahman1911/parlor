# Native accessibility root boundary — Phase1

## Verified evidence and unresolved question

PIN08 [41196051107](https://github.com/Apdelrahman1911/parlor/actions/runs/41196051107)
passed actual Apple controls and independently bound failure-only capability:
4015 original getter rows, zero overflow, exact synthetic Host assertion retained,
source/product/ACK/device/process and owned cleanup verified. Artifact13040462964
is81712139bytes, SHA256 `bdadfb846b19710b946a551dd297de7fabb7d2c22b4f00db3c3a94e35bea0b67`.

PIN09 [41197418298](https://github.com/Apdelrahman1911/parlor/actions/runs/41197418298)
finished successfully: both original English/Arabic methods pass, zero skips,
**zero failure-only exports**. Independent published-source acceptance verifies
all copied inputs/products and owned cleanup. Artifact13041019763 is2893004bytes,
SHA256 `e5c6b7a8b16c5448b4677b7380a8e872c90e3c8ac6cb95bb917baa94d146269c`.
Its local receipt writer initially failed after validation because a successful
arm correctly omits `captures`; corrected receipt publication preserves the
original artifact and every validator requirement. No native tests were replayed.

Neither success establishes the original41180128325 pre-tap cause or repairs
#267/#276. Element returns cannot distinguish root removal/recreation, a stale
AX lookup, or native getter processing. Published Compose1.10.3 has explicit
`AccessibilityRoot.accessibilityElements()` activation and root clearing in
`AccessibilityMediator.cleanUp()`. That is a source hypothesis, not proven cause.

## Distinct missing observation

PIN10 observes the original root getter and inherited setter on the **unique
verified Compose root subclass**, using the already executed ARC-safe inventory.
Exact Objective-C signatures are mandatory before installation. The setter is
added to that subclass only: no UIKit/superclass-wide replacement. Original
arguments/results remain intact; no array contents, labels, values, addresses,
typed text or private player state are read or retained.

Only public main-thread scopes record start/end clocks, read/write enums and a
nil/non-nil bit. The separate8192-row/1MiB numeric file uses the existing once-only
post-failure request; overflow still rejects after safe incomplete retention.
PIN02/PIN08 templates, schemas, file bounds and native completeness stay exact.
Owned diagnostic process lifetime prevents an in-flight unhook race.

Actual Foundation/Objective-C controls must verify original object identity and
nil setter/getter preservation; Simulator SDK typecheck and full copied-app
synthetic capability remain required. Only after independent capability acceptance
may PIN11 interpret original EN/AR root observations. No extra touch recognizer,
AX query, timer, action, selector, timeout, or original assertion is introduced.
Its failure-only settlement log additionally emits the already-computed start
uptime, permitting direct comparison with native getter/root intervals. This
uses no new clock read or successful-path work; original sample collection and
waiters remain unchanged. Post-failure screenshots are not substituted for this
boundary timeline.

## Status and next decisions

Prepared targeted capability/observation on standard free `macos-15`, read-only
permissions, no diagnostic cache saves or shipping/release changes. Software
fixtures do not certify UIKit. Natural root clearing must be correlated with
original failures before attributing causality; a nil write alone is not a bug.
If no natural failure occurs, preserve that limitation without blindly repeating
unchanged successful tests. Phase1 and #267/#276 remain open; Phases2/3 await
actual qualified corrections. Phase4 stays reserved for the owner's device agent.
