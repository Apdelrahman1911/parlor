# Original owned-leaf input boundary (diagnostic only)

## Evidence and missing boundary

PIN41 run41305725712 independently passed476 actual Apple controls and ten
original Arabic/English samples, with no natural failures or exports. Removing
AX/frame/root hooks did not reproduce the original failure. Original41180128325,
PIN30 and PIN31 failures remain failed. Phase1/#267/#276 remain open.

Source inspection identifies one remaining observer intervention: PIN33 adds an
inherited `setState:` method to the Compose recognizer leaf. Forwarding its
arguments is verified, but that does not establish equivalence of UIKit method
lookup or timing. This is an instrumentation hypothesis, not a shipping cause.

## Separate capability, not successful-cohort replay

PIN42 observes only the seven existing owned touch/arbitration overrides. It
verifies the setter's ABI/inheritance before installation and its original IMP
and absence from the leaf's method table afterward. It never adds or intercepts
the setter, reads private input, adds recognizers, or changes Kotlin/Compose or
SwiftUI. Existing PIN33/39/40/41 templates and narrower-evidence rejection stay
unchanged. Schema2 explicitly records seven kinds and actual untouched-setter
status; kind5/state rows are forbidden. Missing/overflowed/foreign evidence fails.

Execute only a separately named isolated absent-destination sentinel to validate
the changed adapter's real failure-only export. Keep the original unexpected
transition assertion, exit65, actions, ten-second deadline and owned cleanup.
Require actual catalog/Host began/ended calls; do not waive a startup/input failure
as the intended sentinel. No unchanged successful natural samples are replayed.

One standard free macos-15 runner, JDK21, repository wrapper, strict dependencies,
Xcode26.3/17C529, ARM64 iPhone16Pro/iOS18.5 and restore-only reviewed caches remain
required. Freeze all supporting/original/copy inputs and independently accept
terminal artifact/result/command/product/cleanup custody once. Shared runner and
workflow contracts are rerun because the new explicit entry changes those bytes.

## Decision limits

Actual Apple ABI/compiler/build/export verification is pending at preparation.
A successful capability proves capture, not a natural defect or correction.
Natural comparison must be justified separately; no speculative runtime patch,
retry, longer deadline, qualification, protected merge or phase closure follows
from this control. Physical Phase4 remains owner-reserved.
