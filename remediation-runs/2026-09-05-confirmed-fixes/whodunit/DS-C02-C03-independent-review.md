# Independent patch review — DS-C02 and DS-C03

Fix author `/root`; independent patch validator `/root/whodunit_cont`.
Baseline `main` at `3625d0663ba6eb51338cbd5f9dc45f859ec18846`;
review applies to the **uncommitted file hashes** recorded in
`DS-C02-C03-independent-evidence.json`, not to HEAD alone. All seven changed
production/test files in this review match both ends of the executed root
cycle `evidence/design-toast-cover-01`.

**Decision: APPROVE both bounded root-cause corrections at those hashes.**
This is a patch-level source and Desktop runtime conclusion, not approval of
all concurrent changes, physical-device behavior or Store readiness.

## DS-C02 — Toast lifetime identity

Reopened the original candidate and root independent validation, entire
`ParlorToastHost.kt`, both changed/new toast test files, current theme and
actual App/Settings/Whodunit/Mafia notification call paths. These components
remain shipping commonMain, with one remembered root state/host. Notifications
from asynchronous failures can legally arrive between automatic dismissal
and the next composition frame.

### Why the correction works

The former live-queue maximum recycled IDs after an empty queue. The new
private `lastIdentity.updateAndGet` reserves a distinct identity for each
`show` before retryable queue insertion. It does not derive identity from
visible items or reuse a dismissed/evicted lifetime. Its increment lambda is
pure across CAS retries, and the queue CAS uses the already reserved value.
The official **coroutines 1.11.0** source was independently re-fetched and
read at `StateFlow.kt:158–180,191–205,224–237`; exact-version URL/hash/excerpt
are in `DS-C02-independent-cas-research.json`.

`key(state, toast.id)` also separates state owners. A new notification after
the old exit cannot inherit `visible=false` or a completed timer from the old
lifetime. A stale `dismiss(oldId)` cannot remove a newer ID. Queue length4,
localized text, severity and adjacent-text coalescing are preserved.

### Counter-evidence and edge paths

- Concurrent reservations may commit to the queue in a different numeric
  order. Nothing sorts by ID or treats it as message chronology; queue CAS
  insertion determines display order. Uniqueness is what lifecycle needs.
- Coalesced attempts consume IDs but do not change the existing lifetime or
  severity. They cannot revive an invisible notification with the same ID.
- `Long.MAX_VALUE` is explicitly rejected instead of wrapping/colliding;
  exhaustion would require over9e18 calls in one state lifetime. No practical
  reachable new overflow failure is established.
- Old timers are cancelled when their keyed composition leaves; conditional
  dismissal additionally protects against stale completion races. No new
  retained scope, collector, queue, data store or game authority is introduced.

### Executed evidence

Root's two `ParlorToastLifetimeTest` cases passed. Each renders the actual
production host and lets its real expiry call `dismiss`. A bounded synthetic
Unconfined producer only schedules public `show` before the empty frame. Both
distinct and identical replacement text became displayed and then expired
on their own timers. The producer is cancelled in `finally`. These are not
manual-dismiss or private-visibility mocks. Four state tests also passed,
including stale-ID dismissal, coalescing, eviction and bounded queue checks.

State-owner replacement and truly concurrent producer stress tests would be
useful additional coverage; this cycle does not claim them. Their relevant
guards were checked at source level. Android/iOS timing remains unexecuted.

## DS-C03 — Recovery action contrast

Reopened both original dossiers, the entire button/recovery components,
palettes, theme/accent composition, new runtime test and existing focus test.
The affected overlays paint opaque black under an enabled, transparent Leave
action. Previously Light mode supplied ordinary dark `textSecondary`.

`CoverGhost` now uses the existing cover-specific foreground and is selected
only at `ReconnectingOverlay` and `HostDisconnectedOverlay`. Ordinary Ghost
remains unchanged on cream/charcoal surfaces. Disabled CoverGhost uses the
cover tertiary token; loading retains the same readable foreground while
disabling interaction. Enabled pressed tint stays8% and all enum branches
are exhaustive. No inspected code serializes or persists button ordinals.
Click callbacks, roles, labels, touch target, scrolling, safe-area padding,
opaque cover and underlying focus/semantics policy are untouched.

Independent exact sRGB contrast calculation:

- Original Light Ghost on black: **2.7003:1**.
- New cover label on black, both palettes: **10.2499:1**.
- Analytic pressed composition, Amber/Crimson × Light/Dark: **9.3615–9.8808:1**.
- Disabled cover tertiary: **5.4625:1** (inactive controls are not the original
  AA failure; included only to inspect the added branch).

Accent scopes change accent tokens, not cover/text tokens. The separate
ContinueWithout dialog and Whodunit modal Ghost actions are deliberately
unchanged because their buttons sit on filled `surfaceElevated` cards, not
directly on the black cover. Global Ghost recoloring would regress those.

Root's **three runtime contrast tests passed**: each production overlay under
Light and Dark plus ordinary Ghost under both themes. They read the actual
Text layout color and assert display/contrast, rather than merely checking
palette constants. The existing recovery focus test passed: covered controls
remain absent from semantics and Tab reaches Leave, then the underlying
control after recovery. Pressed/disabled math is source-level, not a captured
pixel/gesture or physical accessibility measurement.

## Execution, source binding and cleanup

Read the raw new test XML, focus XML, Gradle log and stop log; independently
parsed all17 suite XML files and their actual testcase nodes: **37 tests,
0 failures, 0 errors, 0 skipped**. Command was root-owned
`:shared:design-system:desktopTest`, JDK21/wrapper, strict dependency
verification, no-daemon/no-parallel/max-workers1. It exited0 at
18:47:38.240657UTC; stop exited0 at18:47:38.551180UTC, with log
`No Gradle daemons are running.`

Receipt records no initial generated outputs, removal of only task outputs
`shared/core/build`, `shared/design-system/build`, `build`, and
`build-logic/convention/build`; no retained/remaining output or worker and no
cleanup error. Cleanup completed18:47:38.670293UTC. Source manifest was stable.
A later root build is now running: its current directories/processes must
not be misattributed to this completed cycle or deleted by this reviewer.

Gradle's warning about future Kotlin2.5 minimum Gradle and the host-incompatible
iOSx64 task are retained warnings, not executed Apple tests or new test
failures. No analysis/lint, physical device, screen-reader, signed release or
Store success is inferred. DS-C01 is expressly outside this review.

This reviewer changed no production/tests, ran no Gradle/native builds, and
created only compact independent evidence. The public research archive was
held in bounded memory and not left on disk. No secrets, private data,
configuration, dependency metadata, cache or concurrent work were deleted.
