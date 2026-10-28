# Post-failure public-input capture — October 28

## Scope

Continues [#267](https://github.com/Apdelrahman1911/parlor/issues/267); keep it open.
This implements a **copy-only capture adapter**, not a production input fix or
completion of the original iOS26.2 investigation. Shipping Swift/Kotlin source,
the original UIKit driver, AGENTS, workflow/dependency/Release/security gates and
player-specific information boundaries are unchanged.

The exact-source planner supplies two additional copied-source transformations:
an app-owned Debug-Simulator-only exporter and the original XCTest driver plus
failed-only request helper. The separately planned PIN01 bridge remains required.
No build, launch, Simulator allocation or source-copy ownership is performed by
the planner. A full manifest-owned application runner is still unfinished.

## Behavior and safeguards

- A fresh opaque request token and exact Debug bundle/Simulator UUID bind the
  observer. It is retained by the copied outer container and removed on disposal.
- Public Darwin notifications carry no payload. The main run loop must run;
  background suspension/process termination cannot be bypassed by this adapter.
- Only the **already-failed** original ten-second public Host/Join transition
  requests export. Gesture, selector, predicate, relaunch and success-path waits
  remain unchanged. A bounded three-second post-failure ACK wait cannot change
  the original result; screenshots and numeric geometry diagnostics remain.
- One request attempt per app process; duplicate or failed exports are not retried.
  No app polling, timer, extra thread, event-path JSON/I/O, touch consumption,
  synthesized gesture, navigation/permission/preference/network operation.
- Numeric bridge output is shape-checked and exclusively exported to an owned
  sandbox directory/file. No raw/private/error/hierarchy payload is retained.
  Ownership UUIDs/process/bundle/times are diagnostic, not player/session identity.
- The host reader rejects foreign owners, links, nonregular/changing/oversized
  files, unknown/private/duplicate fields and existing destinations. It preserves
  exact bytes and hashes; ACK is never a file/source/product/cleanup receipt.
- Empty/overflow/invalid buffers can be retained safely but cannot qualify.
  Earlier Compose rows are valid: exporter installation is not process boot.

## Executed local controls

Ten methods join the existing complete readiness gate. On Linux, eight execute
and two Apple controls explicitly skip. They verify the exact additive plan and
restore **all original driver bytes**, failed-only wiring and unchanged original
tap/deadline; reject stale/ambiguous/extra/repeated source bindings; exercise
foreign/private/duplicate/nonfinite/boolean/oversized receipts and exclusive
retention, symlink/hardlink/FIFO/directory rejection and a real mid-read mutation.

Three separately restored Python source mutations each fail the intended single
executed method: force incomplete buffers to qualify, clobber earlier evidence,
and follow a symlink. The symlink case fails two intact assertions, not two test
methods. Its original evaluator incorrectly required exactly one assertion;
that evaluator failure is retained and the original outcomes independently
inspected without replaying the mutation. A symlink fixture was also separated
from the hardlinked target so the independent no-follow guard is truly sensitive.
The restored baseline passes. This is parser/tool sensitivity, **not native input**.

Mutation receipt SHA256:
`45277d85897028568323a6462dd5124b7f03be7140cb354f5d8b692fcd71201f`; individual logs:
`bc5928f8e11d62d31b89411fda6ffa5cf8d3b7a18caa49ad2091f853bd571113`,
`7af22d74bfb6fce0946bf96c213bbc1583dce4a77af7ae60612cd6e77d5424a5`,
`f1f755f5352ee05ce2a5e46a417be437c414625b696b5c591d9b13e6d283b9ea`.

The complete ordinary readiness gate at the initial source stage passes380
controls with six explicit Linux Apple/platform skips. Its later source edits
require another committed-head check; this result does not pre-certify them.

## Required native and integration verification

The existing free-hosted macOS readiness gate must actually typecheck both the
exporter and exact copied original XCTest source against the Simulator SDK. It
must also execute the real exporter over cross-process Darwin IPC, with a
synthetic numeric snapshot, wrong-owner rejection, duplicate preservation and
observer removal. **Neither has executed on this Linux environment yet.**
Those controls still do not link the real Kotlin Native bridge/application,
execute a post-failure capture inside that application, establish original native
gesture arbitration/timing neutrality, or reproduce the original iOS26.2 cause.
Exact source/copy/driver/product/loaded-image/runtime/event/result/file/cleanup
custody and the real three-arm journey matrix remain required. No failure is
turned green by an ACK, stale receipt, missing buffer or successful later runtime.

Apple's current official Darwin-center/observer/NSData documentation was fetched
October28 and retained with URL/body hashes. It describes system-wide delivery,
main-run-loop dependence, ignored Darwin arguments and the incompatibility of
atomic writes with `withoutOverwriting`; exclusive POSIX writes avoid that mix.

Physical six-game host/client interruption/touch/VoiceOver and power/thermal
matrices, strict protection (#258:0PASS/6FAIL), Store ownership/signing/external
approval and overall production acceptance remain open. No paid runner or Store
release is introduced.
