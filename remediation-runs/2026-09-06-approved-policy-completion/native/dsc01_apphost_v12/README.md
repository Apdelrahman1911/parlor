# DS-C01 Exact OS Preference Preservation — V12 Controls

This isolated, copy-only harness revision corrects an unsupported test
assumption. It does not change production source or establish a new app defect.
Apphost09 genuinely selected English through guarded public Settings controls,
then observed effective English and an **absent** app-domain `AppleLanguages`
key before original app initialization and throughout six later samples. Its
mandatory-presence predicate failed. That incomplete run remains **FAIL**;
the unexecuted Arabic → System → restart assertions are not retroactively green.

Apple documents named persistent domains separately from resolved preferred
languages; it does not require a nonempty app-domain key for this OS choice.
See the sibling `dsc01-apphost09-language-representation-research-01.json`,
`dsc01-apphost09-activation-research-01.json`, and
`dsc01-apphost09-author-diagnosis-and-v12-proposal-01.json`. The diagnosis does
not prove English was already selected or that every OS represents it this way.

## Exact replacement oracle

Only the bounded OS test region and its verifier wiring change. A post-real-OS
selection sample must match a fresh process's before-App **presence and exact
ordered array**, with System selected, no owner bookkeeping, and effective
English. It cannot silently rebase a present before-App value to absence.
Actual English Settings must be mounted before the actual Arabic selection.
Arabic ownership must remember both the baseline's presence and its exact
array. System and a fresh before-App restart must restore that same pair, actual
English Settings/Compose LTR, and actual native child direction. Four bounded
stage receipts bind these observations to boots, sequences, and the real
one-tap OS English action, before terminal reporting.

Both PRESENT and ABSENT branches are explicit. PRESENT additionally executes
the original presence-only OS verifier. ABSENT does not claim that verifier or
actual-present OS coverage ran. The runner explicitly renames historical V10
oracle metadata to identify the supplied V12 gate; V10 public-action guards
and V11 exact-pane/provenance guards remain unchanged and mandatory. An
unavailable OS route remains BLOCKED, without an invented baseline.

## Controls, review, and limitations

All 29 other copied controls, all 177 inherited test methods, and the entire
owned-resource finalizer remain byte-identical. The added native contract has
14 shape, six ownership, and four restoration cases. Twenty-two additional
Python test methods cover both representations, malformed/stale/rebased data,
missing before-App observations, exact ownership, real-action ordering,
native/Compose/geometry guards, unchanged Settings/both-local validation, and
source/binding preservation. Static inventory expects **199** methods and
**171** full-path controls; these counts are not execution evidence.

This draft is not source-bound or executed by its author. Root must obtain
independent stable-delta review, create `source-bindings.json`, freeze controls,
run focused tests, obtain independent result review, and separately authorize
a fresh owned-simulator run. Original run receipts stay immutable. Any new run
must report which OS representation actually occurred; synthetic PRESENT
coverage cannot be called real OS-created PRESENT evidence.

No global preferences, private Settings files, user devices/profiles, app
navigation, game state, language ownership implementation, or dependencies are
modified. Original bounded selection/timing, one-action limits, current XCTest
selection, and immediate Gradle-stop/ownership-attested cleanup are preserved.
This revision supplies no physical-device, signing, Store, or readiness claim.
