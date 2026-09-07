# Whodunit Discussion Timing

## Leave confirmation policy

**Time on the Leave Confirmation screen does not count toward Discussion.**
This applies to the pass-and-play organizer and the LAN host, in Classic Vote
and Elimination. The existing presentation-owned ticker implements this policy;
it is not a wall-clock deadline. The policy is now explicit and regression-tested.

- Opening confirmation replaces the game surface and cancels its one ticker.
  The canonical controller, current remaining seconds, and local snapshot writer
  stay alive. Game pixels and accessibility semantics are not retained behind
  a transparent modal.
- Stay returns to the same game and starts a fresh one-second tick interval.
  Confirmation time is never subtracted or caught up. Repeated opens and Stay
  must not create concurrent tickers. A partially elapsed tick is not charged.
- Confirmation does not issue a canonical Pause/Resume pair. Stay must not
  release an existing manual, per-timer, or lifecycle-owned pause. A lifecycle
  resume while confirmation is still open must not start a hidden ticker.
- Save failure leaves the local confirmation open and the timer frozen. A
  successful local save preserves the remaining seconds; load uses those
  seconds without accounting for time spent outside the game.
- Confirmed host Leave terminates the room; there is no host migration or saved
  LAN-room resume. A peer never owns the clock. A peer's personal Leave prompt
  does not pause everyone else's game or grant host-only timer authority.

## Verification and compatibility

`WhodunitLocalLeaveTimerTest` exercises the public recovery/game flow, real
reducer, codec, snapshot writer and confirmation. `WhodunitHostLeaveTimerTest`
exercises the real retained host and host route, including the strict start
barrier, lifecycle interruption and confirmed Leave. Both use virtual Compose
time. Host transport is synthetic and pre-admitted, not physical LAN evidence.
Direct ticker/reducer and action-authority tests cover timer guards separately.

There is no game-rule, seed, action, snapshot, or protocol-format migration.
Protocol **4.2 remains exact**. The clock stays in Whodunit, not shared session
or transport infrastructure. Any future retained/background timer must preserve
this exclusion explicitly before replacing the current composition boundary.
