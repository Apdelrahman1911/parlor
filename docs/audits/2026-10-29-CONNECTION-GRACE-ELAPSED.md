# Short background grace uses continuous elapsed time

## Confirmed behavior and scope

Related existing [#252](https://github.com/Apdelrahman1911/parlor/issues/252),
not a new duplicate or a physical-device security conclusion. The already accepted
120-second hard-recovery budget uses a continuous clock, but the separate
15-second retained-connection path still relied on wall time and queued timers.

At main `5958f928`, two new tests execute **unchanged actual host/peer room code**
with synthetic P2pKit sessions, independent injected wall/elapsed counters and
pending coroutine timers. Move elapsed time to15 seconds while wall time remains
inside the original window: the host still accepts remote game commands and the
peer still attempts retained validation. Both assertions fail (zero errors/skips).
Original RED log SHA256
`a69d73e171490f0fdbfd8c79330f8fdb56012092ce17268219a87e177cf75319`.
These are executed timer/clock fault scenarios, not phones, actual OS sleep or LAN.

## Correction

`ConnectionGraceBudget` is immutable and constant-space: synchronous command
getters, validation and coordinator timers spend the original elapsed budget.
Calendar hints may shorten current eligibility but cannot extend the original
elapsed deadline. Invalid/backward/overflowed
elapsed readings fail closed. Epoch projection saturates on overflow and carries
elapsed time into hard recovery, preventing a delayed short-grace expiry from
renewing the total120-second window. Validation is rechecked after its await;
slow retention work cannot start a fresh timer. Existing room generation,
mutex/close authority, cancellation, privacy and host-only gameplay stay intact.

Reuse the already accepted Android elapsedRealtime, Apple continuous RAW clock
and Desktop nanoTime implementations; no dependency, protocol, thread, polling,
wake lock, background entitlement or host migration. The getter path uses primitive
counter returns rather than allocating a boxed optional value per check.
Common tests inject deterministic clocks, not Android JVM SDK-stub returns.

## Verification checkpoint and required acceptance

Focused corrected source executes all **41** affected methods: five budget,
19coordinator/fault/registration and17actual-room retention methods. All pass,
zero failures/errors/skips; independent retained XML readback matches every suite.
Log SHA256 `7365dab10b37d3398d961e3cdcbb44ae4bb5e2e53ce760d442efb078770cb4d3`.
Tests cover delayed timer start, wall rollback inside/outside the window, slow
retention, an awaited validation crossing the deadline, expiry at the original
120-second total, invalid counters/epochs and overflow, alongside existing
leave/room-replacement/cleanup cases.

The first corrected-source attempt compiled main Kotlin but failed new test
syntax and three trailing-lambda bindings; it executed no new tests. That failed
log and stale original RED XML remain explicitly rejected, not relabeled GREEN.
The first RED launcher also rejected its overly narrow Java exception-type
assumption; distinct readback accepted actual Kotlin/JUnit5 assertion evidence
without replaying the tests. All subsequent source changes require fresh checks.

## Full committed-source execution

Exact local `18c794a76bf1ebc406310f67cbd0e4d3957d9b2c` / tree
`ee0d4098320cd7e56559f8a14e910ea1b0199ce2` passes strict `productionCheck allTests`:
**1,901 Desktop** methods (three existing opt-in LAN skips), **689 Android per
Debug/Release**, 120 GitHub-test and 766 source-derived release controls (one
platform skip), plus Detekt, lint/R8/unsigned AAB, inventory and shell gates.
Independent XML readback matches all 41 affected Desktop methods and applicable
Android methods; no new failures/errors/skips. Source unchanged/owned Gradle stop0.
Full log SHA256 `d1912a3fcedbe7024c69a6c34a6fad841a108e299f0dc8355fd16857239a0197`.
All 362 source-derived ordinary readiness methods pass with three Apple-only Linux
skips. The cached 720.2-second local run is **not** a cold or hosted speed benchmark.

A newly owned detached copy executes two sensitivity controls without changing
the production candidate or tests. Removing elapsed spending causes exactly three
assertion failures (pure budget, actual host, actual peer); removing epoch projection
causes exactly one actual-peer total-recovery assertion failure. Zero errors/skips;
fresh selected XML and mutant hashes are retained, and original copy bytes restored.
The finished disposable checkout is retired; its source/command/XML evidence remains.

Three fresh real pinned-P2pKit Linux host/peer Resume pairs also pass: six natural
zero exits, selected seat, game/version rejection, credential rotation/revocation,
terminal delivery and cleanup. Independent review rehashes 1,146 runtime artifacts,
six helper sources and exact endpoint logs. Receipt SHA256
`4e0a415f25f8af23c4878d1545e7bea182d1e8dcad9477873ad284dea8a042e4`.
These are surviving same-machine JVMs/in-memory stores, **not** short-grace clock
faults, OS sleep/suspension, phones, process death, game reducers or energy evidence.
Whole-JVM samples include startup/JIT and one incomplete peer sample; no exhaustive
mobile resource measurement is claimed.

## Combined current-main alignment

The separate candidate is aligned by normal local merge with main `c81f75b4`;
head `102b38ae` / tree `1c165500` is byte-identical to already tested `f3784375`. Receipts
remain explicitly bound to `f378`; tree identity is not a new-head execution claim.
At `f378`, strict complete local gates pass1,901 Desktop (three existing LAN skips),
689 Android per Debug/Release,120 GitHub-test and768 release controls (one platform
skip), plus Detekt/lint/R8/AAB/shell/inventory. All21affected suites/336Desktop
methods independently match actual XML; source unchanged/owned stop0.
Log SHA256 `4f4b09518aad7d116f005babac4a88b23661822804d706b2454a1a5d73604af3`.
Complete 450 ordinary readiness methods pass with six Apple-only Linux skips.
The 388.2-second local run used680 cached tasks, not a cold/mobile benchmark.

Fresh real LAN execution independently matches 1,146 runtime artifacts / six helper
sources across three Resume pairs, six natural zero exits and unchanged seat,
game/version, rotation/revocation/terminal/cleanup assertions. Receipt SHA256
`13a16a1d25798ee453e3e8735e2207b14ac26a6fc804888522a01260d5965a5f`.
Explicit private preparation-only scheduling adaptation lowers Gradle workers 2 to 1
and adds no-parallel; runtime pair/assertion/deadline/helper source is unchanged.
Do not describe the entire launcher as byte-exact unmodified. Surviving local
JVMs/in-memory stores do not execute short-grace faults, mobile APIs or OS sleep.

## Required integration and remaining limits

Accepted-base alignment and protected integration are complete through
[PR #290](https://github.com/Apdelrahman1911/parlor/pull/290), normal merge
`26eb15b630d9348fa5964b012c27a703e6c4fe06` / tree
`c936c0157f571e949bb3ac3fce03c0a576b3e59b`.
[PR40972918577](https://github.com/Apdelrahman1911/parlor/actions/runs/40972918577)
and the distinct automatic
[actual-main40979550743](https://github.com/Apdelrahman1911/parlor/actions/runs/40979550743)
each independently qualify all eight producers/the original optimized Apple
Release join,23 Swift/716 source-derived KMP Simulator/450 ordinary readiness
methods, zero macOS skips and exact source/artifact/native/output custody.
Actual-main qualification SHA256
`adba10d36861ac678aeb7d722c8455fef6da022c227d21471571731e6e0efe79`.
Earlier local and PR289 receipts remain bound to their original source; no frozen
checkout is rewritten or newer checkout certified by a historical pass.
Physical Android/iOS calls/lock/sleep/Wi-Fi/process death/simultaneous recovery,
energy/thermal and #258 protected-storage acceptance remain unverified. No Store
release, free-runner policy change or additional agents.
