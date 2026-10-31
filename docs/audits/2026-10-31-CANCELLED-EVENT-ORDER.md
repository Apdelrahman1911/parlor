# Cancelled session event waits — October 31, 2026

## Confirmed finding and scope

[#302](https://github.com/Apdelrahman1911/parlor/issues/302) is a P2 generic
session event-contract defect at accepted main `35288a72`.
`PassAndPlaySessionController` commits reducers under its mutex, then emits
ordered event batches outside it. Cancelling a middle batch **while it waits**
unconditionally completes that batch's token, allowing a later emitter to
pass the still-active predecessor. Canonical commits remain ordered.

This is reproducible cancellation/backpressure behavior, not an observed phone
incident, private-state leak, remote authorization bypass, or the cause of
#267/#276. Current shipping event consumption is limited. Historical
`RR-SESSION-01` / `bc77a460` fixed ordinary concurrent ordering; its cancellation
edge still released an unfinished predecessor.

## Actual reproduction

Byte-exact original production, three new helper methods plus two existing
methods: **five Desktop tests, two assertion failures, three passes, zero
errors/skips**. Baseline log SHA256:
`1e7b3e4474358e4a262d2c24bb74617ea8f5bfc1fd2a5e3333d4edbf94213c4e`.
The direct three-turn test suspends A after event1, cancels waiting B, and
starts C before releasing A. Consecutive cancellation also fails.

An independent new common integration method executes the actual controller,
three reducer commits, its unchanged 64-event SharedFlow buffer and a synthetic
100-event first batch. Against the same original production it has **one
ordering assertion failure, zero errors/skips**: later events interleave with
A's remaining events. All three canonical actions committed exactly once.
An initial assertion-type compilation error was corrected and retained
separately; it is not application evidence. Original source/log/XML receipts
remain in local custody, never relabelled as corrected-source results.

## Correction

`EventEmissionCompletion` stores either full completion or the predecessor of
a skipped waiting batch. Successors follow skipped gates iteratively until the
actual ordering barrier finishes. Cancellation remains prompt; no
`NonCancellable` wait, worker/thread, cleanup coroutine, recursive callback
chain or additional event queue is introduced. A normally finished emitter
releases earlier gate references. Already committed actions are neither undone
nor automatically retried; cancelled event delivery is not replayed.

Both original Desktop methods remain. The earlier cancellation test now proves
that its successor **waits for the unfinished predecessor**, then releases after
that predecessor completes; its original final event assertion remains.
This corrects an invalid premature-release expectation rather than removing a
test or weakening a deadline.

Six new common methods cover waiting cancellation, 32 actual consecutive
cancellations, cancellation during emission, exception propagation, real
controller backpressure and 10,000 directly constructed skipped gates. The
last is traversal-depth stress, **not** 10,000 real user submits or a mobile
performance benchmark.

## Verification and remaining gates

Corrected affected suites independently pass **169 Desktop /166 Android Debug
/166 Android Release methods**, zero failures/errors/skips. All six common
methods run on those targets; Desktop also retains both earlier methods.
Plain and explicit type-aware Detekt pass. Affected cached execution is43s,
not a cold/free-hosted-runner or battery benchmark.

## Completed protected integration

Final source `0e3e843d` /tree `079b8cd3` independently passes strict
`productionCheck allTests`: **1,947 Desktop /732 Android per Debug/Release
/120 GitHub-test /777 release controls /450 readiness methods**, established
Linux-only skips. Local exact-method receipt SHA256:
`5341902ae2928a5b9a12051f2e841624ec2e2f2e7776b87d63e58759c0a94e74`.

[PR303](https://github.com/Apdelrahman1911/parlor/pull/303) normally merged after
fresh [41154958886](https://github.com/Apdelrahman1911/parlor/actions/runs/41154958886)
qualified all eight producers and the Apple Release join. The separate automatic
[actual-main41158996451](https://github.com/Apdelrahman1911/parlor/actions/runs/41158996451)
then independently qualifies actual merge `16d4ecab`, the same tree, **23 Swift
/759 KMP Simulator /450 macOS readiness methods**, zero native/readiness skips
and exact source/artifact/native/cleanup custody. Main qualification SHA256:
`5a21fa0ffc207e84527b6ec5c0fc94864f8eaf83922e1326100e14df47dd6a32`.
Scoped #302 closed only after that distinct main acceptance.

This does not qualify newer commits, repair original iOS failures, certify physical
devices, change CI/Release/dependencies or authorize Store distribution. Reviewed
open/closed equivalents and #28/#91/#92/#95/#260; input-ownership #299/#301 and
the wider overhaul remain separate.
