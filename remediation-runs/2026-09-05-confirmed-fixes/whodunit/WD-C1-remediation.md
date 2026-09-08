# WD-C1 — Author remediation review

Author `/root/whodunit_cont`; baseline `main` at
`3625d0663ba6eb51338cbd5f9dc45f859ec18846` (tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`). No commits or branch operations.
This report describes an **uncommitted patch awaiting independent review and
execution**, not a completed test result. Current source identities are in
`source-review.json` beside this file.

## Original evidence and cause

The independently confirmed source proof is preserved in
`audit-runs/2026-09-05-source-audit/candidates/WD-C1-whodunit_cont.md` and
`WD-C1-independent-session_cont.md` in the same directory. The peer command
waiting screen removes `PeerPhaseRouter` from composition. When the matching
host result/snapshot returns it, unconditional entry acknowledgement submits
again although the host has already recorded the seat. The host properly
rejects the no-op; the next entry repeats it. Fast delivery may conceal the
loop by conflating the cover before a Compose frame.

## Bounded change

Only `WhodunitPhaseRouter.kt` production code changes. Its peer readiness
effect now binds session instance, phase, owning seat and assignment
generation, then reads that seat's latest complete authoritative projection.
It sends Intro/Briefing readiness only when absent from the respective host
set and the projection still matches the rendered phase/generation.

Reading `privateStateFor(self).value` instead of a captured Compose snapshot
also covers a host snapshot reaching the mirror before the rendering
collector catches up with command completion. It does not combine public
and private flows. There is no optimistic readiness flag or success cache:
after a definitive rejection, a remount can submit still-missing idempotent
readiness. The existing coordinator still owns pending/outcome recovery.

The opaque command and pause covers remain unchanged. No private screen is
kept mounted beneath a new overlay; no navigation, timer or shared session
refactor is introduced. Concurrent host transitions can still reject a
stale command normally; no claim of a client-side atomic conditional commit
is made. A paused-state one-shot skip was not added: without a new lifecycle
key it could strand readiness if pause/resume is conflated by rendering.

## Regression coverage written

New `game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/ui/flow/PeerReadinessRemountTest.kt`
contains **12 tests, NOT RUN by this agent**. It uses the real composable,
`PartyAwareSession`, `ShadowSessionController`, projection policy and explicit
host-only reducer calls with a validated **synthetic four-seat Classic**
case (not bundled four-seat content).

- Intro and Briefing: hold the cover across frames, install the host ACK,
  remount repeatedly, and require exactly one outbound own-seat action.
- Another seat's readiness cannot suppress the owning seat's ACK; shadow
  canonical/host flows remain null and own-private projection is redacted.
- Previously acknowledged UI recreation and disconnect/pause/resume do not
  send again, for both phases.
- Latest host readiness wins over stale rendering; stale phase/assignment
  effects are skipped, then current rendering may acknowledge normally.
- Replay with an unchanged visible phase and replacement controllers get
  fresh checks; ordinary briefing-card updates do not resend.
- A deliberately rejected/unapplied request does not optimistically mark
  readiness; after its eventual host application no further remount ACK is sent.

The cover and delayed authority schedule are controlled test inputs. These
are not full coordinator, network, rejoin-process, Android/iOS rendering, or
physical P2pKit tests, and do not certify latency or visual frame performance.

## Author second pass and required execution

Reopened original dossiers, conditional parent UI, retained peer wrapper,
shadow controller, host readiness reducer, projection policy, and all newly
written test lines. Reviewed the complete production diff. `git diff --check
-- game-modes/whodunit` exited 0; this is whitespace validation only.

Suggested root-owned focused batch (strict verification retained):

```sh
./gradlew :game-modes:whodunit:desktopTest \
  --tests '*PeerReadinessRemountTest*' \
  --tests '*IntroAndBriefingReadinessTest*' \
  --tests '*WhodunitPeerProjectionBoundaryTest*' \
  --tests '*PartyPlayPassAndPlayParityTest*' \
  --tests '*QueuedActionSafetyTest*' \
  --dependency-verification=strict
```

Run broader Whodunit checks/analysis and combined checks after independent
patch review. Root owns every build, evidence capture, daemon stop and cleanup.
This child created no build outputs or task processes to clean.

## Compatibility and scope

Host-only reducer ownership, transport-attested seat authority, action codec,
protocol exact 4.2, private redaction, deterministic RNG, rules and snapshot
schema are unchanged. Pass-and-play and host router code are unchanged.
Mafia is not imported or modified. Content identity and saves remain byte-
compatible because no resource or schema changed. WD-C4 clock policy is not
part of this patch. Independent validator/result: **pending root assignment**.
