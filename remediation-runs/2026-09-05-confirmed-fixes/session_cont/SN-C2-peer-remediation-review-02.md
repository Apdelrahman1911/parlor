# SN-C2 — same-root peer opening ownership extension

Author: `/root/session_cont`; independent defect adjudicator: `/root`. Root
independently confirmed the peer sibling before authorizing this focused change.
Implementation review and actual execution remain pending. This is the same
SN-C2 lifetime/ownership defect, not a new issue count.

## Scope and root cause

The host correction alone did not cover a peer room created by initial admission
or cold resume. Both methods constructed `PeerP2pRoom` and then suspended for
Ready/CommitAck handoff and lifecycle registration. Their failure finalizers
stopped only the kit. `PeerP2pRoom` owns collectors plus expiry/rejoin jobs in the
process scope; stopping the kit does not complete SharedFlow/StateFlow collection
or detach those jobs. An abandoned owner could start background recovery or later
invalidate a newer same-membership credential. Existing explicit false-return
handoff cleanup closed the room, but the outer finalizer then attempted the
terminal kit stop a second time.

Root's independent source proof covered both outer methods, peer constructor,
collectors, expiry, and close-for-retry path. This author additionally reread the
full admission and resume handshake flows and credential conversion/ownership
contracts before implementing the change.

## Changes

- Retain the constructed peer room in a method-local `openingRoom` until each
  method transfers successful ownership. No room pointer exists before actual
  construction; earlier failures still stop the unowned kit.
- Both finalizers now call one private `closeUnreturnedPeerRoom` helper.
  NonCancellable owner cleanup invokes the existing `abandonFailedResume` /
  `closeForRetry` path, which cancels jobs/collectors, closes the physical session,
  stops the kit once, closes its message channel, and detaches the exact lifecycle
  registration. Existing sanitation of cleanup exceptions is retained.
- Remove the two inline false-return cleanup calls: the shared method finalizer
  is now the sole cleanup owner, avoiding duplicate terminal stop attempts.
- Move initial-join's ownership flag after success diagnostics, so a failure
  before the actual result boundary does not strand an unreturned owner.

This is **not explicit Leave**. It sends no LeaveNotice and retains the committed
credential. It neither changes credential schema/rotation semantics nor uses
membership invalidation to hide the leak. Genuine caller cancellation propagates.
An ordinary failure of the final cleanup acknowledgement remains best-effort,
and a successful returned room remains caller-owned until later close/Leave.

No reducer, game rule, seed, projection, snapshot, protocol4.2 version, admission
validation, seat identity, P2pKit pin, platform lifecycle mapping or navigation
state was changed. Whodunit and Mafia continue to use the same generic transport.

## New production-boundary regression tests

`shared/transport-p2p/src/desktopTest/kotlin/com/parlor/transport/p2p/PeerOpeningCancellationTest.kt`
contains 12 tests using the actual transport, room, codec and credential store:

1. Caller cancellation at AdmissionReady and AdmissionCommitAck.
2. Caller cancellation at ResumeReady and ResumeCommitAck.
3. Ordinary ready failure for admission and cold resume; exactly one kit stop
   attempt and zero incoming/state subscribers.
4. Cancellation during background registration for admission and cold resume,
   including a NonCancellable close barrier and queued foreground event.
5. Ordinary registration failure for both modes.
6. Ordinary cleanup-ACK loss for both modes still returns a live owned room;
   idempotent closeForRetry subsequently releases it without revoking membership.

All failed-opening paths assert no LeaveNotice, retained committed credential,
closed physical session, zero process-scope incoming/state subscribers, and one
terminal kit-stop attempt. Registration tests advance the test scheduler past the
original grace deadline; the cancellation cases commit a newer credential with
the same membership and assert that abandoned expiry cannot delete it. Unexpected
reconnect attempts are counted and blocked using a cancellable synthetic barrier.

The fakes deliberately do not complete their flow collectors on kit.stop, matching
the relevant production lifetime property. No native transport, physical LAN,
signing, device or Store behavior is claimed. The test coroutine scheduler owns
all interleavings; no sleep or network access is added. Credential timestamps are
synthetic relative-to-now bounds, not runtime player data.

## Validation and continuation

- `git diff --check`: exit0.
- New peer tests: **NOT RUN** by this agent; root alone owns the build lane.
- Full new production/test diff self-reviewed; independent remediation review
  remains required. `implementation-source-receipt-02-peer.json` binds exact bytes.
- Original host/lifecycle-coordinator, session ACK and iOS storage edits remain
  unchanged since their previous receipt, except the explicitly described peer
  extension in the shared transport file.
- Recommended focused selector: `:shared:transport-p2p:desktopTest --tests
  '*PeerOpeningCancellationTest*' --tests '*HostOpeningCancellationTest*' --tests
  '*AppLifecycleRoomRegistrationTest*'`, followed by the full transport suite and
  shared session regression suite in the root-owned lane with mandatory cleanup.
- No commits, checkout, user-work modification, issue operations or processes
  were performed. Only one production file and this new test file changed in this
  extension; all other agents' changes remain preserved.
