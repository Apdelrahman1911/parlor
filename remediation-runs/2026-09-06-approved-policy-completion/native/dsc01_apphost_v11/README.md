# DS-C01 Public Settings Pane Identity — V11 Controls

This isolated, copy-only harness revision addresses an observed **test-selector
gap**, not an independently established application defect. Apphost08 observed
zero navigation bars under its label-only pane predicate while the separate
`matching(identifier: "Settings")` diagnostic counted one. That API matches any
of five documented public identifying attributes; the observation does not
prove which attribute matched or that a per-app language control exists.

## Bounded correction

Only the public navigation-pane query changes: one exact-literal union over
`identifier`, `title`, `label`, `value`, and `placeholderValue`. A matching element
is counted once even if several properties or English/Arabic aliases match.
Unique matches are freshly inspected for their actual frame, hittability, and
allowlisted property/literal provenance. Unknown property values are not logged.
Missing, ambiguous, covered, offscreen, or identity-inconsistent panes remain
ineligible. Existing target/action selectors, sampling, one-tap limits, original
OS assertions, terminal reporting, and cleanup are unchanged.

The added native decision contract exercises 12 attribute fixtures, two union
cases, and two identity guards. Nine additional Python methods protect source
preservation and receipt validation; all 168 prior methods remain unchanged.
Their discovery and execution must be established by root, not inferred here.
The new receipt gate supplements the original V10 and complete probe gates; its
PASS cannot establish a real OS language selection or replace a BLOCKED result.

## Source binding and execution

`author-proposal-and-query-research-01.json` records Apple documentation and the
actual installed Xcode 26.5/17F42 declarations. Xcode 26.5 is not the repository's
Store-qualified 26.3 toolchain. `author-prebinding-static-inspection-01.json` and
`v10-to-v11-prebinding-controls-01.diff` identify the draft's complete control
delta and static inventory. A missing `source-bindings.json` is intentional until
root binds a fresh production identity. No author self-approval is permitted.

Root must obtain independent exact-control review, execute focused controls,
obtain independent result review, and separately authorize any fresh owned
simulator cycle. Actual OS English → app Arabic → System → restart remains
unverified until the unchanged runtime assertions genuinely execute.

The entire executed V10 finalizer is retained: immediate isolated Gradle stops,
owned-simulator shutdown/deletion, PID/start-attested worker termination, and
ownership-attested secondary paths/copy/DerivedData cleanup on success or failure.
No private data, user profile, connected phone, or unrelated process is touched.
This draft supplies no physical-device, signing, Store, or project-ready claim.
