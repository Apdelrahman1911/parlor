# Native accessibility boundary investigation

## Confirmed evidence and remaining gap

Original [41180128325](https://github.com/Apdelrahman1911/parlor/actions/runs/41180128325)
fails English Word Impostor settlement **before its opening tap**. The second
sequential XCTest query takes8.200s and reports disabled; the catalog card has
an unconditional enabled clickable. Original query attachment is not a native
hierarchy at failure. UIKit/Compose liveness and XCTest resolution remain distinct
unverified causes.

Completed current-source observations
[41187323148](https://github.com/Apdelrahman1911/parlor/actions/runs/41187323148)
independently match full application `5029b3f4`/tree `fcf9abf3`, harness `d2b9d541`,
original/copied inputs, products, owned devices and exited/reaped cleanup.
Compose and native arms each pass EN/AR, no skips; no natural failure/export.
Artifact SHA256 `4916e829c3e37cecb3551a228d2f1b690926beab74acb7750b69b59632823300`.
This is **not application qualification or original causality**. Do not retry the
same successful experiment unchanged or merge failed PR296/300.

## Targeted new capability control

`PIN08AccessibilityObserver.swift.in` is copied Debug/Simulator-only instrumentation,
never imported by shipping code. It intercepts only the exact published Compose
accessibility subclass's `accessibilityTraits` and `isAccessibilityElement` getters.
Unique superclass and exact Objective-C ABI/implementation checks precede both
replacements. Original calls and full return values remain unchanged; no labels,
values, hierarchies, extra AX queries, actions, polling or timers are added.

Only main-thread catalog/picker scopes enter a preallocated bounded numeric ledger:
getter interval, local non-retaining ordinal, getter enum, button/disabled bits or
element Boolean. Ordinals are **not lifetime IDs**; background getter calls are
not observed. Overflow rejects completeness. The bounded hook is process-owned;
it is never freed under an in-flight getter or chained on a second installation.
The owned diagnostic process/device is terminated by the existing runner.

A separate exclusive companion exports **only after original failure**, inside
PIN02's once-only snapshot request. Existing PIN02 schema/byte bounds/ACK and
original assertion/deadlines remain. Acceptance independently requires matching
token, product, process, actual original unexpected failure, both getter types,
complete Kotlin/native files, source/product manifests and owned cleanup.

One distinct synthetic suppressed Host transition at the actual full current
application validates this capability first on standard free `macos-15`, JDK21,
Xcode26.3/17C529 and owned ARM64 iOS18.5/iPhone16Pro. It does not replay the original
suite. **Native execution pending.** Interception may change timing; capability
is not neutrality, natural reproduction, a shipping fix or physical acceptance.
Issues267/276 and Phase1 remain open. No dependency/security/release gate is waived.
