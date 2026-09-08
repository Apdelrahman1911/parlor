# DS-C02 additional tests — independent continuation

Production fix author `/root`; reviewer `/root/whodunit_cont`. This augments,
not replaces, `DS-C02-C03-independent-review.md` and its earlier passed cycle.
No production code was changed in this continuation. Exact source hashes and
read ranges are in the accompanying source/failure/fixture receipts.

## Coverage and first execution

Read every line of `ParlorToastHost.kt`, the expanded lifetime test and the
new `ParlorToastConcurrencyTest.kt`.

The real four-worker test checks 50 non-overflowing four-message waves:
all four messages survive, 200 presentation IDs are unique across empty-queue
resets, and each wave can be dismissed. It deliberately does not require
numeric reservation order to equal queue insertion order. The second test
checks 200 concurrent identical submissions still coalesce, then 200 distinct
submissions retain the four-item bound and survive stale-ID dismissal.
Barriers/futures are bounded; executor interruption and termination are in
`finally`. This is finite concurrency regression coverage, not exhaustive
schedule or device-load evidence.

Root cycle `design-whodunit-desktop-01` executed both concurrency tests and
the two existing lifetime tests successfully. The new owner-replacement test
failed **before its expiry comparison**, at the first query for replacement
text (`ParlorToastLifetimeTest.kt:46` at the tested version). Full design
result: 40 tests, one failure, zero errors/skips. Whodunit's separate 309
tests all passed; the combined command is nevertheless FAIL.

## Failure investigation: test clock, not an established app regression

The test disabled automatic frame advancement, changed its owner inside
`runOnIdle`, then queried new semantics without another frame. Official
CMP **ui-test 1.10.3** source states that recomposition needs an explicit frame
when the clock is paused; initial setContent is the exception.
`runOnIdle` waits **before** the callback and does not advance after it.
Skiko considers pending recomposition idle when autoAdvance is false.

The follow-up also traced `collectAsState`: the exact pinned JetBrains runtime
1.10.3 is a wrapper depending on **AndroidX runtime 1.10.5**, including its
Desktop variant. `SnapshotFlow.kt:48–69` delegates to keyed produceState;
`ProduceState.kt:133–142` retains its remembered State and restarts only the
producer when the Flow changes. One frame can therefore change the collector
before the replacement emission is rendered on the next frame.

Authoritative source URLs, access dates, archive hashes and read ranges are
preserved in `DS-C02-frame-clock-research.json`,
`DS-C02-owner-flow-research-resolved.json`, and
`DS-C02-desktop-runtime-provenance.json`. The first JetBrains runtime source
archive contained only EmptyFile and was explicitly marked **not evidence**
of the implementation; the subsequent official dependency/source chain
resolves that limitation. No public source archive was left as a build cache.

At root's explicit request this reviewer changed only the fixture to advance
**two bounded frames** after replacing the owner. Equal numeric IDs, both
visibility checks separated by 600ms, queue cardinality and the additional
900ms automatic-expiry check are unchanged. No arbitrary wall-clock sleep,
manual dismissal, production key change or reduced assertion was added.
The production root-cause correction remains root-authored; this small
test-scheduling correction was authored by the reviewer and needs root's
rerun/review rather than being called independently executed here.

Final frozen test SHA-256:
`4bec7f140fa576210c1abc9c33decf1bac5b6ca24112d15d69dee2d0ad702cc9`.
**At this checkpoint its rerun is pending.** The failed run and original
test source hash remain in `DS-C02-additional-tests-failure-review.json`.

## Rejected additional application candidate: owner-switch stale queue

**Not a confirmed application defect.** The synthetic owner-replacement seam
can retain the prior collected value until the new Flow emits. That is not
proof of a reachable Parlor stale-notification leak, let alone another
player's secret being exposed.

I reopened the complete common App, all three Kotlin platform entry files
and the common language provider, and enumerated production toast
construction/provision/host sites. `App.kt:81` owns one unkeyed remembered
`ParlorToastState`; it is outside language, theme and navigation scopes.
`App.kt:89` provides that same value, and the sole shipping `ParlorToastHost`
at `320–323` receives it explicitly. Feature call sites obtain that state to
show notifications; none constructs or replaces the root owner. The Android,
iOS and Desktop entry points all mount this App. App recreation creates a
new state **and a new host composition**, not an in-place owner swap with
an old remembered collector value. A language provider temporarily unmounting
content likewise does not replace the remembered root owner.

No complete production-reachable consequence for this owner-swap hypothesis
was established. It remains useful defensive API/test coverage, not an
additional confirmed finding or a reason to broaden the production patch.
This does not reject the original DS-C02 same-owner ID-reuse defect; that
original automatic-dismiss/replacement path and its fix remain independently
confirmed.

## Hygiene and limits

The failed cycle stopped Gradle with exit 0 and cleaned its task-owned outputs
without errors; source was stable. This reviewer ran only bounded HTTP source
retrieval and Python evidence/source inspection, with no builds, native app,
server or persistent process. Public source text was retained as compact
required evidence, not generated application output. No signing/private
material, original audit evidence or dependency verification metadata was
read or changed. Physical-device animation, native accessibility and Store
claims remain outside these tests.

## Executed follow-up — 2026-09-05 20:28 UTC

The pending checkpoint above is superseded for execution by root's
`design-toast-cover-02`: **40 tests in 18 suites passed, zero failures,
errors or skips**. The two concurrency tests and all three lifetime tests
ran successfully, including the exact two-frame corrected owner case.
Actual suite XML and test-case nodes were parsed by this reviewer. All eight
reviewed DS-C02/DS-C03 production/test file hashes match both executed source
manifests and current files; the lifetime test hash is the one recorded above.

**Independent bounded execution approval: PASS for DS-C02**, supplementing
the original production correction approval. DS-C03's same unchanged patch
and contrast regressions also remain passing. This does not turn the
synthetic owner-swap hypothesis into a shipping defect or certify physical
mobile behavior. The fixture timing correction was authored by this
reviewer at root's request; root reviewed/executed it in its sole build lane.

The strict-wrapper command exited 0 at `20:28:16.746812Z`; stop exited 0 at
`20:28:17.059923Z`; cleanup completed `20:28:17.196419Z`. Source remained
stable. Root/core/design/build-logic task outputs were removed, with no
remaining owned outputs/workers or cleanup errors recorded. A later native
cycle's outputs are distinct and must not be removed by this reviewer.
Raw-result hashes, exact command/source binding and cleanup are recorded in
`DS-C02-additional-tests-execution-approval.json`.
