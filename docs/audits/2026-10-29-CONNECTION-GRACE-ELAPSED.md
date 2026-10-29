# Short background grace uses continuous elapsed time

## Confirmed behavior and scope

Related existing [#252](https://github.com/Apdelrahman1911/parlor/issues/252),
not a new duplicate or a physical-device security conclusion. The already accepted
120-second hard-recovery budget uses a continuous clock, but the separate
15-second retained-connection path still relied on wall time and queued timers.

At main `5958f928`, two new tests execute **unchanged actual host/peer room code**
with synthetic P2pKit sessions, independent injected wall/elapsed counters and
pending coroutine timers. Move elapsed time to15seconds while wall time remains
inside the original window: the host still accepts remote game commands and the
peer still attempts retained validation. Both assertions fail (zero errors/skips).
Original RED log SHA256
`a69d73e171490f0fdbfd8c79330f8fdb56012092ce17268219a87e177cf75319`.
These are executed timer/clock fault scenarios, not phones, actual OS sleep or LAN.

## Correction

`ConnectionGraceBudget` is immutable and constant-space: synchronous command
getters, validation and coordinator timers spend the original elapsed budget.
Calendar hints can shorten but never renew grace. Invalid/backward/overflowed
elapsed readings fail closed. Epoch projection saturates on overflow and carries
elapsed time into hard recovery, preventing a delayed short-grace expiry from
renewing the total120second window. Validation is rechecked after its await;
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
120second total, invalid counters/epochs and overflow, alongside existing
leave/room-replacement/cleanup cases.

The first corrected-source attempt compiled main Kotlin but failed new test
syntax and three trailing-lambda bindings; it executed no new tests. That failed
log and stale original RED XML remain explicitly rejected, not relabeled GREEN.
The first RED launcher also rejected its overly narrow Java exception-type
assumption; distinct readback accepted actual Kotlin/JUnit5 assertion evidence
without replaying the tests. All subsequent source changes require fresh checks.

Full committed-source strict compilation/security/Android/Desktop/readiness checks,
actual P2pKit Linux recovery, accepted-base alignment, protected PR integration,
fresh Apple linkage/runtime and distinct automatic actual-main verification remain
before merge. PR#289 runs independently; its frozen checkouts are unchanged.
Physical Android/iOS calls/lock/sleep/Wi-Fi/process death/simultaneous recovery,
energy/thermal and #258 protected-storage acceptance remain unverified. No Store
release, free-runner policy change or additional agents.
