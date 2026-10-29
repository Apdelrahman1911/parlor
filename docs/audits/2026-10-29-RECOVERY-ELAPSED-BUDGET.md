# Recovery expiry uses continuous elapsed time

## Confirmed behavior and impact

Tracked under existing [#252](https://github.com/Apdelrahman1911/parlor/issues/252),
not a duplicate native-input or host-migration issue. Actual production rooms
execute with synthetic P2pKit sessions and a deterministic coroutine scheduler.
No phone, LAN, OS sleep or signing result is inferred.

1. Background a peer with a 120-second recovery budget. After 60 seconds,
   roll the wall clock backward 60 seconds and foreground it. At the original
   elapsed deadline it remains `Resuming`, rather than `Expired`. The original
   unchanged-shipping-source RED has one assertion failure, no errors/skips.
2. Advance the injected continuous counter to the deadline while coroutine
   timers remain pending. Foregrounding an empty host or a peer still calls
   native foreground networking before expiry. Both new methods fail with
   expected zero foreground notifications, actual one. This reproduces the
   proposed first correction's ordering gap, not executed device suspension.

The first cause is cancelling the original expiry timer and rebuilding its
remaining delay from a wall-clock timestamp. The second is starting native work
before a zero-delay sibling timer can run. These are P2 recovery-bound and
unexpected-operation defects; no unauthorized join or secret disclosure is proven.

## Implementation

`RecoveryWindowBudget` is constant-space, room-mutex-owned and injectable.
Repeated schedules take the smaller of the original remaining elapsed budget
and the bounded epoch hint. Successful restoration starts a distinct later
window; terminal/Leave ownership and stale-deadline guards remain unchanged.
Impossible or overflowing elapsed readings fail closed.

Android uses `SystemClock.elapsedRealtime()`, which includes deep sleep.
iOS uses `mach_continuous_time()` with one cached timebase conversion; its
platform contract includes sleep and availability from iOS 10 (target is 16).
Desktop uses process-relative `System.nanoTime()`.

Both rooms retain their one existing expiry coroutine. All six callers stop
foreground/reconnect work when the budget is spent. Cleanup stays in its sibling
job: a session collector must not join its own parent during Leave. No new thread,
polling, wake lock, background entitlement, protocol, dependency or retry deadline.

Official references, observed October 29, 2026:
- [Android SystemClock](https://developer.android.com/reference/android/os/SystemClock)
- [Apple XNU mach time contract](https://github.com/apple-oss-distributions/xnu/blob/main/osfmk/mach/mach_time.h)
  (exact observed upstream revision and document hashes retained in local evidence).

## Executed verification and limits

Final focused strict Desktop run: **19 PASS, 0 FAIL, 0 ERROR, 0 SKIP**:
four common budget methods, thirteen peer recovery methods and two actual host
methods. Independent XML inspection and original RED artifacts are retained.
Disabling elapsed-budget spending temporarily causes exactly three expected
assertion failures (budget, actual peer, actual host); original bytes are restored.
The delayed-timer pair separately rejects the first correction before the final
six-caller guard. No test or timeout was weakened.

The first host observer incorrectly checked teardown before existing Bonjour
goodbye flush; its failure remains rejected. The corrected test still asserts
expiry at the original deadline and advances only that existing cleanup duration.

Full exact-committed-source strict gates, free hosted Android/Apple compilation,
iOS linkage/runtime tests, normal protected merge and fresh automatic main are
still required. iOS API binding and real sleep behavior are **unverified locally**.
Physical Android/iOS locks/calls/suspension/Wi-Fi/process death, simultaneous
recovery, battery/thermal behavior and strict protected storage remain open.
Host process death ends the room; no indefinite iOS hosting or migration claim.
