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
iOS uses the exposed `clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW)` binding.
Apple implements it with `mach_continuous_time()`, including sleep, available
from iOS 10 (target is 16). Integer conversion requires no per-read C allocation.
Its documented error sentinel fails closed, including the first budget reading.
Desktop uses process-relative `System.nanoTime()`.

Both rooms retain their one existing expiry coroutine. All six callers stop
foreground/reconnect work when the budget is spent. Cleanup stays in its sibling
job: a session collector must not join its own parent during Leave. No new thread,
polling, wake lock, background entitlement, protocol, dependency or retry deadline.

Official references, observed October 29, 2026:
- [Android SystemClock](https://developer.android.com/reference/android/os/SystemClock)
- [Apple clock API and sleep contract](https://github.com/apple-oss-distributions/Libc/blob/71bbe350ab79eef58113991d817ccc6165061a64/gen/clock_gettime.3)
- [Apple continuous-clock implementation](https://github.com/apple-oss-distributions/Libc/blob/71bbe350ab79eef58113991d817ccc6165061a64/gen/clock_gettime.c)
  (document and observed Kotlin platform-binding hashes retained locally).

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

The first complete check failed two iOS library compilation
tasks because the original direct Mach imports are not exported by Kotlin/Native.
That failure remains retained. The exposed POSIX correction compiles all three
iOS Kotlin libraries on Linux and independently passes the same 19 Desktop
methods. This does **not** verify Apple linkage/runtime or actual device sleep.

At corrected committed source `0a8a04b25d1032aaaed0e51f5dc6f9d08f519d6f`, full
strict `productionCheck allTests` passes **1,888 Desktop cases** (three existing
opt-in LAN skips), **681 Android per Debug/Release**, **120 GitHub-test** and
**766 release controls** (one platform skip), plus Detekt, lint, R8, unsigned AAB,
inventory and shell gates. All 294 annotated methods in 14 changed classes match
actual Desktop and applicable Android XML. Source unchanged; owned Gradle stop 0.
Log SHA256 `76e36798ba67bc39fb404610b7bc8ef1f2d85cfd87b9776d0841150e23e4afaf`.
Elapsed 509.9 seconds, 84 executed/1,584 up-to-date tasks: legitimate reuse,
**not a cold-build or mobile-performance benchmark**.

The same source additionally passes three actual P2pKit Linux host/peer Resume
pairs; all six processes exit naturally with code 0 and teardown markers.
Independent review checks 1,133 runtime hashes, six helper hashes, exact assertions
and owned cleanup. Receipt SHA256
`fa14440f7ab44cf9fe24eec5f4fd60df424b71bd5f1e260085a810d6c2b71aeb`.
These surviving same-machine JVMs use in-memory credentials; this is **not** OS
sleep, persistent-storage/process-death, gameplay or phone performance evidence.

Final documentation-source rechecks, fresh free hosted Android/Apple compilation,
iOS linkage/runtime tests, normal protected merge and automatic actual-main
verification remain required.
A subsequent Desktop/iOS regression directly reads the real platform elapsed clock
32 times, requiring a readable, nondecreasing counter on every executable target.
This makes fresh hosted iOS tests exercise the exposed POSIX function itself,
not just injected budget values; it still does not simulate device sleep.
Its initial common-test placement failed both Android JVM variants because the
SDK stub throws `elapsedRealtime not mocked`. Those failures are retained, not
reclassified as a shipping clock defect. The runtime probe belongs in Desktop
and iOS runtime source sets; common injected budget tests remain on all targets.
No mocked Android return values, skips or new dependencies are introduced.
Actual Android elapsed-clock/sleep execution still needs instrumented devices.
Physical Android/iOS locks/calls/suspension/Wi-Fi/process death, simultaneous
recovery, battery/thermal behavior and strict protected storage remain open.
Host process death ends the room; no indefinite iOS hosting or migration claim.
