# Foreground cold-peer recovery and explicit Retry — October 28, 2026

## Scope and confirmed defect

Cold process startup currently offers Home Resume but does not automatically
restore a surviving authenticated peer seat. This follow-up belongs to existing
[#252](https://github.com/Apdelrahman1911/parlor/issues/252), alongside the
[selection-bound membership safeguard](2026-10-28-RESUME-SELECTED-MEMBERSHIP.md).
Host process death still ends the room; no host migration is introduced.

Actual Desktop App + the registered Mafia binding also reproduces a separate
bug: after the first synthetic failed Resume, recreating only the Compose root
starts **two** connection openings instead of one. The process retains Failed,
but recomposed peer content calls acquire again without explicit player intent.
Original red log SHA256:
`ed5471735b2f033560d440502c84c345ab7e65a641597b0e417acc45e1454ed9`.
This is a real Desktop UI/ownership behavior, not a reproduced native phone
failure, LAN/authentication bypass, or duplicated gameplay action.

## Implementation

- A Koin process-scoped atomic gate permits one completed foreground startup
  lookup. It is not persisted; root recreation, foreground transitions and later
  manual Retry cannot rearm automatic navigation in the same process.
- Recovery metadata is read only on the active catalog. Inactive/background
  startup performs no protected-store probe; foregrounding starts a fresh lookup.
  Losing focus/navigation cancels that lookup without a polling loop.
- Automatic routing uses the existing registry and the exact opaque selected
  membership. It requires untouched navigation, Idle multiplayer ownership and
  no local-resume/deletion transaction. Fresh state is checked synchronously
  before navigation; no suspension separates claim, check and routing.
- Selecting even the current Games tab, Settings→Games, a new game or a local
  recovery route permanently opts out of delayed startup override. Missing,
  unsupported or failed lookup consumes the attempt; later Home Retry is manual.
- All six peer UIs acquire with `retryFailedOpen=false` on composition and true
  only after explicit Retry. Matching Active/Opening ownership still reattaches;
  failed opening restores its error without another transport call. Existing
  direct intentional acquire callers retain the previous default behavior.

No extra network layer, thread, persistent launch identity, deadline extension,
automatic rejected-action retry, background entitlement, wire/dependency change
or hidden-player-data serialization is added. Authenticated adapter checks and
ownership-safe final Leave remain authoritative.

## Verification and retained failures

Focused Desktop App tests exercise all six actual bindings in EN/AR at320×740dp
and200% text: manual/direct selection, automatic routing, failed-root restoration
and explicit Retry. Five additional actual-App journeys use an explicitly
non-shipping binding to verify background/inactive startup, delayed Settings→Games,
failed/absent lookup followed by a new root, routed exit and explicit game choice.
These synthetic binding journeys are not successful native/LAN authentication.
Seven common tests cover claim concurrency, eligibility and navigation history;
two owner methods protect failed restore, explicit Retry, active reattachment and
wrong-route rejection. Test-owned executors are cancelled/joined/closed.

The initial root complexity finding is fixed by extracting cohesive Compose
helpers, not raising Detekt thresholds. Intermediate compiler/fixture failures
(type inference, named test API parameters, offscreen lookup, legacy-game Retry
copy and an uninjected test dispatcher) remain retained separately; they do not
count as regression proof. The corrected focused55-method Desktop/owner set and
Detekt pass. Six independent restored mutation controls each fail exactly one
executed intended method: background store probing, forgotten process claim,
forgotten navigation decision, removed foreground predicate, replayed failed
opening and omitted App startup wiring. All original source bytes are restored;
all private Gradle stops exit0. Combined sensitivity receipt SHA256:
`b16780d8bb9f1f14871fe917de5019f82b944a016fb0fea696ba27cc17260d39`.
Fresh full committed-source verification remains pending; the final foreground
journey also counts both local snapshot and transport metadata probes.

## Acceptance limits

This is unpublished stacked local work. Accepted-base integration, fresh strict
all-module checks, free hosted architectures and optimized Release/native lanes,
normal protected merge and independently qualified automatic actual-main CI are
required. Do not close#251/#252 on local or simulator-only evidence.

Native protected storage/unlock, real process termination, both physical host
directions, calls/locking/Wi-Fi/suspension, accessibility and mobile CPU/RAM/battery/
thermal matrices are unexecuted. #267's original iOS26.2 input cause and #258's
0PASS/6FAIL strict protection remain separate. No Store release or signing bypass.
